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

package com.lightbend.modelserving.flink.typeschema

import org.apache.flink.api.common.serialization.{DeserializationSchema, SerializationSchema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor

// Byte Array Serialization schema used for Kafka messaging
class ByteArraySchema extends DeserializationSchema[Array[Byte]] with SerializationSchema[Array[Byte]] {

  private val serialVersionUID: Long = 1234567L

  override def isEndOfStream(nextElement: Array[Byte]): Boolean = false

  override def deserialize(message: Array[Byte]): Array[Byte] = message

  override def serialize(element: Array[Byte]): Array[Byte] = element

  override def getProducedType: TypeInformation[Array[Byte]] =
    TypeExtractor.getForClass(classOf[Array[Byte]])
}
