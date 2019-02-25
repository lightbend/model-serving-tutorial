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

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.lightbend.modelserving.model.ModelToServeStats

/**
  * Akka Typed actor that handles model updates, scoring records with the current model, retrieving the current
  * models, and retrieving the current state to support external state queries.
  * @param context
  */
class ModelServerManagerBehavior(context: ActorContext[ModelServerManagerActor]) extends AbstractBehavior[ModelServerManagerActor] {

  println("Creating Model Serving Manager")

  private def getModelServer(dataType: String): ActorRef[ModelServerActor] = {

    context.child(dataType) match {
      case Some(actorRef) => actorRef.asInstanceOf[ActorRef[ModelServerActor]]
      case _ => context.spawn(Behaviors.setup[ModelServerActor](
        context => new ModelServerBehavior(context, dataType)), dataType)
    }
  }

  private def getInstances : GetModelsResult = GetModelsResult(context.children.map(_.path.name).toSeq)

  override def onMessage(msg: ModelServerManagerActor): Behavior[ModelServerManagerActor] = {
    msg match {
      case updateModel : UpdateModel =>
        getModelServer(updateModel.model.dataType) tell updateModel
      case scoreData : ScoreData =>
        getModelServer(scoreData.record.getType) tell scoreData
      case getState : GetState => // Used for state queries
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
