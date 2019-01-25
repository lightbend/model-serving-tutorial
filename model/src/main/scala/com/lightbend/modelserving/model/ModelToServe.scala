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

package com.lightbend.modelserving.model

import java.io.DataOutputStream

import scala.util.Try
import com.lightbend.model.modeldescriptor.ModelDescriptor

/**
  * Various data transformation methods.
  */
object ModelToServe {

  // Model Factory resolver
  private var resolver : ModelFactoryResolver[_, _] = _

  // This method has to be invoked before execution starts
  def setResolver[RECORD, RESULT](res : ModelFactoryResolver[RECORD, RESULT]) : Unit = resolver = res

  // Convert to String
  override def toString: String = super.toString

  // Get the model from byte array
  def fromByteArray(message: Array[Byte]): Try[ModelToServe] = Try{
    val m = ModelDescriptor.parseFrom(message)
    m.messageContent.isData match {
      case true => new ModelToServe(m.name, m.description, m.modeltype.value, m.getData.toByteArray, null, m.dataType)
      case _ => new ModelToServe(m.name, m.description, m.modeltype.value, Array[Byte](), m.getLocation, m.dataType)
    }
  }

  // Write model to data stream
  def writeModel[RECORD,RESULT](model: Model[RECORD,RESULT], output: DataOutputStream): Unit = {
    try {
      if (model == null) {
        output.writeLong(0)
        return
      }
      val bytes = model.toBytes()
      output.writeLong(bytes.length)
      output.writeLong(model.getType)
      output.write(bytes)
    } catch {
      case t: Throwable =>
        System.out.println("Error Serializing model")
        t.printStackTrace()
    }
  }

  // Deep copy the model
  def copy[RECORD,RESULT](from: Option[Model[RECORD,RESULT]]): Option[Model[RECORD,RESULT]] = {
    validateResolver()
    from match {
      case Some(model) =>
        validateResolver()
        Some(resolver.getFactory(model.getType.asInstanceOf[Int]).get.restore(model.toBytes()).asInstanceOf[Model[RECORD,RESULT]])
      case _ => None
    }
  }

  // Restore model from byte array
  def restore[RECORD,RESULT](t : Int, content : Array[Byte]): Option[Model[RECORD,RESULT]] = {
    validateResolver()
    Some(resolver.getFactory(t).get.restore(content).asInstanceOf[Model[RECORD,RESULT]])
  }

  // Get the model from ModelToServe
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

  // Ensure that resolver is set
  private def validateResolver() : Unit = if(resolver == null) throw new Exception("Model factory resolver is not set")
}

// Model to serve definition
case class ModelToServe(name: String, description: String, modelType: Int, model : Array[Byte], location : String, dataType : String)


// Model serving statistics definition
case class ModelToServeStats(name: String = "", description: String = "",
                             modelType: Int = ModelDescriptor.ModelType.PMML.value,
                             since : Long = 0, var usage : Long = 0, var duration : Double = .0,
                             var min : Long = Long.MaxValue, var max : Long = Long.MinValue){
  def this(m : ModelToServe) = this(m.name, m.description, m.modelType, System.currentTimeMillis())

  // Increment model serving statistics invoked for every processing
  def incrementUsage(execution : Long) : ModelToServeStats = {
    usage = usage + 1
    duration = duration + execution
    if(execution < min) min = execution
    if(execution > max) max = execution
    this
  }
}

// Model serving result definition
// Ideally result should be AnyVal, but due to the fact that Spark structured streaming does not support it, we are using Double here
case class ServingResult[RESULT](name : String, dataType : String = "", duration : Long = 0, result: RESULT = null.asInstanceOf[RESULT])