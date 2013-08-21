package com.norconex.collectors.urldatabase.mongo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.configuration.XMLConfiguration;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.ICrawlURLDatabase;
import com.norconex.collector.http.db.ICrawlURLDatabaseFactory;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;

public class MongoCrawlURLDatabaseFactory implements ICrawlURLDatabaseFactory,
        IXMLConfigurable {

    private static final long serialVersionUID = 2798011257427173733L;

    private int port = 27017;
    private String host = "localhost";
    private String dbName;

    @Override
    public ICrawlURLDatabase createCrawlURLDatabase(HttpCrawlerConfig config,
            boolean resume) {
        return new MongoCrawlURLDatabase(config, resume, port, host, dbName);
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        port = xml.getInt("port", port);
        host = xml.getString("host", host);
        dbName = xml.getString("dbname", dbName);
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        // TODO ???
    }
}
