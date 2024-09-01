/* Copyright 2023-2024 Norconex Inc.
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

import com.norconex.committer.core.batch.BaseBatchCommitterConfig;

import lombok.Data;
import lombok.experimental.Accessors;

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
@Data
@Accessors(chain = true)
public class ApacheKafkaCommitterConfig extends BaseBatchCommitterConfig {

    /**
     * The topic name to which documents will be sent
     */
    private String topicName;

    /**
     * The Apache Kafka broker list, comma-separated
     * (e.g., host1:port1,host2:port2,...).
     */
    private String bootstrapServers;

    /**
     * Whether to create the topic in Apache Kafka.
     * It will be created only if it is not already present.
     */
    private boolean createTopic;

    /**
     * The number of partitions for the new topic.
     * Required if {@link #isCreateTopic()} is <code>true</code>
     */
    private int partitions;

    /**
     * Gets the replication factor for the new topic.
     * Required if {@link #isCreateTopic()} is <code>true</code>
     */
    private short replicationFactor;
}