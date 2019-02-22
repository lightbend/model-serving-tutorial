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

import com.lightbend.modelserving.model.Model
import com.lightbend.modelserving.model.ModelToServe
import org.apache.flink.api.common.typeutils._
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.apache.flink.util.InstantiationUtil

// Type serializer for model
class ModelTypeSerializer[RECORD, RESULT] extends TypeSerializer[Option[Model[RECORD, RESULT]]] {


  override def createInstance(): Option[Model[RECORD, RESULT]] = None

  override def canEqual(obj: scala.Any): Boolean = obj.isInstanceOf[ModelTypeSerializer[RECORD, RESULT]]

  override def duplicate(): TypeSerializer[Option[Model[RECORD, RESULT]]] = new ModelTypeSerializer[RECORD, RESULT]

  override def serialize(record: Option[Model[RECORD, RESULT]], target: DataOutputView): Unit = {
    record match {
      case Some(model) =>
        target.writeBoolean(true)
        val content = model.toBytes()
        target.writeLong(model.getType.value.toLong)
        target.writeLong(content.length)
        target.write(content)
      case _ => target.writeBoolean(false)
    }
  }

  override def isImmutableType: Boolean = false

  override def getLength: Int = -1

  override def snapshotConfiguration(): TypeSerializerSnapshot[Option[Model[RECORD, RESULT]]] = new ModelSerializerConfigSnapshot[RECORD, RESULT]

  override def copy(from: Option[Model[RECORD, RESULT]]): Option[Model[RECORD, RESULT]] = {
    if(from == null) null
    else ModelToServe.copy(from)
  }

  override def copy(from: Option[Model[RECORD, RESULT]], reuse: Option[Model[RECORD, RESULT]]): Option[Model[RECORD, RESULT]] = ModelToServe.copy(from)

  override def copy(source: DataInputView, target: DataOutputView): Unit = {
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

  override def deserialize(source: DataInputView): Option[Model[RECORD, RESULT]] =
    source.readBoolean() match {
      case true =>
        val t = source.readLong().asInstanceOf[Int]
        val size = source.readLong().asInstanceOf[Int]
        val content = new Array[Byte] (size)
        source.read (content)
        ModelToServe.restore(t, content)
      case _ => Option.empty
    }

  override def deserialize(reuse: Option[Model[RECORD, RESULT]], source: DataInputView): Option[Model[RECORD, RESULT]] = deserialize(source)

  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[ModelTypeSerializer[RECORD, RESULT]]

  override def hashCode(): Int = 42
}

object ModelTypeSerializer{

  def apply[RECORD, RESULT] : ModelTypeSerializer[RECORD, RESULT] = new ModelTypeSerializer[RECORD, RESULT]()
}

object ModelSerializerConfigSnapshot{

  val CURRENT_VERSION = 1
}

// Snapshot configuration Model serializer
// See https://github.com/apache/flink/blob/master/flink-core/src/main/java/org/apache/flink/api/common/typeutils/SimpleTypeSerializerSnapshot.java
class ModelSerializerConfigSnapshot[RECORD, RESULT] extends TypeSerializerSnapshot[Option[Model[RECORD, RESULT]]]{

  import ModelSerializerConfigSnapshot._

  private var serializerClass = classOf[ModelTypeSerializer[RECORD, RESULT]]

  override def getCurrentVersion: Int = CURRENT_VERSION

  override def writeSnapshot(out: DataOutputView): Unit = out.writeUTF(serializerClass.getName)


  override def readSnapshot(readVersion: Int, in: DataInputView, classLoader: ClassLoader): Unit = {
    readVersion match {
      case CURRENT_VERSION =>
        val className = in.readUTF
        resolveClassName(className, classLoader, false)
      case _ =>
        throw new IOException("Unrecognized version: " + readVersion)
    }
  }

  override def restoreSerializer(): TypeSerializer[Option[Model[RECORD, RESULT]]] = InstantiationUtil.instantiate(serializerClass)

  override def resolveSchemaCompatibility(newSerializer: TypeSerializer[Option[Model[RECORD, RESULT]]]): TypeSerializerSchemaCompatibility[Option[Model[RECORD, RESULT]]] =
    if (newSerializer.getClass eq serializerClass) TypeSerializerSchemaCompatibility.compatibleAsIs()
    else TypeSerializerSchemaCompatibility.incompatible()

  private def resolveClassName(className: String, cl: ClassLoader, allowCanonicalName: Boolean): Unit =
    try
      serializerClass = cast(Class.forName(className, false, cl))
    catch {
      case e: Throwable =>
        throw new IOException("Failed to read SimpleTypeSerializerSnapshot: Serializer class not found: " + className, e)
    }

  private def cast[T](clazz: Class[_]) : Class[ModelTypeSerializer[RECORD, RESULT]]   = {
    if (!classOf[ModelTypeSerializer[RECORD, RESULT]].isAssignableFrom(clazz)) throw new IOException("Failed to read SimpleTypeSerializerSnapshot. " + "Serializer class name leads to a class that is not a TypeSerializer: " + clazz.getName)
    clazz.asInstanceOf[Class[ModelTypeSerializer[RECORD, RESULT]]]
  }

}
