package com.norconex.collector.http;

import java.io.Serializable;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;

/**
 * HTTP Collector configuration.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class HttpCollectorConfig implements Cloneable, Serializable {

    public static final String DEFAULT_LOGS_DIR = "./logs";
    public static final String DEFAULT_PROGRESS_DIR = "./progress";
    
    private static final long serialVersionUID = -3350877963428801802L;
    private String id;
    private HttpCrawlerConfig[] crawlerConfigs;
    private String progressDir = DEFAULT_PROGRESS_DIR;
    private String logsDir = DEFAULT_LOGS_DIR;
    
    /**
     * Creates a new collector with the given unique id.  It is important
     * the id of the collector is unique amongst your collectors.  This
     * facilitates integration with different systems and facilitates
     * tracking.
     * @param id unique identifier
     */
	public HttpCollectorConfig(String id) {
        super();
        this.id = id;
    }

	public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public HttpCrawlerConfig[] getCrawlerConfigs() {
        return crawlerConfigs;
    }
    public void setCrawlerConfigs(HttpCrawlerConfig[] crawlerConfigs) {
        this.crawlerConfigs = crawlerConfigs;
    }

    public String getProgressDir() {
        return progressDir;
    }

    public void setProgressDir(String progressDir) {
        this.progressDir = progressDir;
    }

    public String getLogsDir() {
        return logsDir;
    }

    public void setLogsDir(String logsDir) {
        this.logsDir = logsDir;
    }
}
