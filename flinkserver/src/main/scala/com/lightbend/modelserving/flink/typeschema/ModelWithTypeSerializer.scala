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

package com.lightbend.modelserving.flink.typeschema

import java.io.IOException

import org.apache.flink.api.common.typeutils._
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.apache.flink.util.InstantiationUtil
import com.lightbend.modelserving.flink.ModelWithType
import com.lightbend.modelserving.model.ModelToServe

/** Serializer for a Model with State */
class ModelWithTypeSerializer[RECORD, RESULT] extends TypeSerializer[ModelWithType[RECORD, RESULT]] {

  override def createInstance(): ModelWithType[RECORD, RESULT] = new ModelWithType[RECORD, RESULT]("", null)

  override def canEqual(obj: scala.Any): Boolean = obj.isInstanceOf[ModelWithTypeSerializer[RECORD, RESULT]]

  override def duplicate(): TypeSerializer[ModelWithType[RECORD, RESULT]] = new ModelWithTypeSerializer[RECORD, RESULT]

  override def serialize(model: ModelWithType[RECORD, RESULT], target: DataOutputView): Unit = {
    target.writeUTF(model.dataType)
    target.writeUTF(model.modelWithName._1)
    val content = model.modelWithName._2.toBytes()
    target.writeLong(model.modelWithName._2.getType.value.toLong)
    target.writeLong(content.length)
    target.write(content)
  }

  override def isImmutableType: Boolean = false

  override def getLength: Int = -1

  override def snapshotConfiguration(): TypeSerializerSnapshot[ModelWithType[RECORD, RESULT]] = new ModelWithTypeSerializerConfigSnapshot[RECORD, RESULT]

  override def copy(from: ModelWithType[RECORD, RESULT]): ModelWithType[RECORD, RESULT] =
    new ModelWithType[RECORD, RESULT](from.dataType, (from.modelWithName._1, ModelToServe.copy(Some(from.modelWithName._2)).get))

  override def copy(from: ModelWithType[RECORD, RESULT], reuse: ModelWithType[RECORD, RESULT]): ModelWithType[RECORD, RESULT] = copy(from)

  override def copy(source: DataInputView, target: DataOutputView): Unit = {
    target.writeBoolean(source.readBoolean())
    target.writeUTF(source.readUTF())
    val exist = source.readBoolean()
    target.writeBoolean(exist)
    exist match {
      case true =>
        target.writeLong (source.readLong () )
        val clen = source.readLong ().asInstanceOf[Int]
        target.writeLong (clen)
        val content = new Array[Byte] (clen)
        source.read (content)
        target.write (content)
      case _ =>
    }
  }

  override def deserialize(source: DataInputView): ModelWithType[RECORD, RESULT] = {
    val dataType = source.readUTF()
    val name = source.readUTF()
    val t = source.readLong().asInstanceOf[Int]
    val size = source.readLong().asInstanceOf[Int]
    val content = new Array[Byte](size)
    source.read(content)
    ModelToServe.restore[RECORD, RESULT](t, content) match {
      case Some(model) => new ModelWithType[RECORD, RESULT](dataType, (name, model))
      case _ => new ModelWithType(dataType, null)
    }
  }

  override def deserialize(reuse: ModelWithType[RECORD, RESULT], source: DataInputView): ModelWithType[RECORD, RESULT] = deserialize(source)

  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[ModelWithTypeSerializer[RECORD, RESULT]]

  override def hashCode(): Int = 42
}

object ModelWithTypeSerializer{

  def apply[RECORD, RESULT] : ModelWithTypeSerializer[RECORD, RESULT] = new ModelWithTypeSerializer[RECORD, RESULT]()
}

object ModelWithTypeSerializerConfigSnapshot{

  val CURRENT_VERSION = 1
}

/**
  * Snapshot configuration for a Model with a type serializer
  * See https://github.com/apache/flink/blob/master/flink-core/src/main/java/org/apache/flink/api/common/typeutils/SimpleTypeSerializerSnapshot.java
  */
class ModelWithTypeSerializerConfigSnapshot[RECORD, RESULT] extends TypeSerializerSnapshot[ModelWithType[RECORD, RESULT]]{

  import ModelWithTypeSerializerConfigSnapshot._

  private var serializerClass = classOf[ModelWithTypeSerializer[RECORD, RESULT]]

  override def getCurrentVersion: Int = CURRENT_VERSION

  override def writeSnapshot(out: DataOutputView): Unit = {
    out.writeUTF(serializerClass.getName)
  }

  override def readSnapshot(readVersion: Int, in: DataInputView, classLoader: ClassLoader): Unit = {
    readVersion match {
      case CURRENT_VERSION =>
        val className = in.readUTF
        resolveClassName(className, classLoader, false)
       case _ =>
        throw new IOException("Unrecognized version: " + readVersion)
    }
  }

  override def restoreSerializer(): TypeSerializer[ModelWithType[RECORD, RESULT]] = InstantiationUtil.instantiate(serializerClass)

  override def resolveSchemaCompatibility(newSerializer: TypeSerializer[ModelWithType[RECORD, RESULT]]): TypeSerializerSchemaCompatibility[ModelWithType[RECORD, RESULT]] =
    if (newSerializer.getClass eq serializerClass) TypeSerializerSchemaCompatibility.compatibleAsIs()
    else TypeSerializerSchemaCompatibility.incompatible()

  private def resolveClassName(className: String, cl: ClassLoader, allowCanonicalName: Boolean): Unit =
    try
      serializerClass = cast(Class.forName(className, false, cl))
    catch {
      case e: Throwable =>
        throw new IOException("Failed to read SimpleTypeSerializerSnapshot: Serializer class not found: " + className, e)
  }

  private def cast[T](clazz: Class[_]) : Class[ModelWithTypeSerializer[RECORD, RESULT]]   = {
    if (!classOf[ModelWithTypeSerializer[RECORD, RESULT]].isAssignableFrom(clazz)) throw new IOException("Failed to read SimpleTypeSerializerSnapshot. " + "Serializer class name leads to a class that is not a TypeSerializer: " + clazz.getName)
    clazz.asInstanceOf[Class[ModelWithTypeSerializer[RECORD, RESULT]]]
  }
}
