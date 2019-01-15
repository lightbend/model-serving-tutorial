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

package com.lightbend.modelserving.flink.typeschema

import java.io.IOException

import com.lightbend.modelserving.model.Model
import com.lightbend.modelserving.model.ModelToServe
import org.apache.flink.api.common.typeutils._
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.apache.flink.util.InstantiationUtil

// Type serializer for model
class ModelTypeSerializer extends TypeSerializer[Option[Model]] {


  override def createInstance(): Option[Model] = None

  override def canEqual(obj: scala.Any): Boolean = obj.isInstanceOf[ModelTypeSerializer]

  override def duplicate(): TypeSerializer[Option[Model]] = new ModelTypeSerializer

  override def serialize(record: Option[Model], target: DataOutputView): Unit = {
    record match {
      case Some(model) =>
        target.writeBoolean(true)
        val content = model.toBytes()
        target.writeLong(model.getType)
        target.writeLong(content.length)
        target.write(content)
      case _ => target.writeBoolean(false)
    }
  }

  override def isImmutableType: Boolean = false

  override def getLength: Int = -1

  override def snapshotConfiguration(): TypeSerializerSnapshot[Option[Model]] = new ModelSerializerConfigSnapshot

  override def copy(from: Option[Model]): Option[Model] = {
    if(from == null) null
    else ModelToServe.copy(from)
  }

  override def copy(from: Option[Model], reuse: Option[Model]): Option[Model] = ModelToServe.copy(from)

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

  override def deserialize(source: DataInputView): Option[Model] =
    source.readBoolean() match {
      case true =>
        val t = source.readLong().asInstanceOf[Int]
        val size = source.readLong().asInstanceOf[Int]
        val content = new Array[Byte] (size)
        source.read (content)
        ModelToServe.restore(t, content)
      case _ => Option.empty
    }

  override def deserialize(reuse: Option[Model], source: DataInputView): Option[Model] = deserialize(source)

  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[ModelTypeSerializer]

  override def hashCode(): Int = 42
}

object ModelTypeSerializer{

  def apply : ModelTypeSerializer = new ModelTypeSerializer()
}

object ModelSerializerConfigSnapshot{

  val CURRENT_VERSION = 1
}

// Snapshot configuration Model serializer
// See https://github.com/apache/flink/blob/master/flink-core/src/main/java/org/apache/flink/api/common/typeutils/SimpleTypeSerializerSnapshot.java
class ModelSerializerConfigSnapshot extends TypeSerializerSnapshot[Option[Model]]{

  import ModelSerializerConfigSnapshot._

  private var serializerClass = classOf[ModelTypeSerializer]

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

  override def restoreSerializer(): TypeSerializer[Option[Model]] = InstantiationUtil.instantiate(serializerClass)

  override def resolveSchemaCompatibility(newSerializer: TypeSerializer[Option[Model]]): TypeSerializerSchemaCompatibility[Option[Model]] =
    if (newSerializer.getClass eq serializerClass) TypeSerializerSchemaCompatibility.compatibleAsIs()
    else TypeSerializerSchemaCompatibility.incompatible()

  private def resolveClassName(className: String, cl: ClassLoader, allowCanonicalName: Boolean): Unit =
    try
      serializerClass = cast(Class.forName(className, false, cl))
    catch {
      case e: Throwable =>
        throw new IOException("Failed to read SimpleTypeSerializerSnapshot: Serializer class not found: " + className, e)
    }

  @SuppressWarnings(Array("unchecked"))
  @throws[IOException]
  private def cast[T](clazz: Class[_]) : Class[ModelTypeSerializer]   = {
    if (!classOf[ModelTypeSerializer].isAssignableFrom(clazz)) throw new IOException("Failed to read SimpleTypeSerializerSnapshot. " + "Serializer class name leads to a class that is not a TypeSerializer: " + clazz.getName)
    clazz.asInstanceOf[Class[ModelTypeSerializer]]
  }

}
