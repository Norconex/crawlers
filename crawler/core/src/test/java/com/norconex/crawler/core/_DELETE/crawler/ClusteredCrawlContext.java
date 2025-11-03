package com.norconex.crawler.core._DELETE.crawler;

import com.norconex.crawler.core._DELETE.clusterold.SharedClusterClient;

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
