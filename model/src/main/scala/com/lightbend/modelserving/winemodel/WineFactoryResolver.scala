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

package com.lightbend.modelserving.winemodel

import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.{ModelFactory, ModelFactoryResolver}
import com.lightbend.modelserving.winemodel.pmml.WinePMMLModel
import com.lightbend.modelserving.winemodel.tensorflow.{WineTensorFlowBundledModel, WineTensorFlowModel}

/**
  * Model factory resolver - requires specific factories
  */
object WineFactoryResolver extends ModelFactoryResolver[WineRecord, Double]{

  private val factories = Map(ModelDescriptor.ModelType.PMML -> WinePMMLModel,
                              ModelDescriptor.ModelType.TENSORFLOW -> WineTensorFlowModel,
                              ModelDescriptor.ModelType.TENSORFLOWSAVED -> WineTensorFlowBundledModel)

  override def getFactory(whichFactory: Int): Option[ModelFactory[WineRecord, Double]] =
    factories.keySet.find(_.value == whichFactory) match {
      case None => println(s"WARNING: Could not a model factory for integer value $whichFactory"); None
      case Some(key) => factories.get(key)
    }

  override def getFactory(whichFactory: ModelDescriptor.ModelType): Option[ModelFactory[WineRecord, Double]] = factories.get(whichFactory)
}
