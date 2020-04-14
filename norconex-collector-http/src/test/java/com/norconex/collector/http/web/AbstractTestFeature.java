/* Copyright 2019-2020 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.committer.core3.ICommitter;
import com.norconex.committer.core3.UpsertRequest;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.map.Properties;

/**
 * @author Pascal Essiembre
 */
public abstract class AbstractTestFeature implements IWebTest {

    private int runIndex = 0;

    @Override
    public int numberOfRun() {
        return 1;
    }
    @Override
    public final void initRunIndex(int runIndex) {
        this.runIndex = runIndex;
    }

    /**
     * 0-based index of current run.
     * @return index of current run
     */
    public final int getRunIndex() {
        return runIndex;
    }
    /**
     * 1-based count of current run.
     * @return count of current run
     */
    public final int getRunCount() {
        return runIndex + 1;
    }


    @Override
    public String getPath() {
        return getClass().getSimpleName();
    }

    @Override
    public void configureCollector(HttpCollectorConfig collectorConfig)
            throws Exception {
        doConfigureCollector(collectorConfig);
        if (!CollectionUtils.isEmpty(collectorConfig.getCrawlerConfigs())) {
            for (CrawlerConfig cfg : collectorConfig.getCrawlerConfigs()) {
                doConfigureCralwer((HttpCrawlerConfig) cfg);
            }
        }
    }
    protected void doConfigureCollector(HttpCollectorConfig collectorConfig)
            throws Exception {
    }
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        resp.setContentType("text/html");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.toString());
        PrintWriter out = resp.getWriter();
        printHtmlHeader(out);
        doHtmlService(req, resp, out);
        printHtmlFooter(out);
    }
    protected void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {
        doHtmlService(req, out);
    }
    protected void doHtmlService(
            HttpServletRequest req, PrintWriter out) throws Exception {
        doHtmlService(out);
    }
    protected void doHtmlService(PrintWriter out) throws Exception {
    }

    @Override
    public void startCollector(HttpCollector collector) throws Exception {
        collector.start();
    }

    @Override
    public void test(HttpCollector collector)
            throws Exception {
        doTest(collector);
        if (!CollectionUtils.isEmpty(collector.getCrawlers())) {
            for (Crawler crawler : collector.getCrawlers()) {
                doTestCrawler((HttpCrawler) crawler);
                doTestCommitter(((HttpCrawler) crawler)
                        .getCrawlerConfig().getCommitters().get(0));
            }
        }
    }
    protected void doTest(HttpCollector collector)
            throws Exception {
    }
    protected void doTestCrawler(HttpCrawler crawler)
            throws Exception {

    }
    protected void doTestCommitter(ICommitter committer)
            throws Exception {
        if (committer instanceof MemoryCommitter) {
            doTestMemoryCommitter((MemoryCommitter) committer);
        }
    }
    protected void doTestMemoryCommitter(
            MemoryCommitter committer) throws Exception {
    }

    protected void printHtmlHeader(PrintWriter out) {
        out.println("<html>"
                // Added date to test #544
                + "<head>"
                + "<meta name=\"article:modified_time\" "
                + "content=\"2018-11-28T16:06:51\">"
                + "</head>"
                + "<body style=\"font-family:Arial, "
                + "Helvetica, sans-serif;\">");
    }
    protected void printHtmlFooter(PrintWriter out) {
        out.println("</body></html>");
    }

    protected void assertListSize(String listName, List<?> list, int size) {
        Assertions.assertEquals(size, list.size(),
                "[Run #" + getRunCount() + "] Wrong "
                        + listName + " list size.");
    }
    protected void assertOneValue(Properties meta, String... fields) {
        for (String field : fields) {
            Assertions.assertEquals(
                    1, meta.getStrings(field).size(),
                "[Run #" + getRunCount() + "] "
                        + field + " does not contain strickly 1 value.");
        }
    }

    protected String content(UpsertRequest req) throws IOException {
        try (InputStream is = req.getContent()) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    protected boolean isFirstRun() {
        return isRunCount(1);
    }
    protected boolean isSecondRun() {
        return isRunCount(2);
    }
    protected boolean isThirdRun() {
        return isRunCount(3);
    }
    protected boolean isFourthRun() {
        return isRunCount(4);
    }
    protected boolean isFifthRun() {
        return isRunCount(5);
    }
    protected boolean isRunIndex(int runIndex) {
        return this.runIndex == runIndex;
    }
    protected boolean isRunCount(int runCount) {
        return getRunCount() == runCount;
    }

    protected final HttpCrawler getCrawler(HttpCollector collector) {
        if (CollectionUtils.isEmpty(collector.getCrawlers())) {
            return null;
        }
        return (HttpCrawler) collector.getCrawlers().get(0);
    }
    protected final HttpCrawlerConfig getCrawlerConfig(
            HttpCollector collector) {
        if (CollectionUtils.isEmpty(
                collector.getCollectorConfig().getCrawlerConfigs())) {
            return null;
        }
        return (HttpCrawlerConfig)
                collector.getCollectorConfig().getCrawlerConfigs().get(0);
    }
    protected final ICommitter getCommitter(HttpCollector collector) {
        HttpCrawlerConfig cfg = getCrawlerConfig(collector);
        if (cfg == null) {
            return null;
        }
        return cfg.getCommitters().get(0);
    }

    @Override
    public String toString() {
        return getPath();
    }
}
