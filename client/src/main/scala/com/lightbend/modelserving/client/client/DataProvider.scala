/*
 * Copyright (C) 2019  Lightbend
 *
 * This file is part of flink-ModelServing
 *
 * flink-ModelServing is free software: you can redistribute it and/or modify
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

package com.lightbend.modelserving.client.client

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Paths}

import com.google.protobuf.ByteString
import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.client.{KafkaLocalServer, MessageSender}
import com.lightbend.modelserving.configuration.ModelServingConfiguration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

/**
 * Created by boris on 5/10/17.
 *
 * Application publishing models from /data directory to Kafka
 */
object DataProvider {

  import ModelServingConfiguration._

  val file = "data/winequality_red.csv"
  val directory = "data/"
  val tensorfile = "data/optimized_WineQuality.pb"
  val tensorfilebundle = "data/saved/1/"
  var modelTimeInterval = 1000 * 60 * 1 // 1 mins
  var dataTimeInterval = 1000 * 1 // 1 sec

  def main(args: Array[String]) {

    println(s"Using kafka brokers at $KAFKA_BROKER")
    println(s"Data Message delay $dataTimeInterval")
    println(s"Model Message delay $modelTimeInterval")

    val kafka = KafkaLocalServer(true)
    kafka.start()
    kafka.createTopic(DATA_TOPIC)
    kafka.createTopic(MODELS_TOPIC)

    println(s"Cluster created")

    publishData()
    publishModels()

    while(true)
      pause(600000)
  }

  def publishData() : Future[Unit] = Future {

    val sender = MessageSender(KAFKA_BROKER)
    val bos = new ByteArrayOutputStream()
    val records = getListOfDataRecords(file)
    var nrec = 0
    while (true) {
      records.foreach(record => {
        val r = record.withTs(System.currentTimeMillis())
        bos.reset()
        r.writeTo(bos)
        sender.writeValue(DATA_TOPIC, bos.toByteArray)
        nrec = nrec + 1
        if (nrec % 10 == 0)
          println(s"printed $nrec records")
        pause(dataTimeInterval)
      })
    }
  }

  def publishModels() : Future[Unit] = Future {

    val sender = MessageSender(KAFKA_BROKER)
    val files = getListOfModelFiles(directory)
    val bos = new ByteArrayOutputStream()
    while (true) {
      // TF model bundled
      val tbRecord = ModelDescriptor(name = "tensorflow saved model",
        description = "generated from TensorFlow saved bundle", modeltype =
          ModelDescriptor.ModelType.TENSORFLOWSAVED, dataType = "wine").
        withLocation(tensorfilebundle)
      bos.reset()
      tbRecord.writeTo(bos)
      sender.writeValue(MODELS_TOPIC, bos.toByteArray)
      println(s"Published Model ${tbRecord.description}")
      pause(modelTimeInterval)
      files.foreach(f => {
        // PMML
        val pByteArray = Files.readAllBytes(Paths.get(directory + f))
        val pRecord = ModelDescriptor(
          name = f.dropRight(5),
          description = "generated from SparkML", modeltype = ModelDescriptor.ModelType.PMML,
          dataType = "wine"
        ).withData(ByteString.copyFrom(pByteArray))
        bos.reset()
        pRecord.writeTo(bos)
        sender.writeValue(MODELS_TOPIC, bos.toByteArray)
        println(s"Published Model ${pRecord.description}")
        pause(modelTimeInterval)
      })

      // TF
      val tByteArray = Files.readAllBytes(Paths.get(tensorfile))
      val tRecord = ModelDescriptor(name = tensorfile.dropRight(3),
        description = "generated from TensorFlow", modeltype = ModelDescriptor.ModelType.TENSORFLOW,
        dataType = "wine").withData(ByteString.copyFrom(tByteArray))
      bos.reset()
      tRecord.writeTo(bos)
      sender.writeValue(MODELS_TOPIC, bos.toByteArray)
      println(s"Published Model ${tRecord.description}")
      pause(modelTimeInterval)
    }
  }

  private def pause(timeInterval : Long): Unit = {
    try {
      Thread.sleep(timeInterval)
    } catch {
      case _: Throwable => // Ignore
    }
  }

  def getListOfDataRecords(file: String): Seq[WineRecord] = {

    var result = Seq.empty[WineRecord]
    val bufferedSource = Source.fromFile(file)
    for (line <- bufferedSource.getLines) {
      val cols = line.split(";").map(_.trim)
      val record = new WineRecord(
        fixedAcidity = cols(0).toDouble,
        volatileAcidity = cols(1).toDouble,
        citricAcid = cols(2).toDouble,
        residualSugar = cols(3).toDouble,
        chlorides = cols(4).toDouble,
        freeSulfurDioxide = cols(5).toDouble,
        totalSulfurDioxide = cols(6).toDouble,
        density = cols(7).toDouble,
        pH = cols(8).toDouble,
        sulphates = cols(9).toDouble,
        alcohol = cols(10).toDouble,
        dataType = "wine"
      )
      result = record +: result
    }
    bufferedSource.close
    result
  }

  private def getListOfModelFiles(dir: String): Seq[String] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(f => (f.isFile) && (f.getName.endsWith(".pmml"))).map(_.getName)
    } else {
      Seq.empty[String]
    }
  }
}
