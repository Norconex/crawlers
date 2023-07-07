package com.norconex.committer.apachekafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.input.NullInputStream;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.map.Properties;

@Testcontainers(disabledWithoutDocker = true)
class ApacheKafkaCommitterTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            ApacheKafkaCommitterTest.class);
    private static String TOPIC_NAME = "";
    private static final String TEST_ID = "http://www.simpsons.com";
    private static final String TEST_CONTENT = "Homer says DOH!";
    private static Admin admin;
    private Consumer<String, String> consumer;
    
    @TempDir
    static File tempDir;
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
            
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        admin = createAdminClient();
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
        admin.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        TOPIC_NAME = String.valueOf(TimeIdGenerator.next());
        createTopic();
        consumer = createConsumerAndSubscribeToTopic();
    }

    @AfterEach
    void tearDown() throws Exception {
        consumer.close();
    }

    @Test
    void testAddOneDocumentWithMetadata_isAdded() throws CommitterException {
        //setup
        ConsumerRecord<String, String> expectedRecord = null;
        String expectedRecordValue = """
                {"id":"http:\\/\\/www.simpsons.com","category":"TV Show","sub-category":"Cartoon","content":"Homer says DOH!"}
                """;
        Properties props = new Properties();
        props.add("category", "TV Show");
        props.add("sub-category", "Cartoon");

        //execute
        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT, props));
        });
        
        //verify
        ConsumerRecords<String, String> records = consumer
                .poll(Duration.ofMillis(5000));

        for (ConsumerRecord<String, String> item : records) {
            expectedRecord = item;
        }
        
        assertThat(expectedRecord).isNotNull();
        assertThat(expectedRecord.key()).isEqualTo(TEST_ID);
        assertThat(expectedRecord.value()).isEqualTo(expectedRecordValue);
        
        consumer.close();        
    }
    
    @Test
    void testDeleteOneExistingDocument_isDeleted() 
            throws CommitterException, ExecutionException, InterruptedException {
        //setup
        ConsumerRecord<String, String> insertedRecord = null;
        String id = "http://www.thesimpsons.com";
        ProducerRecord<String, String> record = 
                new ProducerRecord<String, String>(
                        TOPIC_NAME,
                        id,
                        "Homer says DOH!");
        
        KafkaProducer<String, String> producer = createProducer();
        producer.send(record).get();
        producer.close();
        
        // //ensure record exists in Kafka
        ConsumerRecords<String, String> records = consumer
                .poll(Duration.ofMillis(5000));

        for (ConsumerRecord<String, String> item : records) {
            insertedRecord = item;
        }
        
        assertThat(insertedRecord).isNotNull();
        assertThat(insertedRecord.key()).isEqualTo(id);
        
        //execute
        withinCommitterSession(c -> {
           c.delete(new DeleteRequest(id, new Properties())); 
        });
        
        //verify
        ConsumerRecord<String, String> receivedRecord = null;
        Consumer<String, String> localConsumer = null;
        
        java.util.Properties props = new java.util.Properties();
        props.setProperty("bootstrap.servers", kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "nx-test-consumer234-" 
                + TOPIC_NAME);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        localConsumer = new KafkaConsumer<>(props);
        localConsumer.subscribe(Arrays.asList(TOPIC_NAME));
        
        ConsumerRecords<String, String> receivedRecords = localConsumer
                .poll(Duration.ofMillis(5000));

        for (ConsumerRecord<String, String> item : receivedRecords) {
            receivedRecord = item;
        }
        
        assertThat(receivedRecord).isNotNull();
        assertThat(receivedRecord.key()).isEqualTo(id);
        assertThat(receivedRecord.value()).isNull();
        
        
        localConsumer.close();
    }

    private UpsertRequest upsertRequest(
            String id, String content, Properties metadata) {
        Properties p = metadata == null ? new Properties() : metadata;
        return new UpsertRequest(id, p, content == null
                ? new NullInputStream(0) : toInputStream(content, UTF_8));
    }
    
    protected ApacheKafkaCommitter createApacheKafkaCommitter()
            throws CommitterException {

        CommitterContext ctx = CommitterContext.builder()
                .setWorkDir(new File(tempDir,
                        "" + TimeIdGenerator.next()).toPath())
                .build();
        ApacheKafkaCommitter committer = new ApacheKafkaCommitter();
        committer.setBootstrapServers(kafka.getBootstrapServers());
        committer.setTopicName(TOPIC_NAME);
        
        committer.init(ctx);
        return committer;
    }
    
    protected ApacheKafkaCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        ApacheKafkaCommitter committer = createApacheKafkaCommitter();
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
        return committer;
    }

    @FunctionalInterface
    protected interface CommitterConsumer {
        void accept(ApacheKafkaCommitter c) throws Exception;
    }
    
    private Consumer<String, String> createConsumerAndSubscribeToTopic(){
        java.util.Properties props = new java.util.Properties();
        props.setProperty("bootstrap.servers", kafka.getBootstrapServers());

        props.put(ConsumerConfig.GROUP_ID_CONFIG, "nx-test-consumer-" 
                + TOPIC_NAME);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        Consumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(TOPIC_NAME));
        LOG.info("Created consumer");
        return consumer;
    }
    
    private void createTopic() throws Exception {        
        // Create a compacted topic
        Map<String, String> topicConfig = new HashMap<>();
        topicConfig.put(TopicConfig.CLEANUP_POLICY_CONFIG, 
                TopicConfig.CLEANUP_POLICY_COMPACT);
        topicConfig.put(TopicConfig.DELETE_RETENTION_MS_CONFIG, "1");
        
        CreateTopicsResult result = admin.createTopics(
                Collections.singleton(new NewTopic(TOPIC_NAME, 1, (short) 1)
                        .configs(topicConfig)));

        KafkaFuture<Void> future = result.values().get(TOPIC_NAME);

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new Exception(
                    "Could not create topic '" + TOPIC_NAME + "'.", e);
        }
        LOG.info("Created topic `{}`...", TOPIC_NAME);
    }
    
    
    private static Admin createAdminClient() {
        java.util.Properties props = new java.util.Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, 
                    kafka.getBootstrapServers());
        return Admin.create(props);
    }

    private KafkaProducer<String, String> createProducer() {        
        java.util.Properties props = new java.util.Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
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
