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

package com.lightbend.modelserving.model.PMML

import java.io._
import java.util

import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.modelserving.model.{Model, ModelFactory}
import org.dmg.pmml.{FieldName, PMML}
import org.jpmml.evaluator.visitors._
import org.jpmml.evaluator._
import org.jpmml.model.PMMLUtil

import scala.collection._

// Abstract class for any PMML model processing. It has to be extended by the user
// implement score method, based on his own model. Serializability here is required for Spark
abstract class PMMLModel(inputStream: Array[Byte]) extends Model with Serializable {

  var arguments : mutable.Map[FieldName, FieldValue] = _
  var pmml : PMML = _
  var evaluator : ModelEvaluator[_ <: org.dmg.pmml.Model] = _
  var inputFields : util.List[InputField] = _
  var target: TargetField = _
  var tname : FieldName = _

  var bytes = inputStream
  setup()

  private def setup() : Unit = {
    arguments = mutable.Map[FieldName, FieldValue]()
    // Marshall PMML
    pmml = PMMLUtil.unmarshal(new ByteArrayInputStream(bytes))
    // Optimize model// Optimize model
    PMMLModelBase.optimize(pmml)
    // Create and verify evaluator
    evaluator = ModelEvaluatorFactory.newInstance.newModelEvaluator(pmml)
    evaluator.verify()
    // Get input/target fields
    inputFields = evaluator.getInputFields
    target = evaluator.getTargetFields.get(0)
    tname = target.getName
  }

  override def cleanup(): Unit = {}

  override def toBytes : Array[Byte] = bytes

  override def getType: Long = ModelDescriptor.ModelType.PMML.value

  override def equals(obj: Any): Boolean = {
    obj match {
      case pmmlModel: PMMLModel =>
        pmmlModel.toBytes.toList == inputStream.toList
      case _ => false
    }
  }

  private def writeObject(output: ObjectOutputStream): Unit = {
    val start = System.currentTimeMillis()
    output.writeObject(bytes)
    println(s"PMML java serialization in ${System.currentTimeMillis() - start} ms")
  }

  private def readObject(input: ObjectInputStream): Unit = {
    val start = System.currentTimeMillis()
    bytes = input.readObject().asInstanceOf[Array[Byte]]
    // Marshall PMML
    try {
      setup()
      println(s"PMML java deserialization in ${System.currentTimeMillis() - start} ms")
    }
    catch {
      case t: Throwable =>
        println(s"PMML java deserialization failed in ${System.currentTimeMillis() - start} ms")
        println(s"Exception $t")
        println(s"Restored PMML ${new String(bytes)}")
    }
  }
}

object PMMLModelBase{

  // List of PMML optimizers (https://groups.google.com/forum/#!topic/jpmml/rUpv8hOuS3A)
  private val optimizers = Array(new ExpressionOptimizer, new FieldOptimizer, new PredicateOptimizer, new GeneralRegressionModelOptimizer, new NaiveBayesModelOptimizer, new RegressionModelOptimizer)

  // OPtimize PMML model
  def optimize(pmml : PMML) = this.synchronized {
    optimizers.foreach(opt =>
      try {
        opt.applyTo(pmml)
      } catch {
        case t: Throwable => {
          println(s"Error optimizing model for optimizer $opt")
          t.printStackTrace()
        }
      }
    )
  }
}