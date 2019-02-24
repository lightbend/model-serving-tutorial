/*
 * Copyright (C) 2017-2019  Lightbend
 *
 * This file is part of the Lightbend model-serving-tutorial (https://github.com/lightbend/model-serving-tutorial)
 *
 * The model-serving-tutorial is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License Version 2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.modelserving.spark.server

import com.lightbend.modelserving.configuration.ModelServingConfiguration
import com.lightbend.modelserving.model.{ModelToServe, ServingResult}
import com.lightbend.modelserving.spark.{DataWithModel, ModelState, ModelStateSerializerKryo}
import com.lightbend.modelserving.winemodel.WineFactoryResolver
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{GroupState, GroupStateTimeout, StreamingQueryListener, Trigger}
import org.apache.spark.sql.{Encoder, Encoders, SparkSession}

import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

/**
  * Application that performs model serving using Spark Structured Streaming.
  */
object SparkStructuredModelServer {

  implicit val modelStateEncoder: Encoder[ModelState] = Encoders.kryo[ModelState]

  import ModelServingConfiguration._

  def main(args: Array[String]): Unit = {

    println(s"Running Spark Model Server. Kafka: $KAFKA_BROKER")

    // Create context
    val sparkSession = SparkSession.builder
      .appName("SparkModelServer")
      .master("local")  // TODO: In production code, don't hard code a value here
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.kryo.registrator", "com.lightbend.modelserving.spark.ModelStateRegistrator")
      .config("spark.sql.streaming.checkpointLocation", CHECKPOINT_DIR)
      .getOrCreate()

    sparkSession.sparkContext.setLogLevel("ERROR")
    import sparkSession.implicits._

    // Set the model to serve
    ModelToServe.setResolver(WineFactoryResolver)
    ModelStateSerializerKryo.setResolver(WineFactoryResolver)

    // Message parsing:
    // In order to be able to union both streams we are using a combined format
    sparkSession.udf.register[DataWithModel, Array[Byte]]("deserializeData",  (data: Array[Byte]) => DataWithModel.dataFromByteArrayStructured(data))
    sparkSession.udf.register[DataWithModel, Array[Byte]]("deserializeModel", (data: Array[Byte]) => DataWithModel.modelFromByteArrayStructured(data))

    // Create query listener
    val queryListener = new StreamingQueryListener {
      import org.apache.spark.sql.streaming.StreamingQueryListener._
      def onQueryTerminated(event: QueryTerminatedEvent): Unit = {}
      def onQueryStarted(event: QueryStartedEvent): Unit = {}
      def onQueryProgress(event: QueryProgressEvent): Unit = {
        println(s"Query progress  batch ${event.progress.batchId} at ${event.progress.timestamp}")
        event.progress.durationMs.asScala.toList.foreach(duration => println(s"${duration._1} - ${duration._2}ms"))
        event.progress.sources.foreach(source =>
          println(s"Source ${source.description}, start offset ${source.startOffset}, end offset ${source.endOffset}, " +
            s"input rows ${source.numInputRows}, rows per second ${source.processedRowsPerSecond}")
        )
      }
    }

    // Attach query listener
    sparkSession.streams.addListener(queryListener)

    // Create the stream for the data (records)
    val datastream = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", KAFKA_BROKER)
      .option("subscribe", DATA_TOPIC)
      .option(ConsumerConfig.GROUP_ID_CONFIG, DATA_GROUP)
      .option(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      .load()
      .selectExpr("""deserializeData(value) AS data""")
      .select("data.dataType", "data.data", "data.model")
      .as[DataWithModel]


    // Create the stream for the model updates
    val modelstream = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", KAFKA_BROKER)
      .option("subscribe", MODELS_TOPIC)
      .option(ConsumerConfig.GROUP_ID_CONFIG, MODELS_GROUP)
      .option(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      .load().selectExpr("""deserializeModel(value) AS data""")
      .select("data.dataType", "data.data", "data.model")
      .as[DataWithModel]

    // Exercise:
    // We union the records into one stream next, then we'll have the same processing logic handle records of both types.
    // As described below, this means that we can't use continuous processing, because it doesn't work with unions.
    // We have to use mini-batches instead.
    // What if you redesign the code to keep the streams separate, but apply the same `modelServing` transformation to
    // both in a `mapGroupsWithState` call, as below, or you could try splitting `modelServing` into the separates part
    // for scoring and updating models. Does it still work correctly, especially in a distributed setting
    // (vs. Spark local mode)? Not using unions would allow us to use continuous processing, which can't be used with unions.

    // Order matters here - the data stream is appended to the end so that all the model records will
    // be processed first and data records after them.
    val datamodelstream = modelstream.union(datastream)

    // Actual model serving (i.e., scoring records)
    val servingresultsstream = datamodelstream
      .filter(_.dataType.length > 0)
      .groupByKey(_.dataType)
      .mapGroupsWithState(GroupStateTimeout.NoTimeout())(modelServing).as[Seq[ServingResult[Double]]]
      .withColumn("value", explode($"value"))
      .select("value.name", "value.dataType", "value.duration", "value.result")


    // Ideally, we would use continuous processing here, but it does not work due to the error
    //   Exception in thread "main" org.apache.spark.sql.AnalysisException: Continuous processing does not support Union operations:
    //      .trigger(Trigger.Continuous("1 second"))
    // Instead, we use a processingTime trigger with one-second micro-batch intervals
    servingresultsstream.writeStream
      .outputMode("update")
      .format("console").option("truncate", false).option("numRows", 10) // 10 is the default
      .trigger(Trigger.ProcessingTime("1 second"))
      .start

    //Wait for all streams to finish
    sparkSession.streams.awaitAnyTermination()
  }

  /**
    * A mapping function that implements actual model serving. It handles model updates as well as records that need scoring.
    * For a description of how it works see:
    *   http://www.waitingforcode.com/apache-spark-structured-streaming/stateful-transformations-mapgroupswithstate/read
    * and
    *   https://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.sql.streaming.GroupState
    */
  def modelServing(key: String, values: Iterator[DataWithModel], state: GroupState[ModelState]) : Seq[ServingResult[Double]] = {
    var results = new ListBuffer[ServingResult[Double]]()
    values.foreach(value => {
      value.data match {
        case null =>  // The current value actually holds a model
          println(s"New model ${value.model}")
          if (state.exists){  // updating existing model
            state.get.model.cleanup()
            state.remove()
          }

          // Update state with the new model
          val model = WineFactoryResolver.getFactory(value.model.modelType) match {
            case Some(factory) => factory.create(value.model) // could return Some(model) or None!
            case _ => None
          }
          model match {
            case Some(m) => state.update(ModelState(value.model.name, m))
            case _ =>
          }
        case _ => // The current value holds a record
          if (state.exists) {
            val result = state.get.model.score(value.data)
            results += ServingResult(state.get.name, value.dataType, System.currentTimeMillis() - value.data.ts, result)
          }
          else
            results += ServingResult("No model available")
      }
    })
    results.toList
  }
}
