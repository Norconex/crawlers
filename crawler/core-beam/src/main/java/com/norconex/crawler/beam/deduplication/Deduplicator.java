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
package com.norconex.crawler.beam.deduplication;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.norconex.crawler.beam.frontier.FrontierUrl;
import com.norconex.crawler.beam.frontier.UrlNormalizer;
import com.norconex.importer.doc.Doc;

/**
 * Handles URL and content deduplication.
 * @author Norconex Inc.
 */
public class Deduplicator implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG =
            LoggerFactory.getLogger(Deduplicator.class);

    private final DeduplicationConfig config;
    private transient BloomFilter<CharSequence> urlFilter;
    private transient Map<String, String> fingerprintMap;
    private transient RedisDeduplicator redisDeduplicator;

    /**
     * Creates a new deduplicator with the specified configuration.
     * @param config the deduplication configuration
     */
    public Deduplicator(DeduplicationConfig config) {
        this.config = config;
        initialize();
    }

    private void initialize() {
        if ("memory".equals(config.getStorageType())) {
            // Use BloomFilter for in-memory URL deduplication
            urlFilter = BloomFilter.create(
                    Funnels.stringFunnel(StandardCharsets.UTF_8),
                    config.getMaxCapacity(),
                    0.01); // 1% false positive probability
            fingerprintMap = new ConcurrentHashMap<>();
        } else // Use Redis for distributed deduplication
        if (config.isEnableDistributed()
                && "redis".equals(config.getDistributedImplementation())) {
            redisDeduplicator = new RedisDeduplicator(
                    config.getRedisHost(),
                    config.getRedisPort(),
                    config.getRedisPassword());
        }
    }

    /**
     * Check if a URL has already been seen.
     * @param url the URL to check
     * @return true if the URL is new, false if it's a duplicate
     */
    public boolean isNewUrl(String url) {
        var normalizedUrl = UrlNormalizer.normalize(url);

        if (config.isEnableDistributed() && redisDeduplicator != null) {
            return redisDeduplicator.isNewUrl(normalizedUrl);
        }

        synchronized (urlFilter) {
            if (urlFilter.mightContain(normalizedUrl)) {
                return false;
            }
            urlFilter.put(normalizedUrl);
            return true;
        }
    }

    /**
     * Check if a document's content has already been seen.
     * @param doc the document to check
     * @param url the URL the document was fetched from
     * @return true if the content is new, false if it's a duplicate
     */
    public boolean isNewContent(Doc doc, String url) {
        if (!config.isEnableContentFingerprinting() || doc == null) {
            return true;
        }

        var fingerprint = generateContentFingerprint(doc);
        if (fingerprint == null) {
            return true;
        }

        if (config.isEnableDistributed() && redisDeduplicator != null) {
            return redisDeduplicator.isNewFingerprint(fingerprint, url);
        }

        synchronized (fingerprintMap) {
            if (fingerprintMap.containsKey(fingerprint)) {
                var existingUrl = fingerprintMap.get(fingerprint);
                LOG.debug("Duplicate content detected: {} is duplicate of {}",
                        url, existingUrl);
                return false;
            }

            fingerprintMap.put(fingerprint, url);
            if (config.isRecordMetadata()) {
                // Store additional metadata about the fingerprint if needed
            }

            return true;
        }
    }

    /**
     * Generate a fingerprint from document content.
     * @param doc the document to fingerprint
     * @return the content fingerprint
     */
    private String generateContentFingerprint(Doc doc) {
        try {
            // In a real implementation, this would use a proper hashing algorithm
            // based on the document content, leveraging Norconex Importer

            // For the skeleton, we return a simple hash code
            return String.valueOf(doc.hashCode());

        } catch (Exception e) {
            LOG.warn("Error generating content fingerprint", e);
            return null;
        }
    }

    /**
     * Create a Beam transform for URL deduplication.
     * @return the URL deduplication transform
     */
    public PTransform<PCollection<FrontierUrl>, PCollection<FrontierUrl>>
            createUrlDeduplicationTransform() {
        return new UrlDeduplicationTransform();
    }

    /**
     * Create a Beam transform for content deduplication.
     * @return the content deduplication transform
     */
    public PTransform<PCollection<KV<FrontierUrl, Doc>>,
            PCollection<KV<FrontierUrl, Doc>>>
            createContentDeduplicationTransform() {
        return new ContentDeduplicationTransform();
    }

    /**
     * Releases resources used by the deduplicator.
     */
    public void close() {
        if (redisDeduplicator != null) {
            redisDeduplicator.close();
        }
    }

    /**
     * Beam transform for URL deduplication.
     */
    private class UrlDeduplicationTransform extends
            PTransform<PCollection<FrontierUrl>, PCollection<FrontierUrl>> {
        private static final long serialVersionUID = 1L;

        @Override
        public PCollection<FrontierUrl> expand(PCollection<FrontierUrl> input) {
            return input.apply("DeduplicateUrls",
                    ParDo.of(new DoFn<FrontierUrl, FrontierUrl>() {
                        private static final long serialVersionUID = 1L;

                        private transient Set<String> urlsInBatch;

                        @Setup
                        public void setup() {
                            urlsInBatch = new HashSet<>();
                        }

                        @ProcessElement
                        public void processElement(@Element FrontierUrl url,
                                OutputReceiver<FrontierUrl> out) {
                            var normalizedUrl = url.getNormalizedUrl();

                            // First check for duplicates within this batch
                            if (urlsInBatch.contains(normalizedUrl)) {
                                return;
                            }

                            // Then check against the global deduplicator
                            if (isNewUrl(url.getUrl())) {
                                urlsInBatch.add(normalizedUrl);
                                out.output(url);
                            }
                        }

                        @Teardown
                        public void teardown() {
                            urlsInBatch.clear();
                        }
                    }));
        }
    }

    /**
     * Beam transform for content deduplication.
     */
    private class ContentDeduplicationTransform extends
            PTransform<PCollection<KV<FrontierUrl, Doc>>,
                    PCollection<KV<FrontierUrl, Doc>>> {
        private static final long serialVersionUID = 1L;

        @Override
        public PCollection<KV<FrontierUrl, Doc>>
                expand(PCollection<KV<FrontierUrl, Doc>> input) {
            return input.apply("DeduplicateContent",
                    ParDo.of(new DoFn<KV<FrontierUrl, Doc>,
                            KV<FrontierUrl, Doc>>() {
                        private static final long serialVersionUID = 1L;

                        @ProcessElement
                        public void processElement(
                                @Element KV<FrontierUrl, Doc> element,
                                OutputReceiver<KV<FrontierUrl, Doc>> out) {
                            FrontierUrl url = element.getKey();
                            Doc doc = element.getValue();

                            if (isNewContent(doc, url.getUrl())) {
                                out.output(element);
                            }
                        }
                    }));
        }
    }
}