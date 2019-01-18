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

package com.lightbend.modelserving.winemodel.tensorflow

import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.tensorflow.TensorFlowBundleModel
import com.lightbend.modelserving.model.{Model, ModelFactory}
import com.lightbend.modelserving.model.ModelToServe

/**
  * Implementation of TensorFlow bundled model for Wine.
  */
class WineTensorFlowBundledModel(inputStream: Array[Byte]) extends TensorFlowBundleModel(inputStream) {

  /**
    * Score data.
    *
    * @param input object to score.
    * @return scoring result
    */
  override def score(input: AnyVal): AnyVal = {
    // Create input tensor
    val modelInput = WineTensorFlowModel.toTensor(input.asInstanceOf[WineRecord])
    // Serve model using tensorflow APIs
    val signature = signatures.head._2
    val tinput = signature.inputs.head._2
    val toutput= signature.outputs.head._2
    val result = session.runner.feed(tinput.name, modelInput).fetch(toutput.name).run().get(0)
    // process result
    val rshape = result.shape
    var rMatrix = Array.ofDim[Float](rshape(0).asInstanceOf[Int], rshape(1).asInstanceOf[Int])
    result.copyTo(rMatrix)
    rMatrix(0).indices.maxBy(rMatrix(0)).asInstanceOf[Double]
  }
}

/**
  * Implementation of TensorFlow bundled model factory.
  */
object WineTensorFlowBundledModel extends ModelFactory {

  /**
    * Creates a new TensorFlow bundled model.
    *
    * @param descriptor model to serve representation of PMML model.
    * @return model
    */
  override def create(input: ModelToServe): Option[Model] =
    try
      Some(new WineTensorFlowBundledModel(input.location.getBytes))
    catch {
      case t: Throwable => None
    }

  /**
    * Restore PMML model from binary.
    *
    * @param bytes binary representation of PMML model.
    * @return model
    */
  override def restore(bytes: Array[Byte]) = new WineTensorFlowBundledModel(bytes)
}

