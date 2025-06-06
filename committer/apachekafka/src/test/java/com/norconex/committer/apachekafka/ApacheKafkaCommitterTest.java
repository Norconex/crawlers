/* Copyright 2024 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.norconex.committer.apachekafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.input.NullInputStream;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.map.Properties;

@Testcontainers(disabledWithoutDocker = true)
class ApacheKafkaCommitterTest {

    private static String topicName = "";
    private static final String TEST_ID = "http://www.simpsons.com";
    private static final String TEST_CONTENT = "Homer says DOH!";
    private Consumer<String, String> consumer;
    private static TestHelper testHelper;

    @TempDir
    static File tempDir;

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @BeforeAll
    static void setUpBeforeClass() {
        testHelper = new TestHelper(kafka.getBootstrapServers());
    }

    @BeforeEach
    void setUp() {
        topicName = String.valueOf(TimeIdGenerator.next());
        consumer = testHelper.createConsumerAndSubscribeToTopic(
                "nx-test-consumer-" + topicName,
                topicName);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void testAddOneDocumentWithMetadata_isAdded() throws CommitterException {
        //setup
        ConsumerRecord<String, String> expectedRecord = null;
        var expectedRecordValue =
                """
                {"id":"http:\\/\\/www.simpsons.com","category":"TV Show",\
                "sub-category":"Cartoon","content":"Homer says DOH!"}
                """;
        var metadata = new Properties();
        metadata.add("category", "TV Show");
        metadata.add("sub-category", "Cartoon");

        //execute
        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT, metadata));
        });

        //verify
        var records = consumer
                .poll(Duration.ofMillis(5000));

        for (ConsumerRecord<String, String> item : records) {
            expectedRecord = item;
        }

        assertThat(expectedRecord).isNotNull();
        assertThat(expectedRecord.key()).isEqualTo(TEST_ID);
        assertThat(expectedRecord.value()).isEqualTo(expectedRecordValue);
    }

    @Test
    void testDeleteOneExistingDocument_isDeleted()
            throws CommitterException, ExecutionException,
            InterruptedException {
        //setup
        ConsumerRecord<String, String> insertedRecord = null;
        var id = "http://www.thesimpsons.com";
        var rec =
                new ProducerRecord<>(
                        topicName,
                        id,
                        "Homer says DOH!");

        var producer =
                testHelper.createProducer();
        producer.send(rec).get();
        producer.close();

        // //ensure record exists in Kafka
        var records = consumer
                .poll(Duration.ofMillis(5000));

        assertThat(records)
                .isNotNull()
                .hasSize(1);

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
        var localConsumer =
                testHelper.createConsumerAndSubscribeToTopic(
                        "nx-test-localconsumer" + topicName,
                        topicName);

        var receivedRecords = localConsumer
                .poll(Duration.ofMillis(5000));
        localConsumer.close();

        for (ConsumerRecord<String, String> item : receivedRecords) {
            receivedRecord = item;
        }

        assertThat(receivedRecord).isNotNull();
        assertThat(receivedRecord.key()).isEqualTo(id);
        assertThat(receivedRecord.value()).isNull();
    }

    @Test
    void testAddMultiValueFields_isAdded() throws Exception {
        //setup
        ConsumerRecord<String, String> expectedRecord = null;
        var expectedRecordValue =
                """
                        {"id":"http:\\/\\/www.simpsons.com","content":"","multi":["1","2","3"]}
                        """;
        var metadata = new Properties();
        metadata.set("multi", "1", "2", "3");

        //execute
        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        //verify
        var records = consumer
                .poll(Duration.ofMillis(5000));

        for (ConsumerRecord<String, String> item : records) {
            expectedRecord = item;
        }

        assertThat(expectedRecord).isNotNull();
        assertThat(expectedRecord.key()).isEqualTo(TEST_ID);
        assertThat(expectedRecord.value()).isEqualTo(expectedRecordValue);
    }

    @Test
    void createTopicSetWithoutPartitionOrReplication_throwsException() {
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            withinCommitterSessionCreateTopicOnly(c -> {
                c.upsert(upsertRequest(TEST_ID, null, new Properties()));
            });
        }).withMessageContaining("createTopic=true requires these settings "
                + "be also set: numOfPartitions, replicationFactor");
    }

    @Test
    void testCreateTopicIsFalseAndTopicDoesNotAlreadyExist_throwsException() {
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            withinCommitterSessionTopicDoesNotExist(c -> {
                c.upsert(upsertRequest(TEST_ID, null, new Properties()));
            });
        }).withMessageContaining("Topic `%s` does not exist in Kafka. Either "
                + "create the topic manually or set `createTopic` to true.",
                topicName);
    }

    private UpsertRequest upsertRequest(
            String id, String content, Properties metadata) {
        var p = metadata == null ? new Properties() : metadata;
        return new UpsertRequest(
                id, p, content == null
                        ? new NullInputStream(0)
                        : toInputStream(content, UTF_8));
    }

    protected ApacheKafkaCommitter createApacheKafkaCommitter()
            throws CommitterException {

        var ctx = CommitterContext.builder()
                .setWorkDir(
                        new File(tempDir, "" + TimeIdGenerator.next()).toPath())
                .build();
        var committer = new ApacheKafkaCommitter();
        committer.getConfiguration()
                .setBootstrapServers(kafka.getBootstrapServers())
                .setTopicName(topicName)
                .setCreateTopic(true)
                .setPartitions(1)
                .setReplicationFactor((short) 1);

        committer.init(ctx);
        return committer;
    }

    protected ApacheKafkaCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        var committer = createApacheKafkaCommitter();
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

    protected ApacheKafkaCommitter createApacheKafkaCommitterCreateTopicOnly()
            throws CommitterException {

        var ctx = CommitterContext.builder()
                .setWorkDir(
                        new File(tempDir, "" + TimeIdGenerator.next()).toPath())
                .build();
        var committer = new ApacheKafkaCommitter();
        committer.getConfiguration()
                .setBootstrapServers(kafka.getBootstrapServers())
                .setTopicName(topicName)
                .setCreateTopic(true);

        committer.init(ctx);
        return committer;
    }

    protected ApacheKafkaCommitter withinCommitterSessionCreateTopicOnly(
            CommitterConsumer c)
            throws CommitterException {
        var committer = createApacheKafkaCommitterCreateTopicOnly();
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

    protected ApacheKafkaCommitter createApacheKafkaCommitterTopicDoesNotExist()
            throws CommitterException {

        var ctx = CommitterContext.builder()
                .setWorkDir(
                        new File(tempDir, "" + TimeIdGenerator.next()).toPath())
                .build();
        var committer = new ApacheKafkaCommitter();
        committer.getConfiguration()
                .setBootstrapServers(kafka.getBootstrapServers())
                .setTopicName(topicName)
                .setCreateTopic(false);

        committer.init(ctx);
        return committer;
    }

    protected ApacheKafkaCommitter withinCommitterSessionTopicDoesNotExist(
            CommitterConsumer c)
            throws CommitterException {
        var committer = createApacheKafkaCommitterTopicDoesNotExist();
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
}
