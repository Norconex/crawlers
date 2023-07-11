package com.norconex.committer.apachekafka;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;

class KafkaAdmin {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAdmin.class);    
    private Admin admin;
    
    public KafkaAdmin(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        admin = Admin.create(props);
    }
    
    public void ensureTopicExists(
            String topicName, 
            int partitions, 
            short replicationFactor) throws CommitterException {
        
        if(isTopicExists(topicName)) {
            LOG.info("Topic `{}` already exists.", topicName);
            return;
        }
        
        createTopic(topicName, partitions, replicationFactor);
    }
    
    public boolean isTopicExists(String topicName) 
            throws CommitterException {
        ListTopicsResult topics = admin.listTopics();

        Set<String> topicNames = null;
        try {
            topicNames = topics.names().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new CommitterException("Could not list topics.", e);
        }

        if(topicNames == null) {
            return false;
        }
        
        return topicNames.contains(topicName);
    }

    public void close() {
        admin.close();
    }
    
    private void createTopic(
            String topicName, 
            int partitions, 
            short replicationFactor) throws CommitterException {
        LOG.info("Creating compacted topic `{}`...", topicName);

        CreateTopicsResult result = admin.createTopics(Collections
                .singleton(new NewTopic(topicName, partitions, replicationFactor)
                        .configs(Collections.singletonMap(
                                TopicConfig.CLEANUP_POLICY_CONFIG,
                                TopicConfig.CLEANUP_POLICY_COMPACT))));

        KafkaFuture<Void> future = result.values().get(topicName);

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new CommitterException(
                    "Could not create topic '" + topicName + "'.", e);
        }
        LOG.info("Done");
    }
}
