/**
 * 
 */
package com.norconex.collector.http.crawler;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.norconex.collector.http.HttpCollector;

/**
 * @author Pascal Essiembre
 *
 */
public class BasicFeaturesTest extends AbstractHttpTest {

    /**
     * Constructor.
     */
    public BasicFeaturesTest() {
    }

    
    @Test
    public void testFeatures() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler("/index.html");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        crawler.getCrawlerConfig().setMaxDepth(10);
        collector.start(false);
        
        File addDir = getCommitterAddDir(collector);
        Collection<File> files = FileUtils.listFiles(addDir, null, true);
        
        for (File file : files) {
            if (file.isDirectory() || file.getName().endsWith(".meta")) {
                continue;
            }
            System.out.println("=============================================");
            System.out.println("File: " + file.getAbsolutePath());
            System.out.println("=============================================");
            System.out.println(FileUtils.readFileToString(new File(
                    file.getAbsolutePath() + ".meta")));
            System.out.println("---------------------------------------------");
            System.out.println(FileUtils.readFileToString(file));
            System.out.flush();
        }
        
    }

}
