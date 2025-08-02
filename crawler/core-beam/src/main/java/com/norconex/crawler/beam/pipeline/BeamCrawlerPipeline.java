/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.beam.pipeline;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.beam.BeamCrawlerOptions;
import com.norconex.crawler.beam.config.BeamCrawlerConfig;
import com.norconex.crawler.beam.deduplication.Deduplicator;
import com.norconex.crawler.beam.discovery.UrlDiscovery;
import com.norconex.crawler.beam.fetch.DocumentFetcher;
import com.norconex.crawler.beam.frontier.FrontierUrl;
import com.norconex.crawler.beam.frontier.UrlFrontier;
import com.norconex.crawler.beam.scheduler.CrawlScheduler;
import com.norconex.importer.doc.Doc;

/**
 * Main pipeline for the beam crawler, orchestrating the entire crawl process.
 * @author Norconex Inc.
 */
public class BeamCrawlerPipeline implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(BeamCrawlerPipeline.class);
    
    private final BeamCrawlerConfig config;
    
    /**
     * Creates a new beam crawler pipeline with the specified configuration.
     * @param config the crawler configuration
     */
    public BeamCrawlerPipeline(BeamCrawlerConfig config) {
        this.config = config;
    }
    
    /**
     * Build and return the pipeline with all stages defined.
     * @param pipeline the Beam pipeline to build on
     * @return the configured pipeline
     */
    public Pipeline build(Pipeline pipeline) {
        BeamCrawlerOptions options = pipeline.getOptions().as(BeamCrawlerOptions.class);
        
        // Initialize components
        UrlFrontier frontier = new UrlFrontier(config.getFrontier());
        CrawlScheduler scheduler = new CrawlScheduler(config.getScheduler());
        DocumentFetcher fetcher = new DocumentFetcher();
        UrlDiscovery discovery = new UrlDiscovery(config.getDiscovery());
        Deduplicator deduplicator = new Deduplicator(config.getDeduplication());
        
        // Apply include/exclude patterns
        discovery.setIncludePatterns(config.getIncludePatterns());
        discovery.setExcludePatterns(config.getExcludePatterns());
        
        // Get tenant ID from options
        String tenantId = options.getTenantId();
        
        // Create initial seed URLs
        List<FrontierUrl> seedUrls = config.getStartUrls().stream()
                .map(url -> FrontierUrl.createSeed(url, tenantId))
                .collect(Collectors.toList());
        
        LOG.info("Starting crawl with {} seed URLs", seedUrls.size());
        
        // Create the initial PCollection from seed URLs
        PCollection<FrontierUrl> seeds = pipeline.apply(
                "CreateSeeds", Create.of(seedUrls));
        
        // Main crawling loop
        
        // 1. Deduplicate URLs
        PCollection<FrontierUrl> uniqueUrls = seeds.apply(
                "DeduplicateUrls", deduplicator.createUrlDeduplicationTransform());
        
        // 2. Schedule URLs based on politeness rules
        PCollection<KV<String, FrontierUrl>> scheduledUrls = uniqueUrls.apply(
                "ScheduleUrls", scheduler.createSchedulerTransform());
        
        // 3. Fetch documents
        PCollection<KV<FrontierUrl, Doc>> fetchedDocs = scheduledUrls.apply(
                "FetchDocuments", ParDo.of(new DoFn<KV<String, FrontierUrl>, KV<FrontierUrl, Doc>>() {
                    private static final long serialVersionUID = 1L;
                    
                    @ProcessElement
                    public void processElement(
                            @Element KV<String, FrontierUrl> element,
                            OutputReceiver<KV<FrontierUrl, Doc>> out) {
                        FrontierUrl url = element.getValue();
                        Doc doc = fetcher.fetch(url);
                        
                        if (doc != null) {
                            // Record fetch completed successfully
                            scheduler.notifyFetchCompleted(url, true, 
                                    System.currentTimeMillis() - url.getDiscoveryTime());
                            
                            out.output(KV.of(url, doc));
                        } else {
                            // Record fetch failed
                            scheduler.notifyFetchCompleted(url, false, 0);
                        }
                    }
                }));
        
        // 4. Deduplicate content
        PCollection<KV<FrontierUrl, Doc>> uniqueDocs = fetchedDocs.apply(
                "DeduplicateContent", deduplicator.createContentDeduplicationTransform());
        
        // 5. Process documents (apply transformers, enrichers, etc.)
        PCollection<KV<FrontierUrl, Doc>> processedDocs = uniqueDocs.apply(
                "ProcessDocuments", ParDo.of(new DoFn<KV<FrontierUrl, Doc>, KV<FrontierUrl, Doc>>() {
                    private static final long serialVersionUID = 1L;
                    
                    @ProcessElement
                    public void processElement(
                            @Element KV<FrontierUrl, Doc> element,
                            OutputReceiver<KV<FrontierUrl, Doc>> out) {
                        // Process document using Norconex Importer
                        // This is a simplified implementation
                        out.output(element);
                    }
                }));
        
        // 6. Discover new URLs
        PCollection<FrontierUrl> discoveredUrls = processedDocs.apply(
                "DiscoverUrls", discovery.createDiscoveryTransform(tenantId));
        
        // 7. Commit processed documents (to Solr, Elasticsearch, etc.)
        processedDocs.apply("CommitDocuments", ParDo.of(new DoFn<KV<FrontierUrl, Doc>, Void>() {
            private static final long serialVersionUID = 1L;
            
            @ProcessElement
            public void processElement(@Element KV<FrontierUrl, Doc> element) {
                // Commit using Norconex Committer
                // This is a simplified implementation
            }
        }));
        
        // Feed discovered URLs back to the start to continue crawling
        // In a real implementation, this would use a proper feedback loop mechanism
        
        return pipeline;
    }
}