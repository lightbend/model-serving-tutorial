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

package com.lightbend.modelserving.flink.keyed

import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.{DataToServe, Model}
import com.lightbend.modelserving.flink.typeschema.ModelTypeSerializer
import com.lightbend.modelserving.model.{ModelToServe, ModelToServeStats, ServingResult}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.scala.createTypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector

/**
  * Main class processing data using models (keyed)
  *
  * see http://dataartisans.github.io/flink-training/exercises/eventTimeJoin.html for details
  */

object DataProcessorKeyed {
  def apply() = new DataProcessorKeyed
}

class DataProcessorKeyed extends CoProcessFunction[DataToServe, ModelToServe, ServingResult]{

  // In Flink class instance is created not for key, but rater key groups
  // https://ci.apache.org/projects/flink/flink-docs-release-1.6/dev/stream/state/state.html#keyed-state-and-operator-state
  // As a result, any key specific sate data has to be in the key specific state

  // current model state
  var modelState: ValueState[ModelToServeStats] = _
  // New model state
  var newModelState: ValueState[ModelToServeStats] = _

  // Current model
  var currentModel : ValueState[Option[Model]] = _
  // New model
  var newModel : ValueState[Option[Model]] = _

  // Called when an instance is created
  override def open(parameters: Configuration): Unit = {

    // Model state descriptor
    val modelStateDesc = new ValueStateDescriptor[ModelToServeStats](
      "currentModelState",                        // state name
      createTypeInformation[ModelToServeStats])           // type information
    modelStateDesc.setQueryable("currentModelState")      // Expose it for queryable state
    // Create Model state
    modelState = getRuntimeContext.getState(modelStateDesc)
    // New Model state descriptor
    val newModelStateDesc = new ValueStateDescriptor[ModelToServeStats](
      "newModelState",                             // state name
      createTypeInformation[ModelToServeStats])            // type information
    // Create new model state
    newModelState = getRuntimeContext.getState(newModelStateDesc)
    // Model descriptor
    val modelDesc = new ValueStateDescriptor[Option[Model]](
      "currentModel",                               // state name
      new ModelTypeSerializer)                              // type information
    // Create current model state
    currentModel = getRuntimeContext.getState(modelDesc)
    // New model state descriptor
    val newModelDesc = new ValueStateDescriptor[Option[Model]](
      "newModel",                                    // state name
      new ModelTypeSerializer)                               // type information
    // Create new model state
    newModel = getRuntimeContext.getState(newModelDesc)
  }

  // Process new model
  override def processElement2(model: ModelToServe, ctx: CoProcessFunction[DataToServe, ModelToServe, ServingResult]#Context, out: Collector[ServingResult]): Unit = {

    // Ensure that the state is initialized
    if(newModel.value == null) newModel.update(None)
    if(currentModel.value == null) currentModel.update(None)

    println(s"New model - $model")
    // Create a model
    ModelToServe.toModel(model) match {
      case Some(md) =>
        newModel.update (Some(md))                            // Create a new model
        newModelState.update (new ModelToServeStats (model))  // Create a new model state
      case _ =>   // Model creation failed, continue
    }
  }

  // Serve data
  override def processElement1(record: DataToServe, ctx: CoProcessFunction[DataToServe, ModelToServe, ServingResult]#Context, out: Collector[ServingResult]): Unit = {

    // Ensure that the state is initialized
    if(newModel.value == null) newModel.update(None)
    if(currentModel.value == null) currentModel.update(None)
    // See if we have update for the model
    newModel.value.foreach { model =>
      // close current model first
      currentModel.value.foreach(_.cleanup())
      // Update model
      currentModel.update(newModel.value)
      modelState.update(newModelState.value())
      newModel.update(None)
    }

    // Actually process data
    currentModel.value match {
      case Some(model) => {
        val start = System.currentTimeMillis()
        // Actually serve
        val result = model.score(record.getRecord)
        val duration = System.currentTimeMillis() - start
        // Update state
        modelState.update(modelState.value().incrementUsage(duration))
        // Write result out
        out.collect(ServingResult(modelState.value().name, record.getType, record.getRecord.asInstanceOf[WineRecord].ts, result.asInstanceOf[Double]))
      }
      case _ =>
    }
  }
}
