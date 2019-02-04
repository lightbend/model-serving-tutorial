/*
 * Copyright (C) 2019  Lightbend
 *
 * This file is part of ModelServing-tutorial
 *
 * ModelServing-tutorial is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License Version 2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.lightbend.modelserving.spark.server

import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.configuration.ModelServingConfiguration
import com.lightbend.modelserving.model.{ModelToServe, ServingResult}
import com.lightbend.modelserving.spark.{DataWithModel, KafkaSupport, ModelState}
import com.lightbend.modelserving.winemodel.WineFactoryResolver
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, StreamingContext}

import scala.collection._

/**
  * Implementation of Model serving using Spark Structured Streaming server with near-real time support,
  * using Spark's "continuous processing" engine.
  */

object SparkStructuredStateModelServer {

  import ModelServingConfiguration._

  def main(args: Array[String]): Unit = {

    println(s"Running Spark Model Server. Kafka: $KAFKA_BROKER ")

    // Create context
    val sparkSession = SparkSession.builder
      .appName("SparkModelServer")
      .master("local[3]")  // TODO: In production code, don't hard code a value here
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.kryo.registrator", "com.lightbend.modelserving.spark.ModelStateRegistrator")
      .config("spark.sql.streaming.checkpointLocation", CHECKPOINT_DIR)
      .getOrCreate()

    sparkSession.sparkContext.setLogLevel("ERROR")
    import sparkSession.implicits._

    // Set modelToServe
    ModelToServe.setResolver(WineFactoryResolver)

    val ssc = new StreamingContext(sparkSession.sparkContext, Seconds(1))
    ssc.checkpoint("./cpt")

    // Message parsing
    sparkSession.udf.register("deserializeData", (data: Array[Byte]) => DataWithModel.dataWineFromByteArrayStructured(data))

    // Current state of data models
    val currentModels = mutable.Map[String, ModelState]()

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
      .select("data.fixedAcidity", "data.volatileAcidity", "data.citricAcid", "data.residualSugar",
        "data.chlorides", "data.freeSulfurDioxide", "data.totalSulfurDioxide", "data.density", "data.pH",
        "data.sulphates", "data.alcohol", "data.dataType", "data.ts"
      )
      .as[WineRecord]
      .map(data => {
        data.dataType match {
          case dtype if(dtype != "") =>
            currentModels.get (data.dataType) match {
              case Some (state) =>
                val result = state.model.score (data)
                ServingResult[Double] (state.name, data.dataType, System.currentTimeMillis () - data.ts, result)
              case _ => ServingResult[Double] ("No model available","",0,.0)
            }
          case _ => ServingResult[Double] ("Bad input record", "",0,.0)
        }
      }).as[ServingResult[Double]]

    var dataQuery = datastream
      .writeStream.outputMode("update")
      .format("console").option("truncate", false).option("numRows", 10) // 10 is the default
      .trigger(Trigger.Continuous("5 second"))
      .start

    // Create models kafka stream
    val kafkaParams = KafkaSupport.getKafkaConsumerConfig(KAFKA_BROKER)
    val modelsStream = KafkaUtils.createDirectStream[Array[Byte], Array[Byte]](ssc,PreferConsistent,
      Subscribe[Array[Byte], Array[Byte]](Set(MODELS_TOPIC),kafkaParams))

    // Exercise:
    // We use the older DStream API next, for it's flexibility. An alternative is
    // to use the Structured Streaming idiom `streamingDataset.writeStream.foreach {...}`.
    // Try rewriting the following logic using that approach, as described here:
    // https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html#using-foreach-and-foreachbatch

    modelsStream.foreachRDD( rdd =>
      if (!rdd.isEmpty()) {
        val models = rdd.map(_.value).collect
          .map(ModelToServe.fromByteArray(_)).filter(_.isSuccess).map(_.get)

        // Stop the currently running data stream
        println("Stopping data query")
        dataQuery.stop

        val newModels = models.map(modelToServe => {
          println (s"New model ${modelToServe}")
          // Update state with the new model
          val model = WineFactoryResolver.getFactory(modelToServe.modelType) match {
            case Some (factory) => factory.create(modelToServe)
            case _ => None
          }
          model match {
            case Some (m) => (modelToServe.dataType, ModelState (modelToServe.name, m) )
            case _ => (null, null)
          }
        }).toMap

        // Merge maps
        newModels.foreach{ case (name, value) => {
          if(currentModels.contains(name))
            currentModels(name).model.cleanup()
          currentModels(name) = value
        }}

        // restart the data stream
        println("Starting data query")
        dataQuery = datastream
          .writeStream.outputMode("update")
          .format("console").option("truncate", false).option("numRows", 10) // 10 is the default
          .trigger(Trigger.Continuous("5 second"))
          .start
      }
    )

    // Execute
    ssc.start()
    ssc.awaitTermination()
  }
}
