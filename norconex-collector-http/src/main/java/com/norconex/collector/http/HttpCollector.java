package com.norconex.collector.http;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.jef.AsyncJobGroup;
import com.norconex.jef.IJob;
import com.norconex.jef.JobRunner;
import com.norconex.jef.log.FileLogManager;
import com.norconex.jef.progress.JobProgressPropertiesFileSerializer;
import com.norconex.jef.suite.FileStopRequestHandler;
import com.norconex.jef.suite.IJobSuiteFactory;
import com.norconex.jef.suite.JobSuite;

/**
 * Main application class. In order to use it properly, you must first configure
 * it, either by providing a populated instance of {@link HttpCollectorConfig},
 * or by XML configuration, loaded using {@link HttpCollectorConfigLoader}.
 * Instances of this class can hold several crawler, running at once.
 * This is convenient when there are configuration setting to be shared amonsht
 * crawlers.  When you have many crawler jobs defined that have nothing
 * in common, it may be best to configure and run them separately, to facilitate
 * troubleshooting.  There is no fair rule for this, experienting with your
 * target sites will help you.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
@SuppressWarnings("nls")
public class HttpCollector implements IJobSuiteFactory {

	private static final Logger LOG = LogManager.getLogger(HttpCollector.class);
	
    private File configurationFile;
    private File variablesFile;
    private HttpCrawler[] crawlers;
    private HttpCollectorConfig collectorConfig;
    
    /**
     * Creates a non-configured HTTP collector.
     */
    public HttpCollector() {
        super();
    }
    /**
     * Creates an HTTP Collector configured using the provided
     * configuration fine and variable files.  Sample configuration files
     * and documentation on configuration options and the differences 
     * between a variables file and configuration are found on 
     * the HTTP Collector web site.
     * @param configFile a configuration file
     * @param variablesFile a variables file
     */
	public HttpCollector(File configFile, File variablesFile) {
	    this.configurationFile = configFile;
	    this.variablesFile = variablesFile;
	}
	/**
	 * Creates and configure an HTTP Collector with the provided
	 * configuration.
	 * @param collectorConfig HTTP Collector configuration
	 */
    public HttpCollector(HttpCollectorConfig collectorConfig) {
        this.collectorConfig = collectorConfig;
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
     * Invokes the HTTP Collector from the command line.  
     * @param args Invoke it once without any arguments to get a 
     *    list of command-line options.
     */
	public static void main(String[] args) {
	    CommandLine cmd = parseCommandLineArguments(args);
        String action = cmd.getOptionValue("action");
        File configFile = new File(cmd.getOptionValue("config"));
        File varFile = null;
        if (cmd.hasOption("variables")) {
            varFile = new File(cmd.getOptionValue("variables"));
        }
	    
        try {
            HttpCollector conn = new HttpCollector(configFile, varFile);
    	    if ("start".equalsIgnoreCase(action)) {
    	        conn.crawl(false);
    	    } else if ("resume".equalsIgnoreCase(action)) {
                conn.crawl(true);
    	    } else if ("stop".equalsIgnoreCase(action)) {
    	        conn.stop();
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

    /**
     * Launched all crawlers defined in configuration.
     * @param resumeNonCompleted whether to resume where previous crawler
     *        aborted (if applicable) 
     */
    public void crawl(boolean resumeNonCompleted) {
        JobSuite suite = createJobSuite();
        JobRunner jobRunner = new JobRunner();
        jobRunner.runSuite(suite, resumeNonCompleted);
    }

    /**
     * Stops a running instance of this HTTP Collector.
     */
    public void stop() {
        JobSuite suite = createJobSuite();
        ((FileStopRequestHandler) 
                suite.getStopRequestHandler()).fireStopRequest();
    }
    
    
    @Override
    public JobSuite createJobSuite() {
        if (collectorConfig == null) {
            try {
                collectorConfig = HttpCollectorConfigLoader.loadCollectorConfig(
                        getConfigurationFile(), getVariablesFile());
            } catch (Exception e) {
                throw new HttpCollectorException(e);
            }
        }
        if (collectorConfig == null) {
        	throw new HttpCollectorException(
        			"Configuration file does not exists: "
        			+ getConfigurationFile());
        }
        HttpCrawlerConfig[] configs = collectorConfig.getCrawlerConfigs();
        crawlers = new HttpCrawler[configs.length];
        for (int i = 0; i < configs.length; i++) {
            HttpCrawlerConfig crawlerConfig = configs[i];
            crawlers[i] = new HttpCrawler(crawlerConfig);
        }

        IJob rootJob = null;
        if (crawlers.length > 1) {
            rootJob = new AsyncJobGroup(
                    collectorConfig.getId(), crawlers
            );
        } else if (crawlers.length == 1) {
            rootJob = crawlers[0];
        }
        
        JobSuite suite = new JobSuite(
                rootJob, 
                new JobProgressPropertiesFileSerializer(
                        collectorConfig.getProgressDir()),
                new FileLogManager(collectorConfig.getLogsDir()),
                new FileStopRequestHandler(collectorConfig.getId(), 
                        collectorConfig.getProgressDir()));
        LOG.info("Suite of " + crawlers.length + " HTTP crawler jobs created.");
        return suite;
    }

    private static CommandLine parseCommandLineArguments(String[] args) {
        Options options = new Options();
        options.addOption("c", "config", true, 
                "Required: HTTP Collector configuration file.");
        options.addOption("v", "variables", true, 
                "Optional: variable file.");
        options.addOption("a", "action", true, 
                "Required: one of start|resume|stop");
        
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse( options, args);
            if(!cmd.hasOption("config") 
                    || !cmd.hasOption("action")
                    || EqualsUtil.equalsNone(cmd.getOptionValue("action"),
                            "start", "resume", "stop")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "collector-http[.bat|.sh]", options );
                System.exit(-1);
            }
        } catch (ParseException e1) {
            e1.printStackTrace();
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "collector-http[.bat|.sh]", options );
            System.exit(-1);
        }
        return cmd;
    }
}
