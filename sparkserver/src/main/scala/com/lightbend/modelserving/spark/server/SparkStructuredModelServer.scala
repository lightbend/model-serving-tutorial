/*
 * Copyright (C) 2019  Lightbend
 *
 * This file is part of ModelServing-tutorial
 *
 * ModelServing-tutorial is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.lightbend.modelserving.spark.server

/**
  * Implementation of Model serving using Spark Structured Streaming server.
  */

import com.lightbend.modelserving.configuration.ModelServingConfiguration
import com.lightbend.modelserving.model.{ModelToServe, ServingResult}
import com.lightbend.modelserving.spark.{DataWithModel, ModelState, ModelStateSerializerKryo}
import com.lightbend.modelserving.winemodel.WineFactoryResolver
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{GroupState, GroupStateTimeout, StreamingQueryListener, Trigger}
import org.apache.spark.sql.{Encoders, SparkSession}

import scala.collection.mutable.ListBuffer

import scala.collection.JavaConverters._

object SparkStructuredModelServer {

  implicit val modelStateEncoder  = Encoders.kryo[ModelState]

  import ModelServingConfiguration._

  def main(args: Array[String]): Unit = {

    println(s"Running Spark Model Server. Kafka: $KAFKA_BROKER")

    // Create context
    val sparkSession = SparkSession.builder
      .appName("SparkModelServer")
      .master("local")  // TODO: don't hard code here
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
    // In order to be able to uninon both streams we are using a combined format
    sparkSession.udf.register("deserializeData",  (data: Array[Byte]) => DataWithModel.dataFromByteArrayStructured(data))
    sparkSession.udf.register("deserializeModel", (data: Array[Byte]) => DataWithModel.modelFromByteArrayStructured(data))

    // Create query listener
    val queryListener = new StreamingQueryListener {
      import org.apache.spark.sql.streaming.StreamingQueryListener._
      def onQueryTerminated(event: QueryTerminatedEvent): Unit = {}
      def onQueryStarted(event: QueryStartedEvent): Unit = {}
      def onQueryProgress(event: QueryProgressEvent): Unit = {
        println(s"Query progress  batch ${event.progress.batchId} at ${event.progress.timestamp}")
        event.progress.durationMs.asScala.toList.foreach(duration => println(s"${duration._1} - ${duration._2}"))
        event.progress.sources.foreach(source =>
          println(s"Source ${source.description}, start offset ${source.startOffset}, end offset ${source.endOffset}, " +
            s"input rows ${source.numInputRows}, rows per second ${source.processedRowsPerSecond}")
        )
      }
    }

    // Attach query listener
    sparkSession.streams.addListener(queryListener)

    // Create data stream
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


    // Create model stream
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

    // Order matters here - the data stream is appended to the end so that all the model records will
    // be processed first and data records after them.
    val datamodelstream = modelstream.union(datastream)

    // Actual model serving
    val servingresultsstream = datamodelstream
      .filter(_.dataType.length > 0)
      .groupByKey(_.dataType)
      .mapGroupsWithState(GroupStateTimeout.NoTimeout())(modelServing).as[Seq[ServingResult[Double]]]
      .withColumn("value", explode($"value"))
      .select("value.name", "value.dataType", "value.duration", "value.result")


    servingresultsstream.writeStream
      .outputMode("update")
      .format("console").option("truncate", false).option("numRows", 10) // 10 is the default
      // Ideally, we would use continuous processing here, but it does not work due to the error
      // Exception in thread "main" org.apache.spark.sql.AnalysisException: Continuous processing does not support Union operations.;;
      //      .trigger(Trigger.Continuous("1 second"))
      // Instead, we use processingTime trigger with one-second micro-batch interval
      .trigger(Trigger.ProcessingTime("1 second"))
      .start

    //Wait for all streams to finish
    sparkSession.streams.awaitAnyTermination()
  }

  // A mapping function that implements actual model serving
  // For some descriptions on documentation and how it works see:
  // http://www.waitingforcode.com/apache-spark-structured-streaming/stateful-transformations-mapgroupswithstate/read
  // and https://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.sql.streaming.GroupState
  def modelServing(key: String, values: Iterator[DataWithModel], state: GroupState[ModelState]) : Seq[ServingResult[Double]] = {
    var results = new ListBuffer[ServingResult[Double]]()
    values.foreach(value => {
      value.data match {
        case null =>  // This model
          println(s"New model ${value.model}")
          if (state.exists){  // updating existing model
            state.get.model.cleanup()
            state.remove()
          }

          // Update state with the new model
          val model = WineFactoryResolver.getFactory(value.model.modelType) match {
            case Some(factory) => factory.create(value.model)
            case _ => None
          }
          model match {
            case Some(m) => state.update(ModelState(value.model.name, m))
            case _ =>
          }
        case _ => // This is data
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
