# Norconex Crawler Core - Hazelcast Implementation

Crawler-related code shared between different crawler implementations, using Hazelcast for distributed caching and clustering.

## Overview

This module provides a Hazelcast-based implementation for the Norconex Crawler Core's clustering capabilities. It replaces the original Infinispan implementation with Hazelcast, a powerful in-memory data grid solution.

## Features

- Distributed caching using Hazelcast Maps
- Cluster coordination and membership tracking
- Distributed counters for metrics and statistics
- Distributed task execution
- Cluster-wide locks for synchronization

## Configuration

The Hazelcast cluster can be configured using:

1. Default configuration (built-in)
2. Custom XML configuration file
3. Programmatic configuration via `HazelcastClusterConfig`

Example configuration:

```java
HazelcastClusterConfig config = new HazelcastClusterConfig();
config.setClusterName("my-crawler-cluster");
config.setMemberAddresses(Arrays.asList("192.168.1.10:5701", "192.168.1.11:5701"));
config.setConfigFile("/path/to/hazelcast.xml");

HazelcastCluster cluster = new HazelcastCluster(config);
```

Website: https://opensource.norconex.com/crawlers/core/