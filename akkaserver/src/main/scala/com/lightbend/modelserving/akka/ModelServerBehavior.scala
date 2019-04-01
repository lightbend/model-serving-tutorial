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
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext}
import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.{Model, ModelToServe, ModelToServeStats, ServingResult}

/** Akka Typed Actor for handling a single model, including updates to it and scoring with it. */
class ModelServerBehavior(context: ActorContext[ModelServerActor], dataType : String) extends AbstractBehavior[ModelServerActor] {

  println(s"Creating a new Model Server for data type $dataType")

  private var currentModel: Option[Model[WineRecord, Double]] = None
  var currentState: Option[ModelToServeStats] = None

  override def onMessage(msg: ModelServerActor): Behavior[ModelServerActor] = {
    msg match {
      case model : UpdateModel => // Update Model
        // Update model
        println(s"Updated model: ${model.model}")
        ModelToServe.toModel[WineRecord, Double](model.model) match {
          case Some(m) => // Successfully got a new model
            // close current model first
            currentModel.foreach(_.cleanup())
            // Update model and state
            currentModel = Some(m)
            currentState = Some(ModelToServeStats(model.model))
          case _ =>   // Failed converting
            println(s"Failed to convert model: ${model.model}")
        }
        model.reply ! Done
      case record : ScoreData => // Serve data
        // Actually process data
        val result = currentModel match {
          case Some(model) => {
            val start = System.currentTimeMillis()
            // Actually serve
            val result = model.score(record.record.getRecord)
            val duration = System.currentTimeMillis() - start
            // Update state
            currentState = Some(currentState.get.incrementUsage(duration))
            // result
            Some(ServingResult(currentState.get.name, record.record.getType, record.record.getRecord.asInstanceOf[WineRecord].ts, result.asInstanceOf[Double]))
          }
          case _ => None
        }
        record.reply ! result
      case getState : GetState => // State query
        getState.reply ! currentState.getOrElse(ModelToServeStats())
    }
    this
  }
}
