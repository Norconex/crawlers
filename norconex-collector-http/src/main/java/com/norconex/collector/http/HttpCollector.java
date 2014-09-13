/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.AbstractCollector;
import com.norconex.collector.core.AbstractCollectorConfig;
import com.norconex.collector.core.AbstractCollectorLauncher;
import com.norconex.collector.core.CollectorConfigLoader;
import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.ICollector;
import com.norconex.collector.core.ICollectorConfig;
import com.norconex.collector.core.crawler.ICrawler;
import com.norconex.collector.core.crawler.ICrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.jef4.job.IJob;
import com.norconex.jef4.job.group.AsyncJobGroup;
import com.norconex.jef4.log.FileLogManager;
import com.norconex.jef4.status.FileJobStatusStore;
import com.norconex.jef4.suite.JobSuite;
import com.norconex.jef4.suite.JobSuiteConfig;
 
/**
 * Main application class. In order to use it properly, you must first configure
 * it, either by providing a populated instance of {@link HttpCollectorConfig},
 * or by XML configuration, loaded using {@link CollectorConfigLoader}.
 * Instances of this class can hold several crawler, running at once.
 * This is convenient when there are configuration setting to be shared amongst
 * crawlers.  When you have many crawler jobs defined that have nothing
 * in common, it may be best to configure and run them separately, to facilitate
 * troubleshooting.  There is no set rules for this, experimenting with your
 * target sites will help you.
 * @author Pascal Essiembre
 */
@SuppressWarnings("nls")
public class HttpCollector extends AbstractCollector {

	private static final Logger LOG = LogManager.getLogger(HttpCollector.class);

	private JobSuite jobSuite;
    
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
	    new AbstractCollectorLauncher() {
	        @Override
	        protected ICollector createCollector(
	                ICollectorConfig config) {
	            return new HttpCollector((HttpCollectorConfig) config);
	        }
	        @Override
	        protected Class<? extends AbstractCollectorConfig> 
	                getCollectorConfigClass() {
	            return HttpCollectorConfig.class;
	        }
	    }.launch(args);
	}

    /**
     * Launched all crawlers defined in configuration.
     * @param resumeNonCompleted whether to resume where previous crawler
     *        aborted (if applicable) 
     */
    public void start(boolean resumeNonCompleted) {
        
        //TODO move this code to a config validator class?
        //TODO move this code to base class?
        if (StringUtils.isBlank(getCollectorConfig().getId())) {
            throw new CollectorException("HTTP Collector must be given "
                    + "a unique identifier (id).");
        }
        
        if (jobSuite != null) {
            throw new CollectorException(
                    "Collector is already running. Wait for it to complete "
                  + "before starting the same instance again, or stop "
                  + "the currently running instance first.");
        }
        jobSuite = createJobSuite();
        try {
            jobSuite.execute(resumeNonCompleted);
        } finally {
            jobSuite = null;
        }
    }

    /**
     * Stops a running instance of this HTTP Collector.
     */
    public void stop() {
        if (jobSuite == null) {
            throw new CollectorException(
                    "This collector cannot be stopped since it is NOT "
                  + "running.");
        }
        try {
            jobSuite.stop();
            //TODO wait for stop confirmation before setting to null?
            jobSuite = null;
        } catch (IOException e) {
            throw new CollectorException(
                    "Could not stop collector: " + getId(), e);
        }
    }
    
    @Override
    public HttpCollectorConfig getCollectorConfig() {
        return (HttpCollectorConfig) super.getCollectorConfig();
    }
    
    @Override
    public JobSuite createJobSuite() {
        ICrawler[] crawlers = getCrawlers();
        
        IJob rootJob = null;
        if (crawlers.length > 1) {
            rootJob = new AsyncJobGroup(
                    getId(), crawlers
            );
        } else if (crawlers.length == 1) {
            rootJob = crawlers[0];
        }
        
        JobSuiteConfig suiteConfig = new JobSuiteConfig();

        
        //TODO have a base workdir, which is used to figure out where to put
        // everything (log, progress), and make log and progress overwritable.

        HttpCollectorConfig collectorConfig = getCollectorConfig();
        suiteConfig.setLogManager(
                new FileLogManager(collectorConfig.getLogsDir()));
        suiteConfig.setJobStatusStore(
                new FileJobStatusStore(collectorConfig.getProgressDir()));
        suiteConfig.setWorkdir(collectorConfig.getProgressDir()); 
        JobSuite suite = new JobSuite(rootJob, suiteConfig);
        LOG.info("Suite of " + crawlers.length + " HTTP crawler jobs created.");
        return suite;
    }

    @Override
    protected ICrawler createCrawler(ICrawlerConfig config) {
        return new HttpCrawler((HttpCrawlerConfig) config);
    }
}
