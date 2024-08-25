package com.norconex.committer.apachekafka;

import java.util.Arrays;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestHelper {
    private String kafkaBrokers;
    private static final Logger LOG = LoggerFactory.getLogger(
            TestHelper.class);

    public TestHelper(String kafkaBootstrapServers) {
        kafkaBrokers = kafkaBootstrapServers;
    }

    public Consumer<String, String> createConsumerAndSubscribeToTopic(
            String groupId, String topicName) {
        var props = new java.util.Properties();
        props.setProperty("bootstrap.servers", kafkaBrokers);

        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        props.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        Consumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(topicName));
        LOG.info("Created consumer");
        return consumer;
    }

    public KafkaProducer<String, String> createProducer() {
        var props = new java.util.Properties();
        props.put("bootstrap.servers", kafkaBrokers);
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        return new KafkaProducer<>(props);
    }
}
