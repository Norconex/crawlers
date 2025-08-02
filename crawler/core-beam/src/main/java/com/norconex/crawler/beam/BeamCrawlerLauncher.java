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
package com.norconex.crawler.beam;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.beam.runners.direct.DirectRunner;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.beam.config.BeamCrawlerConfig;
import com.norconex.crawler.beam.pipeline.BeamCrawlerPipeline;

/**
 * Main entry point for the Norconex Beam Crawler.
 * Configures and launches the crawler pipeline with appropriate runner.
 * @author Norconex Inc.
 */
public class BeamCrawlerLauncher {
    private static final Logger LOG =
            LoggerFactory.getLogger(BeamCrawlerLauncher.class);

    /**
     * Main entry point for the crawler.
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        LOG.info("Starting Norconex Beam Crawler with args: {}",
                Arrays.toString(args));

        try {
            // Parse command line arguments
            BeamCrawlerOptions options = PipelineOptionsFactory.fromArgs(args)
                    .withValidation()
                    .as(BeamCrawlerOptions.class);

            // Load configuration file
            var config = loadConfiguration(options);

            // Create pipeline
            var pipeline = createPipeline(options);

            // Setup and run crawler pipeline
            var crawlerPipeline =
                    new BeamCrawlerPipeline(config);
            PipelineResult result = crawlerPipeline.build(pipeline).run();

            // If finite crawl, wait for completion
            if (!options.isContinuousCrawl()) {
                result.waitUntilFinish();
            } else {
                // For continuous crawling, keep the application running until user termination
                LOG.info(
                        "Running in continuous mode. Press Ctrl+C to terminate.");
                while (true) {
                    TimeUnit.MINUTES.sleep(1);
                    LOG.debug("Crawler still running in continuous mode...");
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to run crawler", e);
            System.exit(1);
        }
    }

    private static BeamCrawlerConfig
            loadConfiguration(BeamCrawlerOptions options) throws IOException {
        var configFile = new File(options.getConfigFile());
        if (!configFile.exists()) {
            throw new IOException("Configuration file not found: "
                    + configFile.getAbsolutePath());
        }
        return BeamCrawlerConfig.loadFromFile(configFile);
    }

    private static Pipeline createPipeline(BeamCrawlerOptions options) {
        // Use DirectRunner by default for local development
        if (options.getRunner() == null) {
            options.setRunner(DirectRunner.class);
        }

        return Pipeline.create(options);
    }
}