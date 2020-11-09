/* Copyright 2015-2020 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import java.io.ByteArrayInputStream;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Assert;
import org.junit.Test;

import com.norconex.collector.core.crawler.event.CrawlerEventManager;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedStreamFactory;

/**
 * @author Pascal Essiembre
 * @since 2.2.0
 */
public class HttpImporterPipelineTest {

    @Test
    public void testCanonicalStageSameReferenceContent() {
        String reference = "http://www.example.com/file.pdf";
        String contentValid = "<html><head><title>Test</title>\n"
                + "<link rel=\"canonical\"\n href=\"\n" + reference +  "\" />\n"
                + "</head><body>Nothing of interest in body</body></html>";
        HttpDocument doc = new HttpDocument(reference,
                new CachedStreamFactory(1000, 1000).newInputStream(
                        new ByteArrayInputStream(contentValid.getBytes())));
        HttpImporterPipelineContext ctx = new HttpImporterPipelineContext(
                new HttpCrawler(new HttpCrawlerConfig()),
                null, new HttpCrawlData(reference, 0), null, doc);
        Assert.assertTrue(HttpImporterPipelineUtil.resolveCanonical(
                ctx, false));
    }

    @Test
    public void testCanonicalStageSameReferenceHeader() {
        String reference = "http://www.example.com/file.pdf";
        HttpDocument doc = new HttpDocument(reference,
                new CachedStreamFactory(1, 1).newInputStream());
        doc.getMetadata().setString(
                "Link", "<" + reference + "> rel=\"canonical\"");
        HttpImporterPipelineContext ctx = new HttpImporterPipelineContext(
                new HttpCrawler(new HttpCrawlerConfig()),
                null, new HttpCrawlData(reference, 0), null, doc);
        Assert.assertTrue(HttpImporterPipelineUtil.resolveCanonical(ctx, true));
    }

    @Test
    public void testKeepMaxDepthLinks() throws IllegalAccessException {
        String reference = "http://www.example.com/file.html";
        String content = "<html><head><title>Test</title>\n"
                + "</head><body><a href=\"link.html\">A link</a></body></html>";

        HttpDocument doc = new HttpDocument(reference,
                new CachedStreamFactory(1000, 1000).newInputStream(
                        new ByteArrayInputStream(content.getBytes())));
        doc.setContentType(ContentType.HTML);
        HttpImporterPipelineContext ctx = new HttpImporterPipelineContext(
                new HttpCrawler(new HttpCrawlerConfig()),
                null, new HttpCrawlData(reference, 2), null, doc);


        ctx.getConfig().setMaxDepth(2);
        LinkExtractorStage stage = new LinkExtractorStage();



        FieldUtils.writeField(ctx.getCrawler(), "crawlerEventManager",
                new CrawlerEventManager(
                        ctx.getCrawler(),
                        ctx.getConfig().getCrawlerListeners()),
                true);


        // By default do not extract urls on max depth
        stage.execute(ctx);
        Assert.assertEquals(0, doc.getMetadata().getStrings(
                HttpMetadata.COLLECTOR_REFERENCED_URLS).size());

        // Here 1 URL shouled be extracted even if max depth is reached.
        ctx.getConfig().setKeepMaxDepthLinks(true);
        stage.execute(ctx);
        Assert.assertEquals(1, doc.getMetadata().getStrings(
                HttpMetadata.COLLECTOR_REFERENCED_URLS).size());
    }
}
