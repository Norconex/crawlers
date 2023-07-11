package com.norconex.committer.apachekafka;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
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
            short replicationFactor) {
        
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
}
