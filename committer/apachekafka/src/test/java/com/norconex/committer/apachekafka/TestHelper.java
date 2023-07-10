package com.norconex.committer.apachekafka;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestHelper {
    private String kafkaBrokers;
    private static Admin admin;
    private static final Logger LOG = LoggerFactory.getLogger(
            TestHelper.class);
    
    public TestHelper(String kafkaBootstrapServers) {
        kafkaBrokers = kafkaBootstrapServers;
        createAdminClient();
    }
    
    public void tearDown() {
        admin.close();
    }
    
    public Consumer<String, String> createConsumerAndSubscribeToTopic(
            String groupId, String topicName){
        java.util.Properties props = new java.util.Properties();
        props.setProperty("bootstrap.servers", kafkaBrokers);

        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        Consumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(topicName));
        LOG.info("Created consumer");
        return consumer;
    }
    
    public void createTopic(String topicName) throws Exception {        
        // Create a compacted topic
        Map<String, String> topicConfig = new HashMap<>();
        topicConfig.put(TopicConfig.CLEANUP_POLICY_CONFIG, 
                TopicConfig.CLEANUP_POLICY_COMPACT);
        
        CreateTopicsResult result = admin.createTopics(
                Collections.singleton(new NewTopic(topicName, 1, (short) 1)
                        .configs(topicConfig)));

        KafkaFuture<Void> future = result.values().get(topicName);

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new Exception(
                    "Could not create topic '" + topicName + "'.", e);
        }
        LOG.info("Created topic `{}`...", topicName);
    }
    
    
    private void createAdminClient() {
        java.util.Properties props = new java.util.Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokers);
        admin = Admin.create(props);
    }

    public KafkaProducer<String, String> createProducer() {        
        java.util.Properties props = new java.util.Properties();
        props.put("bootstrap.servers", kafkaBrokers);
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        
        return new KafkaProducer<>(props);
    }
}
