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
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.xml.XML;

public class ApacheKafkaCommitter extends AbstractBatchCommitter {

    private static final Logger LOG = LoggerFactory
            .getLogger(ApacheKafkaCommitter.class);
    private static final String EXCEPTION_MSG_INVALID_CONFIG = 
            "Invalid configuration. Both `topicName` and `bootstrapServers` "
            + "are required.";
    private static final String BOOTSTRAP_SERVERS_CONFIG = "bootstrapServers";
    
    private String topicName;
    private String bootstrapServers;
    private KafkaProducer<String, String> producer;

    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void saveBatchCommitterToXML(XML xml) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {
        init();

    }

    private void init() throws CommitterException {
        if (producer != null) {
            return;
        }

        if (StringUtils.isBlank(topicName) || 
                StringUtils.isBlank(bootstrapServers)) {
            throw new CommitterException(EXCEPTION_MSG_INVALID_CONFIG);
        }
        
        Properties props = new Properties();
        props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);

        // ensure exactly once delivery
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
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
}