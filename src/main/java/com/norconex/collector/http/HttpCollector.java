/* Copyright 2010-2018 Norconex Inc.
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
package com.norconex.collector.http;

import com.norconex.collector.core.Collector;
import com.norconex.collector.core.cmdline.CollectorCommandLauncher;
import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;

/**
 * Main application class.
 * Instances of this class can hold several crawler, running at once.
 * This is convenient when there are configuration setting to be shared amongst
 * crawlers.  When you have many crawler jobs defined that have nothing
 * in common, it may be best to configure and run them separately, to facilitate
 * troubleshooting.  There is no set rules for this, experimenting with your
 * target sites will help you.
 * @author Pascal Essiembre
 */
public class HttpCollector extends Collector {

    /**
     * Creates a non-configured HTTP collector.
     */
    public HttpCollector() {
        super(new HttpCollectorConfig());
    }
	/**
	 * Creates and configure an HTTP Collector with the provided
	 * configuration.
	 * @param collectorConfig HTTP Collector configuration
	 */
    public HttpCollector(HttpCollectorConfig collectorConfig) {
        super(collectorConfig);
    }

    /**
     * Invokes the HTTP Collector from the command line.
     * @param args Invoke it once without any arguments to get a
     *    list of command-line options.
     */
	public static void main(String[] args) {
	    HttpCollector collector = new HttpCollector();
	    new CollectorCommandLauncher().launch(collector, args);
	}


    @Override
    public HttpCollectorConfig getCollectorConfig() {
        return (HttpCollectorConfig) super.getCollectorConfig();
    }

    @Override
    protected Crawler createCrawler(CrawlerConfig config) {
        return new HttpCrawler((HttpCrawlerConfig) config, this);
    }
}
