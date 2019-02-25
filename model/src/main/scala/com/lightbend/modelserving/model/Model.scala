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

import com.lightbend.model.modeldescriptor.ModelDescriptor

/**
  * Generic definition of a machine learning model
  */
trait Model [RECORD, RESULT]{
  /** Score a record with the model */
  def score(input : RECORD) : RESULT

  /** Abstraction for cleaning up resources */
  def cleanup() : Unit

  /** Serialize the model to bytes */
  def toBytes() : Array[Byte]

  /** Get the type of model. */
  def getType : ModelDescriptor.ModelType
}
