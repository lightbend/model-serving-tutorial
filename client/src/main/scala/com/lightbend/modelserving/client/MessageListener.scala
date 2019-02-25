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

package com.lightbend.modelserving.client

import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.ByteArrayDeserializer

/** Read messages from Kafka. */
object MessageListener {
  private val AUTOCOMMITINTERVAL = "1000" // Frequency off offset commits
  private val SESSIONTIMEOUT = "30000"    // The timeout used to detect failures - should be greater then processing time
  private val MAXPOLLRECORDS = "10"       // Max number of records consumed in a single poll

  def consumerProperties(brokers: String, group: String, keyDeserealizer: String, valueDeserealizer: String): Map[String, String] = {
    Map[String, String](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.GROUP_ID_CONFIG -> group,
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "true",
      ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG -> AUTOCOMMITINTERVAL,
      ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG -> SESSIONTIMEOUT,
      ConsumerConfig.MAX_POLL_RECORDS_CONFIG -> MAXPOLLRECORDS,
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> keyDeserealizer,
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> valueDeserealizer
    )
  }

  def apply[K, V](brokers: String, topic: String, group: String,
                  processor: RecordProcessorTrait[K, V]): MessageListener[K, V] =
    new MessageListener[K, V](brokers, topic, group, classOf[ByteArrayDeserializer].getName, classOf[ByteArrayDeserializer].getName, processor)
}

/** Read messages from Kafka. */
class MessageListener[K, V](
  brokers: String,
  topic: String,
  group: String,
  keyDeserealizer: String,
  valueDeserealizer: String,
  processor: RecordProcessorTrait[K, V]) extends Runnable {

  import MessageListener._

  import scala.collection.JavaConversions._

  val consumer = new KafkaConsumer[K, V](consumerProperties(brokers, group, keyDeserealizer, valueDeserealizer))
  consumer.subscribe(Seq(topic))
  var completed = false

  def complete(): Unit = {
    completed = true
  }

  override def run(): Unit = {
    while (!completed) {
      val records = consumer.poll(100)
      for (record <- records) {
        processor.processRecord(record)
      }
    }
    consumer.close()
    System.out.println("Listener completes")
  }

  def start(): Unit = {
    val t = new Thread(this)
    t.start()
  }
}
