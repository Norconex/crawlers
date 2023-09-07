package com.norconex.crawler.web.spark;

import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.WebCrawlSession;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;

import java.nio.file.Paths;

public class CrawlSessionTest {
    public static void main(String[] args) {
        runCrawlingFromFile();
    }

    private static void runCrawlingFromFile() {
        String configFilePath = "C:\\Workspace\\Norconex\\Projects\\spark\\crawler-v4-test.xml";
        CrawlSessionConfig csc = new CrawlSessionConfig(WebCrawlerConfig.class);
        csc.loadFromXML(new XML(Paths.get(configFilePath)));
        CrawlSession crawlSession2 = WebCrawlSession.createSession(csc);

        crawlSession2.start();
    }
}
