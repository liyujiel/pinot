/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.plugin.stream.kafka20.server;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.pinot.spi.data.readers.GenericRow;
import org.apache.pinot.spi.stream.StreamDataProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KafkaDataProducer implements StreamDataProducer {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaDataProducer.class);
  private Producer<byte[], byte[]> _producer;

  @Override
  public void init(Properties props) {
    if (!props.containsKey("bootstrap.servers")) {
      props.put("bootstrap.servers", props.get("metadata.broker.list"));
    }
    if (!props.containsKey("key.serializer")) {
      props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    }
    if (!props.containsKey("value.serializer")) {
      props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    }
    if (props.containsKey("partitioner.class")) {
      props.remove("partitioner.class");
    }
    props.remove("metadata.broker.list");
    props.remove("request.required.acks");
    props.remove("serializer.class");
    try {
      _producer = new KafkaProducer<>(props);
    } catch (Exception e) {
      LOGGER.error("Failed to create a Kafka 2 Producer.", e);
    }
  }

  @Override
  public void produce(String topic, byte[] payload) {
    _producer.send(new ProducerRecord<>(topic, payload));
    _producer.flush();
  }

  @Override
  public void produce(String topic, byte[] key, byte[] payload) {
    _producer.send(new ProducerRecord<>(topic, key, payload));
    _producer.flush();
  }

  @Override
  public void produce(String topic, byte[] key, byte[] payload, GenericRow headers) {
    List<Header> headerList = new ArrayList<>();
    headerList.add(new RecordHeader("producerTimestamp", String.valueOf(System.currentTimeMillis()).getBytes(
        StandardCharsets.UTF_8)));
    if (headers != null) {
      headers.getFieldToValueMap().forEach((k, v) -> {
        Header header = convertToKafkaHeader(k, v);
        if (header != null) {
          headerList.add(header);
        }
      });
    }
    _producer.send(new ProducerRecord<>(topic, null, key, payload, headerList));
    _producer.flush();
  }

  public Header convertToKafkaHeader(String key, Object value) {
    if (value != null) {
      if (value instanceof String) {
        return new RecordHeader(key, ((String) value).getBytes(StandardCharsets.UTF_8));
      }
      if (value instanceof Long) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(((Long) value));
        return new RecordHeader(key, buffer.array());
      }
    }
    return null;
  }

  @Override
  public void close() {
    _producer.close();
  }
}
