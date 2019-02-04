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

package com.lightbend.modelserving.akka

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.lightbend.modelserving.model.ModelToServeStats

class ModelServerManagerBehavior(context: ActorContext[ModelServerManagerActor]) extends AbstractBehavior[ModelServerManagerActor] {

  println("Creating Model Serving Manager")

  private def getModelServer(dataType: String): ActorRef[ModelServerActor] = {

    context.child(dataType) match {
      case Some(actorRef) => actorRef.asInstanceOf[ActorRef[ModelServerActor]]
      case _ => context.spawn(Behaviors.setup[ModelServerActor](
        context => new ModelServerBehaviour(context, dataType)), dataType)
    }
  }

  private def getInstances : GetModelsResult = GetModelsResult(context.children.map(_.path.name).toSeq)

  override def onMessage(msg: ModelServerManagerActor): Behavior[ModelServerManagerActor] = {
    msg match {
      case model : ModelUpdate => // Update Model
        getModelServer(model.model.dataType) tell model
      case record : ServeData => // Serve datat
        getModelServer(record.record.getType) tell record
      case getState : GetState => // State query
        context.child(getState.dataType) match{
        case Some(server) => server.asInstanceOf[ActorRef[ModelServerActor]] tell getState
        case _ => getState.reply ! ModelToServeStats()
      }
      case getModels : GetModels => // Get list of models
        getModels.reply ! getInstances
    }
    this
  }
}
