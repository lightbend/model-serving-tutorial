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

package com.lightbend.modelserving.flink.partitioned

/**
  * Main class processing data using models (partitioned)
  *
  */
import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.{DataToServe, Model}
import com.lightbend.modelserving.flink.ModelWithType
import com.lightbend.modelserving.flink.typeschema.ModelWithTypeSerializer
import com.lightbend.modelserving.model.{ModelToServe, ServingResult}
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction
import org.apache.flink.util.Collector

import scala.collection.JavaConverters._
import scala.collection.mutable.{ListBuffer, Map}

object DataProcessorMap{
  def apply[RECORD, RESULT]() = new DataProcessorMap[RECORD, RESULT]
}

class DataProcessorMap[RECORD, RESULT] extends RichCoFlatMapFunction[DataToServe[RECORD], ModelToServe, ServingResult[RESULT]] with CheckpointedFunction {

  // Current models
  private var currentModels = Map[String, (String,Model[RECORD, RESULT])]()
  // New models
  private var newModels = Map[String, (String,Model[RECORD, RESULT])]()

  // Checkpointing state
  @transient private var checkpointedState: ListState[ModelWithType[RECORD, RESULT]] = _

  // Create a snapshot
  override def snapshotState(context: FunctionSnapshotContext): Unit = {
    // Clear checkpointing state
    checkpointedState.clear()
    // Populate checkpointing state
    currentModels.foreach(entry => checkpointedState.add(new ModelWithType[RECORD, RESULT](true, entry._1, entry._2)))
    newModels.foreach(entry => checkpointedState.add(new ModelWithType[RECORD, RESULT](false, entry._1, entry._2)))
  }

  // Restore checkpoint
  override def initializeState(context: FunctionInitializationContext): Unit = {
    // Checkpointing descriptor
    val checkPointDescriptor = new ListStateDescriptor[ModelWithType[RECORD, RESULT]] (
        "modelState",
        new ModelWithTypeSerializer[RECORD, RESULT])
    // Get checkpointing data
    checkpointedState = context.getOperatorStateStore.getListState (checkPointDescriptor)

    // If restored
    if (context.isRestored) {
      // Create state
      val nm = new ListBuffer[(String, (String, Model[RECORD, RESULT]))]()
      val cm = new ListBuffer[(String, (String, Model[RECORD, RESULT]))]()
      checkpointedState.get().iterator().asScala.foreach(modelWithType => {
        // For each model in the checkpointed state
        modelWithType.isCurrent match {
              case true => cm += (modelWithType.dataType -> modelWithType.modelWithName)  // Its a current model
              case _ => nm += (modelWithType.dataType -> modelWithType.modelWithName)     // Its a new model
            }
       })
      // Convert lists into maps
      currentModels = Map(cm: _*)
      newModels = Map(nm: _*)
    }
  }

  // Process new model
  override def flatMap2(model: ModelToServe, out: Collector[ServingResult[RESULT]]): Unit = {

    println(s"New model - $model")
    ModelToServe.toModel[RECORD, RESULT](model) match {                     // Inflate model
      case Some(md) => newModels += (model.dataType -> (model.name, md))  // Save a new model
      case _ =>
    }
  }

  // Serve data
  override def flatMap1(record: DataToServe[RECORD], out: Collector[ServingResult[RESULT]]): Unit = {
    // See if we need to update
    newModels.contains(record.getType) match {    // There is a new model for this type
      case true =>
        currentModels.contains(record.getType) match {  // There is currently a model for this type
          case true => currentModels(record.getType)._2.cleanup()  // Cleanup
          case _ =>
        }
        // Update current models and remove a model from new models
        currentModels += (record.getType -> newModels(record.getType))
        newModels -= record.getType
      case _ =>
    }
    // actually process
    currentModels.get(record.getType) match {
      case Some(model) =>
        val start = System.currentTimeMillis()
        // Actual serving
        val result = model._2.score(record.getRecord)
        val duration = System.currentTimeMillis() - start
        // write result out
        out.collect(ServingResult[RESULT](model._1, record.getType, record.getRecord.asInstanceOf[WineRecord].ts, result))
      case _ =>
    }
  }
}
