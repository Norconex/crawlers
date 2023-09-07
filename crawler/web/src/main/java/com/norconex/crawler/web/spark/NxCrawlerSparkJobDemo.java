package com.norconex.crawler.web.spark;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.fs.impl.XMLFileCommitter;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.WebCrawlSession;
import com.norconex.crawler.web.crawler.URLCrawlScopeStrategy;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;


@Slf4j
public class NxCrawlerSparkJobDemo {
    private static String WORK_DIR = "C:\\Workspace\\Norconex\\Projects\\spark\\workDir";

    public static void main(String[] args) {
        try (JavaSparkContext sc = new JavaSparkContext(SparkUtil.createSparkConf("NxCrawlerSparkJobDemo"))) {
            String filePath = "C:\\Workspace\\Norconex\\Projects\\spark\\startUrls.txt";

            JavaRDD<String> rdd = sc.textFile(filePath);

            System.out.println("Num of partitions: " + rdd.getNumPartitions());
            rdd.filter(url -> url.trim().length() > 0)
                    .mapPartitionsWithIndex(NxCrawlerSparkJobDemo::startCrawler, true)
                    .collect();

            while (true) {
                System.out.print("Enter 'quit' to exit program: ");
                String command = new Scanner(System.in).next();
                if (command.equalsIgnoreCase("quit")) {
                    System.exit(1);
                }
            }
        }
    }

    static Iterator<String> startCrawler(Integer index, Iterator<String> iterator) {
        List<String> startUrls = new ArrayList<>();

        while (iterator.hasNext()) {
            startUrls.add(iterator.next());
        }


        String crawlerId = String.valueOf(index);
        String workDir = WORK_DIR + File.separator + index;
        System.out.println("Crawler started with startUrls: " + startUrls + ", workDir=" + workDir);

        CrawlSessionConfig myConfig = new CrawlSessionConfig();

        myConfig.setId(crawlerId);
        myConfig.setWorkDir(Paths.get(workDir));

        List<CrawlerConfig> crawlerConfigs = new ArrayList<>();

        var cc = new WebCrawlerConfig();
        cc.setId(crawlerId);
        cc.setStartReferences(startUrls);
        cc.setMaxDepth(0);
        cc.setMaxDocuments(1);
        cc.setStayOnSitemap(false);

        List<Committer> committers = new ArrayList<>();
        XMLFileCommitter xmlFileCommitter = new XMLFileCommitter();
        xmlFileCommitter.setIndent(4);
        committers.add(xmlFileCommitter);

        cc.setCommitters(committers);

        var s = new URLCrawlScopeStrategy();
        s.setStayOnDomain(true);
        cc.setUrlCrawlScopeStrategy(s);

        crawlerConfigs.add(cc);
        myConfig.setCrawlerConfigs(crawlerConfigs);

        CrawlSession crawlSession = WebCrawlSession.createSession(myConfig);
        crawlSession.start();

//        if (crawlSession.isRunning()) {
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }

        System.out.println("Completed...");

        return iterator;
    }

}
