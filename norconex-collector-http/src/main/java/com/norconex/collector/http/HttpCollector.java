package com.norconex.collector.http;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.jef.AsyncJobGroup;
import com.norconex.jef.IJob;
import com.norconex.jef.JobRunner;
import com.norconex.jef.log.FileLogManager;
import com.norconex.jef.progress.JobProgressPropertiesFileSerializer;
import com.norconex.jef.suite.FileStopRequestHandler;
import com.norconex.jef.suite.IJobSuiteFactory;
import com.norconex.jef.suite.JobSuite;

@SuppressWarnings("nls")
public class HttpCollector implements IJobSuiteFactory {

	private static final Logger LOG = LogManager.getLogger(HttpCollector.class);
	
    private File configurationFile;
    private File variablesFile;
    private HttpCrawler[] crawlers;
    private HttpCollectorConfig connectorConfig;
    
    public HttpCollector() {
        super();
    }
	public HttpCollector(File configFile, File variablesFile) {
	    this.configurationFile = configFile;
	    this.variablesFile = variablesFile;
	}
    public HttpCollector(HttpCollectorConfig connectorConfig) {
        this.connectorConfig = connectorConfig;
    }
	public File getConfigurationFile() {
        return configurationFile;
    }
    public void setConfigurationFile(File configurationFile) {
        this.configurationFile = configurationFile;
    }
    public File getVariablesFile() {
        return variablesFile;
    }
    public void setVariablesFile(File variablesFile) {
        this.variablesFile = variablesFile;
    }
    public HttpCrawler[] getCrawlers() {
        return crawlers;
    }
    public void setCrawlers(HttpCrawler[] crawlers) {
        this.crawlers = crawlers;
    }
    
    /**
	 * @param args
	 */
	public static void main(String[] args) {
	    if (args.length != 2 && args.length != 3) {
	        usageError();
	    }
	    String command = args[0];
        File configFile = new File(args[1]);
        File configVariables = null;
        if (args.length > 2) {
            configVariables = new File(args[2]);
        }
        try {
            HttpCollector conn = new HttpCollector(configFile, configVariables);
    	    if ("start".equalsIgnoreCase(command)) {
    	        conn.crawl(false);
    	    } else if ("resume".equalsIgnoreCase(command)) {
                conn.crawl(true);
    	    } else if ("stop".equalsIgnoreCase(command)) {
    	        conn.stop();
    	    } else {
    	        usageError();
    	    }
        } catch (Exception e) {
        	File errorFile = new File(
        			"./error-" + System.currentTimeMillis() + ".log");
        	System.err.println("\n\nAn ERROR occured:\n\n"
                  + e.getLocalizedMessage());
        	System.err.println("\n\nDetails of the error has been stored at: "
        			+ errorFile + "\n\n");
        	try {
        		PrintWriter w = new PrintWriter(errorFile);
				e.printStackTrace(w);
				w.flush();
				w.close();
			} catch (FileNotFoundException e1) {
				throw new RuntimeException("Cannot write error file.", e);
			}
        }
	}

    public void crawl(boolean resumeNonCompleted) {
        JobSuite suite = createJobSuite();
        JobRunner jobRunner = new JobRunner();
        jobRunner.runSuite(suite, resumeNonCompleted);
    }

    public void stop() {
        JobSuite suite = createJobSuite();
        ((FileStopRequestHandler) 
                suite.getStopRequestHandler()).fireStopRequest();
    }
    
    @Override
    public JobSuite createJobSuite() {
        if (connectorConfig == null) {
            try {
                connectorConfig = HttpCollectorConfigLoader.loadConnectorConfig(
                        getConfigurationFile(), getVariablesFile());
            } catch (Exception e) {
                throw new HttpCollectorException(e);
            }
        }
        if (connectorConfig == null) {
        	throw new HttpCollectorException(
        			"Configuration file does not exists: "
        			+ getConfigurationFile());
        }
        HttpCrawlerConfig[] configs = connectorConfig.getCrawlerConfigs();
        crawlers = new HttpCrawler[configs.length];
        for (int i = 0; i < configs.length; i++) {
            HttpCrawlerConfig crawlerConfig = configs[i];
            crawlers[i] = new HttpCrawler(crawlerConfig);
        }

        IJob rootJob = null;
        if (crawlers.length > 1) {
            rootJob = new AsyncJobGroup(
                    connectorConfig.getId(), crawlers
            );
        } else if (crawlers.length == 1) {
            rootJob = crawlers[0];
        }
        
        JobSuite suite = new JobSuite(
                rootJob, 
                new JobProgressPropertiesFileSerializer(
                        connectorConfig.getProgressDir()),
                new FileLogManager(connectorConfig.getLogsDir()),
                new FileStopRequestHandler(connectorConfig.getId(), 
                        connectorConfig.getProgressDir()));
        LOG.info("Suite of " + crawlers.length + " HTTP crawler jobs created.");
        return suite;
    }
	

	private static void usageError() {
        System.err.println(
                "Usage: <connector> start|stop configFile [variables]");
        System.err.println("Where:");
        System.err.println(
                "  start|stop -> Whether to start or stop the connector.");
        System.err.println("  configFile -> Connector configuration file.");
        System.err.println("  variables  -> Optional variables file.\n");
        System.exit(-1);
	}
	
	
	/*
	 * 


[PROCESS_URL]
  * Get URL
  * Check 
   * Check against database of already crawled URLs 


	 * 
	 */
}
