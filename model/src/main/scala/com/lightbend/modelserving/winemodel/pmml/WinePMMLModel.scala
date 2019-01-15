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

package com.lightbend.modelserving.winemodel.pmml


import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.PMML.PMMLModel
import com.lightbend.modelserving.model.{Model, ModelFactory}
import com.lightbend.modelserving.model.ModelToServe
import org.jpmml.evaluator.Computable

import scala.collection.JavaConversions._
import scala.collection._

// PMML model implementation for wine data
class WinePMMLModel(inputStream: Array[Byte]) extends PMMLModel(inputStream) {

  // Scoring (using PMML evaluator)
  override def score(input: AnyVal): AnyVal = {
    // Convert input
    val inputs = input.asInstanceOf[WineRecord]
    // Clear arguments (from previous run)
    arguments.clear()
    // Populate input based on record
    inputFields.foreach(field => {
      arguments.put(field.getName, field.prepare(getValueByName(inputs, field.getName.getValue)))
    })

    // Calculate Output// Calculate Output
    val result = evaluator.evaluate(arguments)

    // Prepare output
    result.get(tname) match {
      case c : Computable => c.getResult.toString.toDouble
      case v : Any => v.asInstanceOf[Double]
    }
  }

  // Support function to get values
  private def getValueByName(inputs : WineRecord, name: String) : Double =
    WinePMMLModel.names.get(name) match {
    case Some(index) => {
     val v = inputs.getFieldByNumber(index + 1)
      v.asInstanceOf[Double]
    }
    case _ => .0
  }
}

// Factory for wine data PMML model
object WinePMMLModel extends ModelFactory{
  private val names = Map("fixed acidity" -> 0,
    "volatile acidity" -> 1,"citric acid" ->2,"residual sugar" -> 3,
    "chlorides" -> 4,"free sulfur dioxide" -> 5,"total sulfur dioxide" -> 6,
    "density" -> 7,"pH" -> 8,"sulphates" ->9,"alcohol" -> 10)

  override def create(input: ModelToServe): Option[Model] = {
    try {
      Some(new WinePMMLModel(input.model))
    }catch{
      case t: Throwable => None
    }
  }

  override def restore(bytes: Array[Byte]): Model = new WinePMMLModel(bytes)
}