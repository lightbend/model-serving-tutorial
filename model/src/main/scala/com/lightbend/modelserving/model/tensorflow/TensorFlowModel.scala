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

package com.lightbend.modelserving.model.tensorflow

import java.io.{IOException, ObjectInputStream, ObjectOutputStream}

import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.modelserving.model.Model
import org.tensorflow.{Graph, Session}

/**
  * Abstract class for any TensorFlow (optimized export) model processing. It has to be extended by the user
  * implement score method, based on his own model. Serializability here is required for Spark.
  */
abstract class TensorFlowModel[RECORD,RESULT](inputStream : Array[Byte]) extends Model[RECORD,RESULT] with Serializable {

  // Make sure data is not empty
  if(inputStream.length < 1) throw new Exception("Empty graph data")
  // Model graph
  var graph = new Graph
  graph.importGraphDef(inputStream)
  // Create TensorFlow session
  var session = new Session(graph)
  var bytes = inputStream

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

  /** Convert the TensorFlow model to bytes */
  override def toBytes(): Array[Byte] = bytes

  /** Get model type */
  override def getType: ModelDescriptor.ModelType = ModelDescriptor.ModelType.TENSORFLOW

  override def equals(obj: Any): Boolean = {
    obj match {
      case tfModel: TensorFlowModel[RECORD,RESULT] =>
        tfModel.toBytes.toList == inputStream.toList
      case _ => false
    }
  }

  private def writeObject(output: ObjectOutputStream): Unit = {
    val start = System.currentTimeMillis()
    output.writeObject(bytes)
    println(s"TensorFlow java serialization in ${System.currentTimeMillis() - start} ms")
  }

  private def readObject(input: ObjectInputStream): Unit = {
    val start = System.currentTimeMillis()
    bytes = input.readObject().asInstanceOf[Array[Byte]]
    try{
      graph = new Graph
      graph.importGraphDef(bytes)
      session = new Session(graph)
      println(s"TensorFlow java deserialization in ${System.currentTimeMillis() - start} ms")
    }
    catch {
      case t: Throwable =>
        t.printStackTrace
        println(s"TensorFlow java deserialization failed in ${System.currentTimeMillis() - start} ms")
        println(s"Restored TensorFlow ${new String(bytes)}")
    }
  }
}
