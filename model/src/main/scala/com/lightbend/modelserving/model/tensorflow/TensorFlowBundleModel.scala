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

import java.io.{File, ObjectInputStream, ObjectOutputStream}
import java.nio.file.Files

import com.google.protobuf.Descriptors
import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.modelserving.model.Model
import org.tensorflow.{Graph, SavedModelBundle, Session}

import scala.collection.mutable.{Map => MMap}
import org.tensorflow.framework.{MetaGraphDef, SavedModel, SignatureDef, TensorInfo, TensorShapeProto}

import scala.collection.JavaConverters._

/**
  * Abstract class for any TensorFlow (SavedModelBundle) model processing. It has to be extended by the user
  * implement score method, based on his own model
  * This is a very simple implementation, assuming that the TensorFlow saved model bundle is local (constructor, get tags)
  * The realistic implementation has to use some shared data storage, for example, S3, Minio, etc.
  */
abstract class TensorFlowBundleModel[RECORD,RESULT](inputStream : Array[Byte]) extends Model[RECORD,RESULT]  with Serializable {

  var bytes = inputStream
  setup()
  var tags : Seq[String] = _
  var graph : Graph = _
  var signatures : Map[String, Signature] = _
  var session : Session = _

  private def setup() : Unit = {
    // Convert input into file path
    val path = new String(bytes)
    // get tags. We assume here that the first tag is the one we use
    tags = getTags(path)
    // get saved model bundle
    val bundle = SavedModelBundle.load(path, tags.head)
    // get grapth
    graph = bundle.graph
    // get metatagraph and signature
    val metaGraphDef = MetaGraphDef.parseFrom(bundle.metaGraphDef)
    val signatureMap = metaGraphDef.getSignatureDefMap.asScala
    //  parse signature, so that we can use definitions (if necessary) programmatically in score method
    signatures = parseSignatures(signatureMap)
    // Create TensorFlow session
    session = bundle.session
  }

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

  /** Convert TensorFlow model to bytes */
  override def toBytes(): Array[Byte] = bytes

  /** Get model type */
  override def getType: ModelDescriptor.ModelType = ModelDescriptor.ModelType.TENSORFLOWSAVED

  override def equals(obj: Any): Boolean = {
    obj match {
      case tfModel: TensorFlowBundleModel[RECORD,RESULT] =>
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
      setup()
      println(s"TensorFlow java deserialization in ${System.currentTimeMillis() - start} ms")
    }
    catch {
      case t: Throwable =>
        t.printStackTrace
        println(s"TensorFlow java deserialization failed in ${System.currentTimeMillis() - start} ms")
        println(s"Restored TensorFlow ${new String(bytes)}")
    }
  }

  private def parseSignatures(signatures : MMap[String, SignatureDef]) : Map[String, Signature] = {
    signatures.map(signature =>
      signature._1 -> Signature(parseInputOutput(signature._2.getInputsMap.asScala), parseInputOutput(signature._2.getOutputsMap.asScala))
    ).toMap
  }

  private def parseInputOutput(inputOutputs : MMap[String, TensorInfo]) : Map[String, Field] = {
    inputOutputs.map(inputOutput => {
      var name = ""
      var dtype : Descriptors.EnumValueDescriptor = null
      var shape = Seq.empty[Int]
      inputOutput._2.getAllFields.asScala.foreach(descriptor => {
        if(descriptor._1.getName.contains("shape") ){
          descriptor._2.asInstanceOf[TensorShapeProto].getDimList.toArray.map(d =>
            d.asInstanceOf[TensorShapeProto.Dim].getSize).toSeq.foreach(v => shape = shape :+ v.toInt)

        }
        if(descriptor._1.getName.contains("name") ) {
          name = descriptor._2.toString.split(":")(0)
        }
        if(descriptor._1.getName.contains("dtype") ) {
          dtype = descriptor._2.asInstanceOf[Descriptors.EnumValueDescriptor]
        }
      })
      inputOutput._1 -> Field(name,dtype, shape)
    }).toMap
  }

  // This method gets all tags in the saved bundle and uses the first one. If you need a specific tag, overwrite this method
  // With a seq (of one) tags returning desired tag.
  protected def getTags(directory : String) : Seq[String] = {
    val d = new File(directory)
    val pbfiles = if (d.exists && d.isDirectory)
      d.listFiles.filter(_.isFile).filter(name => (name.getName.endsWith("pb") || name.getName.endsWith("pbtxt"))).toList
    else List[File]()
    if(pbfiles.length > 0) {
      val byteArray = Files.readAllBytes(pbfiles(0).toPath)
      SavedModel.parseFrom(byteArray).getMetaGraphsList.asScala.
        flatMap(graph => graph.getMetaInfoDef.getTagsList.asByteStringList.asScala.map(_.toStringUtf8))
    }
    else
      Seq.empty
  }
}

/** Definition of the field (input/output) */
case class Field(name : String, `type` : Descriptors.EnumValueDescriptor, shape : Seq[Int])

/** Definition of the signature */
case class Signature(inputs :  Map[String, Field], outputs :  Map[String, Field])
