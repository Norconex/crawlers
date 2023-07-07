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
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Commits documents to Kafka via it's Producer API
 * </p>
 * <h3>XML configuration usage:</h3>
 *
 * <pre>
 *  &lt;committer class="com.norconex.committer.apachekafka.KafkaCommitter&gt;
 *
 *      &lt;bootstrapServers&gt;...&lt;/bootstrapServers&gt;
 *      &lt;topicName&gt;...&lt;/topicName&gt;
 *
 *      &lt;commitBatchSize&gt;
 *          (max number of documents to send to Apache Kafka at once)
 *      &lt;/commitBatchSize&gt;
 *      &lt;queueDir&gt;(optional path where to queue files)&lt;/queueDir&gt;
 *      &lt;queueSize&gt;(max queue size before committing)&lt;/queueSize&gt;
 *      &lt;maxRetries&gt;(max retries upon commit failures)&lt;/maxRetries&gt;
 *      &lt;maxRetryWait&gt;(max delay in milliseconds between retries)&lt;/maxRetryWait&gt;
 *  &lt;/committer&gt;
 * </pre>
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
    
    private String topicName;
    private String bootstrapServers;
    private KafkaProducer<String, String> producer;

    @Override
    protected void initBatchCommitter() throws CommitterException {
        if (    StringUtils.isBlank(topicName) || 
                StringUtils.isBlank(bootstrapServers)) {
            throw new CommitterException(EXCEPTION_MSG_INVALID_CONFIG);
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
//System.out.println("Sending upsert-- " + rec.toString());
                    producer.send(rec);
                    
                    docCountUpserts++;
                    json.setLength(0);
                    
                } else if (req instanceof DeleteRequest delete) {
                    var json = new StringBuilder();
                    appendDeleteRequest(json, delete);
            
                    ProducerRecord<String, String> rec = new ProducerRecord<>
                    (getTopicName(), delete.getReference(), json.toString());

//System.out.println("Sending delete-- " + rec.toString());
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
    }

    private synchronized KafkaProducer<String, String> createProducer() {        
        Properties props = new Properties();
        props.put("bootstrap.servers", getBootstrapServers());
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        
        // ensure exactly once delivery
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        
        producer = new KafkaProducer<>(props);
        
        LOG.info("Created Apache Kafka producer client");        
        return producer;
    }
    
    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }
    
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String servers) {
        this.bootstrapServers = servers;
    }

    @Override
    protected void saveBatchCommitterToXML(XML writer) {

        if (StringUtils.isNotBlank(topicName)) {
            writer.addElement(TOPIC_NAME_CONFIG, getTopicName());
        }

        if (StringUtils.isNotBlank(bootstrapServers)) {
            writer.addElement(BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        }
    }

    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        setTopicName(xml.getString(TOPIC_NAME_CONFIG));
        setBootstrapServers(xml.getString(BOOTSTRAP_SERVERS_CONFIG));
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
    
    private void appendUpsertRequest(StringBuilder json, UpsertRequest upsert) {
//        String id = upsert.getMetadata().getString(getSourceReferenceField());
//        String id = upsert.getReference();
//        if (StringUtils.isBlank(id)) {
//            id = upsert.getReference();
//        }
        json.append("{");
        append(json, "id", upsert.getReference());
        json.append(",");
        for (Map.Entry<String, List<String>> entry : upsert.getMetadata().entrySet()) {
            String field = entry.getKey();
//            field = StringUtils.replace(field, ".", dotReplacement);

            // Remove id from source unless specified to keep it
//            if (!isKeepSourceReferenceField()
//                    && field.equals(getSourceReferenceField())) {
//                continue;
//            }
            append(json, field, entry.getValue());
        }
        json.append("}\n");
    }

    private void appendDeleteRequest(StringBuilder json, DeleteRequest del) {
        json.append("{\"delete\":{");
        append(json, "id", del.getReference());
        json.append("}}\n");
    }

    private void append(StringBuilder json, String field, List<String> values) {
        if (values.size() == 1) {
            append(json, field, values.get(0));
            return;
        }
        json.append('"')
                .append(StringEscapeUtils.escapeJson(field))
                .append("\":[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                json.append(',');
            }
            appendValue(json, field, value);
            first = false;
        }
        json.append(']');
    }

    private void append(StringBuilder json, String field, String value) {
        json.append('"')
                .append(StringEscapeUtils.escapeJson(field))
                .append("\":");
        appendValue(json, field, value);
    }

    private void appendValue(StringBuilder json, String field, String value) {
//        if (getJsonFieldsPattern() != null
//                && getJsonFieldsPattern().matches(field)) {
//            json.append(value);
//        } else {
            json.append('"')
                    .append(StringEscapeUtils.escapeJson(value))
                    .append("\"");
//        }
    }
}