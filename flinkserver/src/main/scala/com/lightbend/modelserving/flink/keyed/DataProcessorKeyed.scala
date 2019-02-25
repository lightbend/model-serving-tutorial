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

package com.lightbend.modelserving.flink.keyed

import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model._
import com.lightbend.modelserving.flink.typeschema.ModelTypeSerializer
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.scala.createTypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector

/**
  * Class for processing data using models with state managed by key, rather than partitioned.
  *
  * see http://dataartisans.github.io/flink-training/exercises/eventTimeJoin.html for details
  * In Flink, a class instance is created not for each key, but rather for each key group,
  * https://ci.apache.org/projects/flink/flink-docs-release-1.6/dev/stream/state/state.html#keyed-state-and-operator-state.
  * As a result, any state data has to be in the key specific state.
  */
class DataProcessorKeyed[RECORD, RESULT]() extends CoProcessFunction[DataToServe[RECORD], ModelToServe, ServingResult[RESULT]]{

  /** The current model state */
  var modelState: ValueState[ModelToServeStats] = _
  /** The new model state */
  var newModelState: ValueState[ModelToServeStats] = _

  var currentModel : ValueState[Option[Model[RECORD, RESULT]]] = _
  var newModel : ValueState[Option[Model[RECORD, RESULT]]] = _

  /** Called when an instance is created */
  override def open(parameters: Configuration): Unit = {

    // Model state descriptor
    val modelStateDesc = new ValueStateDescriptor[ModelToServeStats](
      "currentModelState",                         // state name
      createTypeInformation[ModelToServeStats])           // type information

    modelStateDesc.setQueryable("currentModelState")      // Expose it for queryable state

    // Create Model state
    modelState = getRuntimeContext.getState(modelStateDesc)

    // New Model state descriptor
    val newModelStateDesc = new ValueStateDescriptor[ModelToServeStats](
      "newModelState",                             // state name
      createTypeInformation[ModelToServeStats])           // type information

    // Create new model state
    newModelState = getRuntimeContext.getState(newModelStateDesc)

    // Model descriptor
    val modelDesc = new ValueStateDescriptor[Option[Model[RECORD, RESULT]]](
      "currentModel",                               // state name
      new ModelTypeSerializer[RECORD, RESULT])                              // type information

    // Create current model state
    currentModel = getRuntimeContext.getState(modelDesc)

    // Create the new model state descriptor
    val newModelDesc = new ValueStateDescriptor[Option[Model[RECORD, RESULT]]](
      "newModel",                                    // state name
      new ModelTypeSerializer[RECORD, RESULT])                               // type information

    // Create the new model state
    newModel = getRuntimeContext.getState(newModelDesc)
  }


  /**
    * Process a new model. We store it in the `newModel`, then `processElement1` will detect it and switch out the old
    * model.
    */
  override def processElement2(model: ModelToServe, ctx: CoProcessFunction[DataToServe[RECORD], ModelToServe, ServingResult[RESULT]]#Context, out: Collector[ServingResult[RESULT]]): Unit = {

    // Ensure that the state is initialized
    if(newModel.value == null) newModel.update(None)
    if(currentModel.value == null) currentModel.update(None)

    println(s"New model - $model")
    // Create a model
    ModelToServe.toModel[RECORD, RESULT](model) match {
      case Some(md) =>
        newModel.update (Some(md))                            // Create a new model
        newModelState.update (ModelToServeStats(model))  // Create a new model state
      case _ =>   // Model creation failed, continue
    }
  }

  /** Serve data, i.e., score with the current model */
  override def processElement1(record: DataToServe[RECORD], ctx: CoProcessFunction[DataToServe[RECORD], ModelToServe, ServingResult[RESULT]]#Context, out: Collector[ServingResult[RESULT]]): Unit = {

    // Exercise:
    // Instead of tossing the old model, create a stack of models. Add the ability to pop the current model and recover
    // the previous one(s).
    // Then decide how to bound the number of stack elements by some N, but this suggests you might want to store them
    // in a bounded-size cache, so you can toss the oldest ones.

    // Ensure that the state is initialized
    if(newModel.value == null) newModel.update(None)
    if(currentModel.value == null) currentModel.update(None)

    // See if we have update for the model
    newModel.value.foreach { model =>
      // Close current model first
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
        val score = model.score(record.getRecord)
        val duration = System.currentTimeMillis() - start

        modelState.update(modelState.value().incrementUsage(duration))
        val result = ServingResult[RESULT](modelState.value().name, record.getType, record.getRecord.asInstanceOf[WineRecord].ts, score)
//        println(result)
        out.collect(result)
      }
      case _ => // Exercise: print/log when a matching model wasn't found. Does the output make sense?
    }
  }
}

object DataProcessorKeyed {
  def apply[RECORD, RESULT]() = new DataProcessorKeyed[RECORD, RESULT]
}