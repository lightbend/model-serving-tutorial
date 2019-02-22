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

import akka.actor.Scheduler
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.lightbend.modelserving.model.ModelToServeStats
import de.heikoseeberger.akkahttpjackson.JacksonSupport

import scala.concurrent.duration._

object QueriesAkkaHttpResource extends JacksonSupport {

  implicit val askTimeout = Timeout(30.seconds)

  def storeRoutes(modelserver: ActorRef[ModelServerManagerActor])(implicit scheduler: Scheduler) : Route =
    get {
      // Get list of models
      path("processors") {
        onSuccess(modelserver ? ((replyTo: ActorRef[GetModelsResult]) => GetModels(replyTo))) {
          case models: GetModelsResult =>
            complete(models)
        }
      } ~
      // Get statistics for a given data type
      path("state"/Segment) { dataType =>
        onSuccess(modelserver ? ((replyTo: ActorRef[ModelToServeStats]) => GetState(replyTo, dataType))) {
          case stats : ModelToServeStats =>
            complete(stats)
        }
      }
    }
}
