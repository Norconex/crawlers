/* Copyright 2023 Norconex Inc.
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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.CommitterUtil;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Commits documents to Kafka via it's Producer API
 * </p>
 *
 * <h3>createTopic</h3>
 * <p>
 * Whether to create the topic in Apache Kafka. 
 * It will be created only if it is not already present. Defaults to false.
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * committer class="com.norconex.committer.apachekafka.KafkaCommitter&gt;
 *      <bootstrapServers>
 *          (A list of host/port pairs in the form host1:port1,host2:port2,...
 *          to use for establishing a connection to the Kafka cluster)
 *      </bootstrapServers>
 *      <topicName>my-topic</topicName>
 *      <createTopic>
 *          [true|false](Whether to create topic in Apache Kafka)
 *      </createTopic>
 *      <numOfPartitions>
 *          (Number of partitions, if createTopic is set to <code>true</code>)
 *      </numOfPartitions>
 *      <replicationFactor>
 *          (Replication Factor, if createTopic is set to <code>true</code>)
 *      </replicationFactor>
 *
 *      {@nx.include com.norconex.committer.core.batch.AbstractBatchCommitter#options}
 *  </committer>
 * 
 *
 * {@nx.xml.example
 * <committer class="com.norconex.committer.apachekafka.KafkaCommitter">
 *   <bootstrapServers>http://some_host:1234</bootstrapServers>
 *   <topicName>my-topic</topicName>
 * </committer>
 * }
 * <p>
 * The above example uses the minimum required settings. It does not attempt
 * to create the topic. As such, topic must already exist in Apache Kafka.
 * </p>
 * 
 * @author Harinder Hanjan
 */

public class ApacheKafkaCommitter extends AbstractBatchCommitter {

    private static final Logger LOG = LoggerFactory
            .getLogger(ApacheKafkaCommitter.class);
    private static final String EXCEPTION_MSG_INVALID_CONFIG = 
            "Invalid configuration. Both `topicName` and `bootstrapServers` "
            + "are required.";
    private static final String BOOTSTRAP_SERVERS_CONFIG = "bootstrapServers";
    private static final String TOPIC_NAME_CONFIG = "topicName";
    private static final String CREATE_TOPIC_CONFIG = "createTopic";
    private static final String NUM_OF_PARTITIONS_CONFIG = "numOfPartitions";
    private static final String REPLICATION_FACTOR_CONFIG = "replicationFactor";
    private KafkaProducer<String, String> producer;
    private KafkaAdmin kafkaAdmin; 
    
    private String topicName;
    private String bootstrapServers;
    private boolean createTopic;
    private int partitions;
    private short replicationFactor;
    
    @Override
    protected void initBatchCommitter() throws CommitterException {
        if (    StringUtils.isBlank(topicName) || 
                StringUtils.isBlank(bootstrapServers)) {
            throw new CommitterException(EXCEPTION_MSG_INVALID_CONFIG);
        }
        
        kafkaAdmin = new KafkaAdmin(bootstrapServers);

        if(isCreateTopic()) {
            if(partitions == 0 || replicationFactor == 0) {
                String msg = String.format(
                        "%s=true requires these settings be also set. %s, %s",
                        CREATE_TOPIC_CONFIG,
                        NUM_OF_PARTITIONS_CONFIG, 
                        REPLICATION_FACTOR_CONFIG);
                throw new CommitterException(msg);
            }
            
            LOG.info("Ensuring topic `{}` exists in Kafka", topicName);
            kafkaAdmin.ensureTopicExists(
                    topicName, 
                    partitions, 
                    replicationFactor);
            
        } else if(! kafkaAdmin.isTopicExists(topicName)) {
            String msg = String.format("Topic `%s` does not exist in Kafka. "
                    + "Either create the topic manually or set `%s` to true.", 
                    topicName, CREATE_TOPIC_CONFIG);
            LOG.error(msg);            
            throw new CommitterException(msg);
        }
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {
        if(producer == null) {
            createProducer();
        }

        LOG.info("Committing batch to Apache Kafka");
        
        var docCountUpserts = 0;
        var docCountDeletes = 0;
        try {
            while (it.hasNext()) {
                var req = it.next();
                if (req instanceof UpsertRequest upsert) {
                    var json = new StringBuilder();
                    appendUpsertRequest(json, upsert);
                    
                    ProducerRecord<String, String> rec = new ProducerRecord<>
                        (getTopicName(), upsert.getReference(), json.toString());

                    producer.send(rec);
                    
                    docCountUpserts++;
                    json.setLength(0);
                    
                } else if (req instanceof DeleteRequest delete) {
                    var json = new StringBuilder();
            
                    ProducerRecord<String, String> rec = new ProducerRecord<>
                    (getTopicName(), delete.getReference(), null);

                    producer.send(rec);
                    
                    docCountDeletes++;
                    json.setLength(0);
                } else {
                    throw new CommitterException("Unsupported request: " + req);
                }
            }

            if(docCountUpserts > 0) {
                LOG.info("Sent {} upsert commit operation(s) to Apache Kafka.", 
                    docCountUpserts);
            }
            
            if(docCountDeletes> 0) {
                LOG.info("Sent {} delete commit operation(s) to Apache Kafka.", 
                    docCountDeletes);
            }
            
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Could not commit JSON batch to Elasticsearch.", e);
        }
    }
    
    @Override
    protected void closeBatchCommitter() throws CommitterException {
        LOG.info("Flushing and closing Kafka Producer client...");
        producer.flush();
        producer.close();
        LOG.info("Done");
        
        LOG.info("Closing Kafka Admin client");
        kafkaAdmin.close();
    }

    private synchronized KafkaProducer<String, String> createProducer() {        
        Properties props = new Properties();
        props.put("bootstrap.servers", getBootstrapServers());
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        
        producer = new KafkaProducer<>(props);
        
        LOG.info("Created Apache Kafka producer client");        
        return producer;
    }
    
    /**
     * Gets the topic name to which documents will be sent
     * 
     * @return name of the topic
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * Sets the topic name to which documents will be sent
     * 
     * @param   topicName   name of the topic
     */
    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }
    
    /**
     * Gets the Apache Kafka broker list
     * 
     * @return the list of Kafka brokers
     */
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * Sets the Apache Kafka broker list
     * 
     * @param   servers a CSV list of Kafka brokers of format 
     *                  host1:port1,host2:port2,...
     */
    public void setBootstrapServers(String servers) {
        this.bootstrapServers = servers;
    }
    
    /**
     * Gets whether to create the topic in Apache Kafka 
     * 
     * @return  <code>true</code> if topic should be created
     */
    public boolean isCreateTopic() {
        return createTopic;
    }

    /**
     * Sets whether to create the topic in Apache Kafka. 
     * It will be created only if it is not already present.
     * 
     * @param   createTopic whether topic should be created
     */
    public void setCreateTopic(boolean createTopic) {
        this.createTopic = createTopic;
    }
    
    /**
     * Gets the number of partitions for the new topic. 
     * Required if {@see #createTopic} is set to <code>true</code>
     * 
     * @return number of partitions
     */
    public int getNumOfPartitions() {
        return partitions;
    }

    /**
     * Sets the number of partitions for the new topic. 
     * Required if {@see #createTopic} is set to <code>true</code>
     * 
     * @param   numOfPartitions   number of partitions
     */
    public void setNumOfPartitions(int numOfPartitions) {
        this.partitions = numOfPartitions;
    }

    /**
     * Gets the replication factor for the new topic. 
     * Required if {@see #createTopic} is set to <code>true</code>
     */
    public short getReplicationFactor() {
        return replicationFactor;
    }

    /**
     * Sets the replication factor for the new topic. 
     * Required if {@see #createTopic} is set to <code>true</code>
     */
    public void setReplicationFactor(short replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    @Override
    protected void saveBatchCommitterToXML(XML writer) {
        writer.addElement(TOPIC_NAME_CONFIG, getTopicName());
        writer.addElement(BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        writer.addElement(CREATE_TOPIC_CONFIG, isCreateTopic());
        writer.addElement(NUM_OF_PARTITIONS_CONFIG, getNumOfPartitions());
        writer.addElement(REPLICATION_FACTOR_CONFIG, getReplicationFactor());
    }

    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        setTopicName(xml.getString(TOPIC_NAME_CONFIG));
        setBootstrapServers(xml.getString(BOOTSTRAP_SERVERS_CONFIG));
        setCreateTopic(xml.getBoolean(CREATE_TOPIC_CONFIG, isCreateTopic()));
        setNumOfPartitions(
                xml.getInteger(NUM_OF_PARTITIONS_CONFIG, getNumOfPartitions()));
        setReplicationFactor(
                xml.get(REPLICATION_FACTOR_CONFIG, 
                        Short.class, 
                        getReplicationFactor()));
    }
    
    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
    
    private void appendUpsertRequest(StringBuilder json, UpsertRequest upsert) 
            throws CommitterException {
        CommitterUtil.applyTargetContent(upsert, "content");
        
        json.append("{");
        appendFieldAndValue(json, "id", upsert.getReference());
        json.append(",");
        for (Map.Entry<String, List<String>> entry : 
                upsert.getMetadata().entrySet()) {
            String field = entry.getKey();
            append(json, field, entry.getValue());
            json.append(",");
        }
        json.deleteCharAt(json.length()-1);
        json.append("}\n");
    }
    
    private void append(StringBuilder json, String field, List<String> values) {
        if (values.size() == 1) {
            appendFieldAndValue(json, field, values.get(0));
            return;
        }
        
        json.append("\"")
                .append(StringEscapeUtils.escapeJson(field))
                .append("\":[");

        for (String value : values) {
            appendValue(json, value);
            json.append(',');
        }
        json.deleteCharAt(json.length()-1);
        json.append(']');
    }

    private void appendFieldAndValue(
            StringBuilder json, String field, String value) {
        json.append("\"")
                .append(StringEscapeUtils.escapeJson(field))
                .append("\":");
        appendValue(json, value);
    }

    private void appendValue(StringBuilder json, String value) {
        json.append("\"")
                .append(StringEscapeUtils.escapeJson(value))
                .append("\"");
    }
}