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

package com.lightbend.modelserving.model.tensorflow

import java.io.{IOException, ObjectInputStream, ObjectOutputStream}

import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.modelserving.model.Model
import org.tensorflow.{Graph, Session}

// Abstract class for any Tensorflow (optimized export) model processing. It has to be extended by the user
// implement score method, based on his own model. Serializability here is required for Spark
abstract class TensorFlowModel(inputStream : Array[Byte]) extends Model with Serializable {

  // Make sure data is not empty
  if(inputStream.length < 1) throw new Exception("Empty graph data")
  // Model graph
  var graph = new Graph
  graph.importGraphDef(inputStream)
  // Create tensorflow session
  var session = new Session(graph)
  var bytes = inputStream

  // Cleanup
  override def cleanup(): Unit = {
    try{
      session.close
    }catch {
      case t: Throwable =>    // Swallow
    }
    try{
      graph.close
    }catch {
      case t: Throwable =>    // Swallow
    }
  }

  // Convert tensorflow model to bytes
  override def toBytes(): Array[Byte] = bytes

  // Get model type
  override def getType: Long = ModelDescriptor.ModelType.TENSORFLOW.value

  override def equals(obj: Any): Boolean = {
    obj match {
      case tfModel: TensorFlowModel =>
        tfModel.toBytes.toList == inputStream.toList
      case _ => false
    }
  }

  private def writeObject(output: ObjectOutputStream): Unit = {
    val start = System.currentTimeMillis()
    output.writeObject(bytes)
    println(s"Tensorflow java serialization in ${System.currentTimeMillis() - start} ms")
  }

  private def readObject(input: ObjectInputStream): Unit = {
    val start = System.currentTimeMillis()
    bytes = input.readObject().asInstanceOf[Array[Byte]]
    try{
      graph = new Graph
      graph.importGraphDef(bytes)
      session = new Session(graph)
      println(s"Tensorflow java deserialization in ${System.currentTimeMillis() - start} ms")
    }
    catch {
      case t: Throwable =>
        t.printStackTrace
        println(s"Tensorflow java deserialization failed in ${System.currentTimeMillis() - start} ms")
        println(s"Restored Tensorflow ${new String(bytes)}")
    }
  }
}