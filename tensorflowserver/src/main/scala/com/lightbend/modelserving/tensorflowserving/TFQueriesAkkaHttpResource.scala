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

package com.lightbend.modelserving.tensorflowserving

import akka.actor.Scheduler
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.lightbend.modelserving.model.ModelToServeStats
import de.heikoseeberger.akkahttpjackson.JacksonSupport

import scala.concurrent.duration._

object TFQueriesAkkaHttpResource extends JacksonSupport {

  implicit val askTimeout = Timeout(30.seconds)

  def storeRoutes(modelserver: ActorRef[TFModelServerActor])(implicit scheduler: Scheduler) : Route =
    get {
      // Get statistics
      path("state") {
        onSuccess(modelserver ? ((replyTo: ActorRef[ModelToServeStats]) => GetState(replyTo))) {
          case stats : ModelToServeStats =>
            complete(stats)
        }
      }
    }
}