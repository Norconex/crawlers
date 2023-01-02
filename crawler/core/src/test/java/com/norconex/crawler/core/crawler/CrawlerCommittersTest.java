/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.fs.impl.XMLFileCommitter;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecord;


/**
 */
class CrawlerCommittersTest {

    @TempDir
    Path folder;

    @Test
    void testMultipleCommitters() throws IOException {

        folder.toFile().setWritable(true, false);

        // create 3 Committers with the first two modifying content.
        XMLFileCommitter modifyTitle = new XMLFileCommitter() {
            @Override
            protected void doUpsert(UpsertRequest upsertRequest)
                    throws CommitterException {
                upsertRequest.getMetadata().set("title", "modified title");
                super.doUpsert(upsertRequest);

            }
        };
        modifyTitle.setIndent(4);

        XMLFileCommitter addKeyword = new XMLFileCommitter() {
            @Override
            protected void doUpsert(UpsertRequest upsertRequest)
                    throws CommitterException {
                upsertRequest.getMetadata().add("keyword", "added keyword");
                super.doUpsert(upsertRequest);
            }
        };
        addKeyword.setIndent(4);

        var xmlNoModif = new XMLFileCommitter();
        xmlNoModif.setIndent(4);

//        MockCrawler crawler = new MockCrawler("test", folder);
//        crawler.getCrawlerConfig().setCommitters(
//                modifyTitle, addKeyword, xmlNoModif);
//
//        CrawlerCommitterService committers = new CrawlerCommitterService(crawler);

        // The test document
        var meta = new Properties();
        meta.set("title", "original title");
        meta.set("keyword", "original keyword");

        var doc = new CrawlDoc(
                new CrawlDocRecord("ref"),
                new CachedStreamFactory()
                    .newInputStream(
                            IOUtils.toInputStream("original content", UTF_8)));
        doc.getMetadata().putAll(meta);

        // Perform the addition
        var ctx =
                CommitterContext.builder().setWorkDir(folder).build();
//        committers.init(ctx);
//        committers.upsert(doc);//req);
//        committers.close();

        // Test committed data
        try (var subFolders = Files.list(folder)) {
            Assertions.assertEquals(3, subFolders.count());
        }

        XML xml;

        // Committer 0
        xml = getXML(0);
        Assertions.assertEquals("modified title", getTitle(xml));
        Assertions.assertEquals(1, getKeywords(xml).size());
        Assertions.assertEquals("original keyword", getKeywords(xml).get(0));
        Assertions.assertEquals("original content", getContent(xml));

        // Committer 1
        xml = getXML(1);
        Assertions.assertEquals("original title", getTitle(xml));
        Assertions.assertEquals(2, getKeywords(xml).size());
        Assertions.assertEquals("original keyword", getKeywords(xml).get(0));
        Assertions.assertEquals("added keyword", getKeywords(xml).get(1));
        Assertions.assertEquals("original content", getContent(xml));

        // Committer 2
        xml = getXML(2);
        Assertions.assertEquals("original title", getTitle(xml));
        Assertions.assertEquals(1, getKeywords(xml).size());
        Assertions.assertEquals("original keyword", getKeywords(xml).get(0));
        Assertions.assertEquals("original content", getContent(xml));
    }

    private XML getXML(int idx) throws IOException {
        try (var subFolders = Files.list(folder)) {
            var xmlDir = subFolders.filter(
                    f -> f.getFileName().toString().startsWith(
                            Integer.toString(idx))).findFirst().get();
            try (var xmlFiles = Files.list(xmlDir)) {
                var xmlFile = xmlFiles.findFirst().get();
                return XML.of(xmlFile).create();
            }
        }
    }
    private String getTitle(XML xml) {
        return xml.getString("upsert/metadata/meta[@name='title']");
    }
    private List<String> getKeywords(XML xml) {
        return xml.getStringList("upsert/metadata/meta[@name='keyword']");
    }
    private String getContent(XML xml) {
        return xml.getString("upsert/content");
    }
}
