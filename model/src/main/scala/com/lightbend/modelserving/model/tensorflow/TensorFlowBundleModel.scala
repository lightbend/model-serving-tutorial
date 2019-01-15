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

import java.io.File
import java.nio.file.Files

import com.google.protobuf.Descriptors
import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.modelserving.model.Model
import org.tensorflow.{SavedModelBundle}

import scala.collection.mutable.{Map => MMap}
import org.tensorflow.framework.{MetaGraphDef, SavedModel, SignatureDef, TensorInfo, TensorShapeProto}

import scala.collection.JavaConverters._


// Abstract class for any Tensorflow (SavedModelBundle) model processing. It has to be extended by the user
// implement score method, based on his own model
// This is a very simple implementation, assuming that the Tensorflow saved model bundle is local (constructor, get tags)
// The realistic implementation has to use some shared data storage, for example, S3, Minio, etc.

abstract class TensorFlowBundleModel(inputStream : Array[Byte]) extends Model {

  // Convert input into file path
  val path = new String(inputStream)
  // get tags. We assume here that the first tag is the one we use
  val tags = getTags(path)
  // get saved model bundle
  val bundle = SavedModelBundle.load(path, tags(0))
  // get grapth
  val graph = bundle.graph
  // get metatagraph and signature
  val metaGraphDef = MetaGraphDef.parseFrom(bundle.metaGraphDef)
  val signatureMap = metaGraphDef.getSignatureDefMap.asScala
  //  parse signature, so that we can use definitions (if necessary) programmatically in score method
  val parsedSign = parseSignatures(signatureMap)
  // Create tensorflow session
  val session = bundle.session

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
  override def toBytes(): Array[Byte] = inputStream

  // Get model type
  override def getType: Long = ModelDescriptor.ModelType.TENSORFLOWSAVED.value

  override def equals(obj: Any): Boolean = {
    obj match {
      case tfModel: TensorFlowBundleModel =>
        tfModel.toBytes.toList == inputStream.toList
      case _ => false
    }
  }

  // Parse signature
  private def parseSignatures(signatures : MMap[String, SignatureDef]) : Map[String, Signature] = {
    signatures.map(signature =>
      signature._1 -> Signature(parseInputOutput(signature._2.getInputsMap.asScala), parseInputOutput(signature._2.getOutputsMap.asScala))
    ).toMap
  }

  // Parse input and output
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

// Definition of the field (input/output)
case class Field(name : String, `type` : Descriptors.EnumValueDescriptor, shape : Seq[Int])

// Definition of the signature
case class Signature(inputs :  Map[String, Field], outputs :  Map[String, Field])
