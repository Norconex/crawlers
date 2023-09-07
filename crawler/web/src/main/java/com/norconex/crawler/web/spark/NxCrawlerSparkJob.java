package com.norconex.crawler.web.spark;

import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.WebCrawlSession;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Slf4j
public class NxCrawlerSparkJob {
    public static void main(String[] args) {
        try (JavaSparkContext sc = SparkUtil.createSparkConf("NxCrawlerSparkJob", args)) {
//            String filePath = "C:\\Workspace\\Norconex\\Projects\\spark\\crawler-v4-test.xml";
            String filePath = getFilePath(args);
            int numOfPartitions = 2;

            CrawlSessionConfig csc = loadConfig(filePath);

            for (CrawlerConfig cc : csc.getCrawlerConfigs()) {
                JavaRDD<String> rdd = sc.parallelize(cc.getStartReferences(), numOfPartitions);

                System.out.println("Num of partitions: " + rdd.getNumPartitions());

                String ccId = cc.getId();
                rdd.mapPartitionsWithIndex((index, iterator) -> startCrawler(index, iterator, filePath, ccId), false)
                        .collect();
            }

//            SparkUtil.awaitQuitCommand();
        }
    }

    private static String getFilePath(String[] args) {
        if (args.length == 0) {
            System.out.println("Please pass config file path and master url: \"<ConfigFilePath>\" <SparkMasterURL>");
            System.exit(1);
        }

        return args[0].replace("\"", "");
    }

    private static Iterator<String> startCrawler(Integer index, Iterator<String> iterator, String filePath, String crawlerId) {
        if (index != 0) {
            SparkUtil.waitForSeconds(2);
        }

        CrawlSessionConfig csc = loadConfig(filePath);
        List<String> startUrls = new ArrayList<>();

        while (iterator.hasNext()) {
            startUrls.add(iterator.next());
        }

        csc.setWorkDir(Path.of(csc.getWorkDir() + File.separator + index));
        CrawlerConfig cc = csc.getCrawlerConfigs().stream().filter(c -> c.getId().equals(crawlerId)).findFirst().get();
        cc.setStartReferences(startUrls);
        csc.setCrawlerConfigs(Collections.singletonList(cc));
        CrawlSession crawlSession = WebCrawlSession.createSession(csc);

        System.out.println("Crawler started with startUrls: " + startUrls + ", workDir=" + csc.getWorkDir());
        crawlSession.start();

        System.out.println("Completed...");

        return iterator;
    }

    private static CrawlSessionConfig loadConfig(String filePath) {
        CrawlSessionConfig csc = new CrawlSessionConfig(WebCrawlerConfig.class);
        csc.loadFromXML(new XML(Paths.get(filePath)));
        return csc;
    }
}
