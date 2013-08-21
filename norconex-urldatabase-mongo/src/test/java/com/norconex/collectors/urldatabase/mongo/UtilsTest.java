package com.norconex.collectors.urldatabase.mongo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;

public class UtilsTest {

    @Test
    public void test_getDbNameOrGenerate_generate() throws Exception {

        HttpCrawlerConfig config = new HttpCrawlerConfig();
        String id = "my-crawl";
        config.setId(id);

        assertEquals(id, Utils.getDbNameOrGenerate("", config));
    }

    @Test
    public void test_getDbNameOrGenerate_generate_and_replace()
            throws Exception {

        HttpCrawlerConfig config = new HttpCrawlerConfig();
        String id = "my crawl";
        config.setId(id);

        // Whitespace should be replaced with '_'
        assertEquals("my_crawl", Utils.getDbNameOrGenerate("", config));
    }

    @Test
    public void test_getDbNameOrGenerate_invalid_name() throws Exception {
        HttpCrawlerConfig config = new HttpCrawlerConfig();
        try {
            // Tests some of the invalid characters
            Utils.getDbNameOrGenerate("invalid.name", config);
            Utils.getDbNameOrGenerate("invalid$name", config);
            Utils.getDbNameOrGenerate("invalid/name", config);
            Utils.getDbNameOrGenerate("invalid:name", config);
            Utils.getDbNameOrGenerate("invalid name", config);
            fail("Should throw an IllegalArgumentException because the name is invalid");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}
