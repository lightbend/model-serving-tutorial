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

package com.lightbend.modelserving.configuration

/**
  * Created by boris on 5/10/17.
  */
object ModelServingConfiguration {

  val ZOOKEEPER_HOST = "localhost:2181"
  val KAFKA_BROKER = "localhost:9092"

  val DATA_TOPIC = "mdata"
  val MODELS_TOPIC = "models"

  val DATA_GROUP = "wineRecordsGroup"
  val MODELS_GROUP = "modelRecordsGroup"

  val CHECKPOINT_DIR = "checkpoint"

  val MODELSERVING_PORT = 5500
}
