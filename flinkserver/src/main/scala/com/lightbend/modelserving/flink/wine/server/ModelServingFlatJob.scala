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

package com.lightbend.modelserving.flink.wine.server

import java.util.Properties

import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.DataToServe
import com.lightbend.modelserving.flink.wine.BadDataHandler
import com.lightbend.modelserving.configuration.ModelServingConfiguration
import com.lightbend.modelserving.flink.partitioned.DataProcessorMap
import com.lightbend.modelserving.flink.typeschema.ByteArraySchema
import com.lightbend.modelserving.model.ModelToServe
import com.lightbend.modelserving.winemodel.{DataRecord, WineFactoryResolver}
import org.apache.flink.configuration.{Configuration, JobManagerOptions, TaskManagerOptions}
import org.apache.flink.runtime.concurrent.Executors
import org.apache.flink.runtime.highavailability.HighAvailabilityServicesUtils
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.streaming.api.scala._

/**
  * loosely based on http://dataartisans.github.io/flink-training/exercises/eventTimeJoin.html approach
  * for queriable state
  *   https://github.com/dataArtisans/flink-queryable_state_demo/blob/master/README.md
  * Using Flink min server to enable Queryable data access
  *   see https://github.com/dataArtisans/flink-queryable_state_demo/blob/master/src/main/java/com/dataartisans/queryablestatedemo/EventCountJob.java
  *
  * This little application is based on a RichCoFlatMapFunction which works on a non keyed streams. It is
  * applicable when a single applications serves a single model(model set) for a single data type.
  * Scaling of the application is based on the parallelism of input stream and RichCoFlatMapFunction.
  * The model is broadcasted to all RichCoFlatMapFunction instances. The messages are processed by different
  * instances of RichCoFlatMapFunction in a round-robin fashion.
  */

object ModelServingFlatJob {

  import ModelServingConfiguration._

  def main(args: Array[String]): Unit = {
    executeServer()
  }

  // Execute on the local Flink server - to test queariable state
  def executeServer() : Unit = {

    // We use a mini cluster here for sake of simplicity, because I don't want
    // to require a Flink installation to run this demo. Everything should be
    // contained in this JAR.

    val port = 6124
    val parallelism = 2

    val config = new Configuration()
    config.setInteger(JobManagerOptions.PORT, port)
    config.setString(JobManagerOptions.ADDRESS, "localhost")
    config.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, parallelism)

    // Create a local Flink server
    val flinkCluster = new LocalFlinkMiniCluster(
      config,
      HighAvailabilityServicesUtils.createHighAvailabilityServices(
        config,
        Executors.directExecutor(),
        HighAvailabilityServicesUtils.AddressResolution.TRY_ADDRESS_RESOLUTION),
      false)
    try {
      // Start server and create environment
      flinkCluster.start(true)
      val env = StreamExecutionEnvironment.createRemoteEnvironment("localhost", port, parallelism)
      // Build Graph
      buildGraph(env)
      val jobGraph = env.getStreamGraph.getJobGraph()
      // Submit to the server and wait for completion
      flinkCluster.submitJobAndWait(jobGraph, false)
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  // Build execution Graph
  def buildGraph(env : StreamExecutionEnvironment) : Unit = {
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.enableCheckpointing(5000)
    // configure Kafka consumer
    // Data
    val dataKafkaProps = new Properties
    dataKafkaProps.setProperty("bootstrap.servers", KAFKA_BROKER)
    dataKafkaProps.setProperty("group.id", DATA_GROUP)
    dataKafkaProps.setProperty("auto.offset.reset", "earliest")

    // Model
    val modelKafkaProps = new Properties
    modelKafkaProps.setProperty("bootstrap.servers", KAFKA_BROKER)
    modelKafkaProps.setProperty("group.id", MODELS_GROUP)
    // always read the Kafka topic from the current location
    modelKafkaProps.setProperty("auto.offset.reset", "earliest")

    // create a Kafka consumers
    // Data
    val dataConsumer = new FlinkKafkaConsumer[Array[Byte]](
      DATA_TOPIC,
      new ByteArraySchema,
      dataKafkaProps
    )

    // Model
    val modelConsumer = new FlinkKafkaConsumer[Array[Byte]](
      MODELS_TOPIC,
      new ByteArraySchema,
      modelKafkaProps
    )

    // Create input data streams
    val modelsStream = env.addSource(modelConsumer)
    val dataStream = env.addSource(dataConsumer)

    // Set modelToServe
    ModelToServe.setResolver(WineFactoryResolver)

    // Read models from streams
    val models = modelsStream.map(ModelToServe.fromByteArray(_))
      .flatMap(BadDataHandler[ModelToServe])
      .broadcast
    // Read data from streams
    val data = dataStream.map(DataRecord.fromByteArray(_))
      .flatMap(BadDataHandler[DataToServe[WineRecord]])

    // Merge streams
    data
      .connect(models)
      .flatMap(DataProcessorMap[WineRecord, Double]())
      .map(result => println(s"Model serving in ${System.currentTimeMillis() - result.duration} ms, with result ${result.result} (model ${result.name}, data type ${result.dataType})"))
  }
}
