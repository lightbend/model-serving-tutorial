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

package com.lightbend.modelserving.akka

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.typed.scaladsl.{ActorFlow, ActorMaterializer}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.lightbend.modelserving.configuration.ModelServingConfiguration
import com.lightbend.modelserving.model.{ModelToServe, ServingResult}
import com.lightbend.modelserving.winemodel.{DataRecord, WineFactoryResolver}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.duration._
import scala.util.Success

/** Entry point for the Akka Server example. */
object AkkaModelServer {

  import ModelServingConfiguration._

  // Initialization

  implicit val modelServerManager = ActorSystem(
    Behaviors.setup[ModelServerManagerActor](
      context => new ModelServerManagerBehavior(context)), "ModelServing")

  implicit val materializer = ActorMaterializer()
  implicit val executionContext = modelServerManager.executionContext
  implicit val askTimeout = Timeout(30.seconds)

  /** Kafka topic configuration for the data records source. */
  val dataSettings = ConsumerSettings(modelServerManager.toUntyped, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(KAFKA_BROKER)
    .withGroupId(DATA_GROUP)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  /** Kafka topic configuration for the model parameters source. */
  val modelSettings = ConsumerSettings(modelServerManager.toUntyped, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(KAFKA_BROKER)
    .withGroupId(MODELS_GROUP)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  def main(args: Array[String]): Unit = {

    println(s"Akka model server, brokers $KAFKA_BROKER")

    // Set modelToServe
    ModelToServe.setResolver(WineFactoryResolver)

    // Model stream processing
    Consumer.atMostOnceSource(modelSettings, Subscriptions.topics(MODELS_TOPIC))
      .map(record => ModelToServe.fromByteArray(record.value)).collect { case Success(a) => a }
      .via(ActorFlow.ask(1)(modelServerManager)((elem, replyTo : ActorRef[Done]) => new UpdateModel(replyTo, elem)))
      .runWith(Sink.ignore) // run the stream, we do not read the results directly

    // Data stream processing
    Consumer.atMostOnceSource(dataSettings, Subscriptions.topics(DATA_TOPIC))
      .map(record => DataRecord.fromByteArray(record.value)).collect { case Success(a) => a }
      .via(ActorFlow.ask(1)(modelServerManager)((elem, replyTo : ActorRef[Option[ServingResult[Double]]]) => new ScoreData(replyTo, elem)))
      .collect{ case (Some(result)) => result}
      .runWith(Sink.foreach(result =>
        println(s"Model serving in ${System.currentTimeMillis() - result.duration} ms, with result ${result.result} " +
          s"(model ${result.name}, data type ${result.dataType})")))
    // Rest Server
    startRest(modelServerManager)
  }

  def startRest(modelServerManager: ActorSystem[ModelServerManagerActor]): Unit = {

    implicit val timeout = Timeout(10.seconds)
    implicit val system = modelServerManager.toUntyped

    val host = "0.0.0.0"
    val port = MODELSERVING_PORT
    val routes = QueriesAkkaHttpResource.storeRoutes(modelServerManager)(modelServerManager.scheduler)

    val _ = Http().bindAndHandle(routes, host, port) map
      { binding =>
        println(s"Starting models observer on port ${binding.localAddress}") } recover {
      case ex =>
        println(s"Models observer could not bind to $host:$port - ${ex.getMessage}")
    }
  }
}
