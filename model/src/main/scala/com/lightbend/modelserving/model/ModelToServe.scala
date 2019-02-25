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

package com.lightbend.modelserving.model

import java.io.DataOutputStream

import scala.util.Try
import com.lightbend.model.modeldescriptor.ModelDescriptor

/**
  * Various data transformation methods.
  */
object ModelToServe {

  private var resolver : ModelFactoryResolver[_, _] = _

  /** This method has to be invoked before execution starts */
  def setResolver[RECORD, RESULT](res : ModelFactoryResolver[RECORD, RESULT]) : Unit = resolver = res

  override def toString: String = super.toString

  /** Get the model from byte array */
  def fromByteArray(message: Array[Byte]): Try[ModelToServe] = Try{
    val m = ModelDescriptor.parseFrom(message)
    m.messageContent.isData match {
      case true => new ModelToServe(m.name, m.description, m.modeltype.value, m.getData.toByteArray, null, m.dataType)
      case _ => new ModelToServe(m.name, m.description, m.modeltype.value, Array[Byte](), m.getLocation, m.dataType)
    }
  }

  /** Write the model to data stream */
  def writeModel[RECORD,RESULT](model: Model[RECORD,RESULT], output: DataOutputStream): Unit = {
    try {
      if (model == null) {
        output.writeLong(0)
        return
      }
      val bytes = model.toBytes()
      output.writeLong(bytes.length)
      output.writeLong(model.getType.value)
      output.write(bytes)
    } catch {
      case t: Throwable =>
        System.out.println("Error Serializing model")
        t.printStackTrace()
    }
  }

  /** Deep copy the model */
  def copy[RECORD,RESULT](from: Option[Model[RECORD,RESULT]]): Option[Model[RECORD,RESULT]] = {
    validateResolver()
    from match {
      case Some(model) =>
        validateResolver()
        Some(resolver.getFactory(model.getType.value).get.restore(model.toBytes()).asInstanceOf[Model[RECORD,RESULT]])
      case _ => None
    }
  }

  /** Restore model of the specified ModelType from a byte array */
  def restore[RECORD,RESULT](t : ModelDescriptor.ModelType, content : Array[Byte]): Option[Model[RECORD,RESULT]] = {
    validateResolver()
    Some(resolver.getFactory(t.value).get.restore(content).asInstanceOf[Model[RECORD,RESULT]])
  }

  /** Restore model of the specified ModelType value from a byte array */
  def restore[RECORD,RESULT](tValue : Int, content : Array[Byte]): Option[Model[RECORD,RESULT]] = {
    validateResolver()
    Some(resolver.getFactory(tValue).get.restore(content).asInstanceOf[Model[RECORD,RESULT]])
  }

  /** Get the model from ModelToServe */
  def toModel[RECORD,RESULT](model: ModelToServe): Option[Model[RECORD,RESULT]] = {
    validateResolver()
    resolver.getFactory(model.modelType) match {
      case Some(factory) => factory.create(model) match {
        case Some(model) => Some(model.asInstanceOf[Model[RECORD,RESULT]])
        case _ => None
      }
      case _ => None
    }
  }

  /** Ensure that the resolver is set */
  private def validateResolver() : Unit = if(resolver == null) throw new Exception("Model factory resolver is not set")
}

/**
  * Encapsulates a model to serve along with some metadata about it.
  * Using an Int for the modelType, instead of a ModelDescriptor.ModelType, which is what it represents, is
  * unfortunately necessary because otherwise you can't use these objects in Spark UDFs; you get a Scala Reflection
  * exception at runtime. Hence, the integration values for modelType should match the known integer values in the
  * ModelType objects. See also protobufs/src/main/protobuf/modeldescriptor.proto
  */
case class ModelToServe(
  name: String,
  description: String,
  modelType: Int,
  model : Array[Byte],
  location : String,
  dataType : String)


/**
  * Model serving statistics definition
  */
case class ModelToServeStats(
  name: String = "",
  description: String = "",
  modelType: Int = ModelDescriptor.ModelType.PMML.value,
  since: Long = 0,
  var usage: Long = 0,
  var duration: Double = .0,
  var min: Long = Long.MaxValue,
  var max: Long = Long.MinValue) {

  /**
    * Increment model serving statistics; invoked after scoring every record.
    * @arg execution Long value for the milliseconds it took to score the record.
    */
  def incrementUsage(execution : Long) : ModelToServeStats = {
    usage = usage + 1
    duration = duration + execution
    if(execution < min) min = execution
    if(execution > max) max = execution
    this
  }
}

object ModelToServeStats {
  def apply(m : ModelToServe): ModelToServeStats = ModelToServeStats(m.name, m.description, m.modelType, System.currentTimeMillis())
}
