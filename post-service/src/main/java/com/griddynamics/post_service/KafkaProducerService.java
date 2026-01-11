package com.griddynamics.post_service;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.HashMap;
import java.util.Map;

public class KafkaProducerService {

  private KafkaProducer<String, String> producer;

  public KafkaProducerService(Vertx vertx) {
    Map<String, String> config = new HashMap<>();

    String kafkaBroker = System.getenv("KAFKA_BROKER");

    if (kafkaBroker == null || kafkaBroker.isEmpty()) {
      kafkaBroker = "localhost:9092";
    }

    System.out.println(">>> KAFKA CONFIG: Connecting to " + kafkaBroker);

    // Connection Config
    config.put("bootstrap.servers", kafkaBroker);
    config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    config.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    config.put("acks", "1");

    this.producer = KafkaProducer.create(vertx, config);
  }

  public void sendPostCreatedEvent(Long authorId) {
    // DEBUG LOG: Before sending
    System.out.println(">>> Attempting to send Kafka event for Author ID: " + authorId);

    KafkaProducerRecord<String, String> record =
      KafkaProducerRecord.create("post-created-topic", String.valueOf(authorId));

    producer.send(record)
      .onSuccess(v -> {
        // DEBUG LOG: Success
        System.out.println(">>> SUCCESS: Kafka Event sent!");
      })
      .onFailure(err -> {
        // DEBUG LOG: Failure
        System.err.println(">>> ERROR: Kafka send failed: " + err.getMessage());
        err.printStackTrace();
      });
  }
}
