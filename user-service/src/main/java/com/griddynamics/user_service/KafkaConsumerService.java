package com.griddynamics.user_service;

import com.griddynamics.user_service.repositories.UserRepository;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import java.util.HashMap;
import java.util.Map;

public class KafkaConsumerService {

  private KafkaConsumer<String, String> consumer;
  private UserRepository userRepository;

  public KafkaConsumerService(Vertx vertx, UserRepository userRepository) {
    this.userRepository = userRepository;

    // 1. Configure Consumer
    Map<String, String> config = new HashMap<>();

    String kafkaBroker = System.getenv("KAFKA_BROKER");

    if (kafkaBroker == null || kafkaBroker.isEmpty()) {
      kafkaBroker = "localhost:9092";
    }

    System.out.println(">>> KAFKA CONFIG: Connecting to " + kafkaBroker);
    config.put("bootstrap.servers", kafkaBroker);
    config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("group.id", "user-service-group"); // Important for tracking offsets
    config.put("auto.offset.reset", "earliest");

    this.consumer = KafkaConsumer.create(vertx, config);

    // 2. Register Handler
    consumer.handler(record -> {
      System.out.println(">>> Kafka Message Received. User ID: " + record.value());

      try {
        Long userId = Long.parseLong(record.value());

        // 3. Update DB
        userRepository.incrementPostCount(userId)
          .onSuccess(v -> System.out.println(">>> User " + userId + " post_count updated!"))
          .onFailure(err -> System.err.println(">>> DB Update failed: " + err.getMessage()));

      } catch (NumberFormatException e) {
        System.err.println("Invalid User ID in message: " + record.value());
      }
    });
  }

  // 3. Subscribe to Topic
  public void start() {
    consumer.subscribe("post-created-topic")
      .onSuccess(v -> System.out.println(">>> Subscribed to Kafka topic: post-created-topic"));
  }
}
