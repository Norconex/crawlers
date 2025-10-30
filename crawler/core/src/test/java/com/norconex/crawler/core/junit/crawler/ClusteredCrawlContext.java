package com.norconex.crawler.core.junit.crawler;

import com.norconex.crawler.core.junit.cluster.SharedClusterClient;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
@Setter(AccessLevel.PACKAGE)
public class ClusteredCrawlContext {
    private ClusteredCrawlOuput ouput;
    private SharedClusterClient client;
    private int nodeCount;
}
