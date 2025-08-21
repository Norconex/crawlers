/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.importer.handler.splitter.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.Doc;

class DomSplitterTest {


    @Test
    void testRefieldAndAttrFieldVariants2() throws IOException{
        String htmlb = """
            <html>
            	<body>
            		<div id="job-table">
            			<div class="searchResultsShell">
            				<table id="searchresults" class="searchResults full table table-striped table-hover" cellpadding="0" cellspacing="0">
            					<tbody>
            						<tr class="data-row">
            							<td class="colTitle" headers="hdrTitle">
            								<span class="jobTitle hidden-phone">
            									<a href="/job/Toronto-Associate-ProfessorProfessor-Lawson-Chair-in-Climate-Policy-Innovation-ON/594178317/" class="jobTitle-link">Associate Professor/Professor - Lawson Chair in Climate Policy Innovation</a>
            								</span>
            	
            							</td>
            						</tr>
            						<tr class="data-row">
            							<td class="colTitle" headers="hdrTitle">
            								<span class="jobTitle hidden-phone">
            									<a href="/job/Toronto-Associate-Professor-Evolutionary-Genetics-and-Hybridization-ON/594177517/" class="jobTitle-link">Associate Professor - Evolutionary Genetics and Hybridization</a>
            								</span>
                        
            							</td>
            						</tr>
            						<tr class="data-row">
            							<td class="colTitle" headers="hdrTitle">
            								<span class="jobTitle hidden-phone">
            									<a href="/job/Toronto-Assistant-Professor-Strategic-Management-ON/594180317/" class="jobTitle-link">Assistant Professor - Strategic Management</a>
            								</span>
            							</td>
            						</tr>
            					</tbody>
            				</table>
            			</div>
            		</div>
            	</body>
            </html>
            """;

        var splittera = new DomSplitter();
        splittera.getConfiguration().setSelector("#job-table tbody tr.data-row");
        splittera.getConfiguration().setReferenceField("a.jobTitle-link");
        splittera.getConfiguration().setAttributeField("href");

        var docsa = split(htmlb, splittera);
        Assertions.assertEquals(3, docsa.size());

        Assertions.assertTrue(docsa.get(0).getReference().endsWith("/job/Toronto-Associate-ProfessorProfessor-Lawson-Chair-in-Climate-Policy-Innovation-ON/594178317/"));
        Assertions.assertTrue(docsa.get(1).getReference().endsWith("/job/Toronto-Associate-Professor-Evolutionary-Genetics-and-Hybridization-ON/594177517/"));
        Assertions.assertTrue(docsa.get(2).getReference().endsWith("/job/Toronto-Assistant-Professor-Strategic-Management-ON/594180317/"));

//        Assertions.assertEquals(3, docs1.size());
//        Assertions.assertEquals("https://example.com/alice",
//            docs1.get(0).getReference());
//        Assertions.assertEquals("https://example.com/bob",
//            docs1.get(1).getReference());
//        Assertions.assertEquals("https://example.com/dalton",
//            docs1.get(2).getReference());
    }

    @Test
    void testRefFieldAndAttrFieldVariants() throws IOException {

        // --- Case 0: ?? ---
        String htmla = """
        <html>
          <body>
            <div class="person" id="top">
                <div class="person" id="p1"><a class="link" href="https://example.com/alice">Alice</a></div>
                <div class="person" id="p2"><a class="link" href="https://example.com/bob">Bob</a></div>
                <div class="person" id="p3"><a class="link" href="https://example.com/dalton">Dalton</a></div>
            </div>
            <div class="person" id="other">
                <div class="person" id="blah1"><a class="link" href="https://example.com/alice">Alice</a></div>
                <div class="person" id="blah2"><a class="link" href="https://example.com/bob">Bob</a></div>
                <div class="person" id="blah3"><a class="link" href="https://example.com/dalton">Dalton</a></div>
            </div>
          </body>
        </html>
        """;

        var splittera = new DomSplitter();
        splittera.getConfiguration().setSelector("div.person#top .person");

        var docsa = split(htmla, splittera);
        Assertions.assertEquals(3, docsa.size());
        Assertions.assertTrue(docsa.get(0).getReference().endsWith("p1"));
        Assertions.assertTrue(docsa.get(1).getReference().endsWith("p2"));
        Assertions.assertTrue(docsa.get(2).getReference().endsWith("p3"));

        // --- Case 0: ?? ---
        String html0 = """
        <html>
          <body>
            <div class="person" id="p1"><a class="link" href="https://example.com/alice">Alice</a></div>
            <div class="person" id="p2"><a class="link" href="https://example.com/bob">Bob</a></div>
            <div class="person" id="p3"><a class="link" href="https://example.com/dalton">Dalton</a></div>
          </body>
        </html>
        """;

        var splitter0 = new DomSplitter();
        splitter0.getConfiguration().setSelector("div.person");

        var docs0 = split(html0, splitter0);
        Assertions.assertEquals(3, docs0.size());
        Assertions.assertTrue(docs0.get(0).getReference().endsWith("p1"));
        Assertions.assertTrue(docs0.get(1).getReference().endsWith("p2"));
        Assertions.assertTrue(docs0.get(2).getReference().endsWith("p3"));

        // --- Case 1: refField= "a.link", attrField="href" ---
        String html = """
        <html>
          <body>
            <div class="person" id="p1"><a class="link" href="https://example.com/alice">Alice</a></div>
            <div class="person" id="p2"><a class="link" href="https://example.com/bob">Bob</a></div>
            <div class="person" id="p3"><a class="link" href="https://example.com/dalton">Dalton</a></div>
          </body>
        </html>
        """;

        var splitter1 = new DomSplitter();
        splitter1.getConfiguration().setSelector("div.person");
        splitter1.getConfiguration().setReferenceField("a.link");
        splitter1.getConfiguration().setAttributeField("href");

        var docs1 = split(html, splitter1);
        Assertions.assertEquals(3, docs1.size());
        Assertions.assertEquals("https://example.com/alice",
                docs1.get(0).getReference());
        Assertions.assertEquals("https://example.com/bob",
                docs1.get(1).getReference());
        Assertions.assertEquals("https://example.com/dalton",
                docs1.get(2).getReference());

        // --- Case 2: refField = "span.link", attrField = null (uses .text()) ---
        String html2 = """
        <html>
          <body>
            <div class="person" id="p1"><span class="link">https://example.com/alice</span></div>
            <div class="person" id="p2"><span class="link">https://example.com/bob</span></div>
            <div class="person" id="p3"><span class="link">https://example.com/dalton</span></div>
          </body>
        </html>
        """;

        var splitter2 = new DomSplitter();
        splitter2.getConfiguration().setSelector("div.person");
        splitter2.getConfiguration().setReferenceField("span.link");
        // no setReferenceAttribute()

        var docs2 = split(html2, splitter2);
        Assertions.assertEquals(3, docs2.size());
        Assertions.assertEquals("https://example.com/alice",
                docs2.get(0).getReference());
        Assertions.assertEquals("https://example.com/bob",
                docs2.get(1).getReference());
        Assertions.assertEquals("https://example.com/dalton",
                docs2.get(2).getReference());

        // --- Case 3: refField = "span.name", attrField = "missing" (fallback to parent!cssSelector) ---
        String html3 = """
        <html>
          <body>
            <div class="person" id="p1"><span class="name">Alice</span></div>
            <div class="person" id="p2"><span class="name">Bob</span></div>
            <div class="person" id="p3"><span class="name">Dalton</span></div>
          </body>
        </html>
        """;

        var splitter3 = new DomSplitter();
        splitter3.getConfiguration().setSelector("div.person");
        splitter3.getConfiguration().setReferenceField("span.name");
        splitter3.getConfiguration().setAttributeField("missing"); // attribute doesn't exist

        var docs3 = split(html3, splitter3);
        Assertions.assertEquals(3, docs3.size());
        Assertions.assertTrue(docs3.get(0).getReference().endsWith("p1"));
        Assertions.assertTrue(docs3.get(1).getReference().endsWith("p2"));
        Assertions.assertTrue(docs3.get(2).getReference().endsWith("p3"));

        //        Assertions.assertTrue(docs3.get(0).getReference().startsWith("n/a!div.person:nth-child"));
        //        Assertions.assertTrue(docs3.get(1).getReference().startsWith("n/a!div.person:nth-child"));
        //        Assertions.assertTrue(docs3.get(2).getReference().startsWith("n/a!div.person:nth-child"));
    }

    @Test
    void testHtmlDOMSplit() throws IOException {
        var html = ResourceLoader.getHtmlString(getClass());
        var splitter = new DomSplitter();
        splitter.getConfiguration().setSelector("div.person");
        var docs = split(html, splitter);

        Assertions.assertEquals(3, docs.size());
        var content = TestUtil.getContentAsString(docs.get(2));
        Assertions.assertTrue(content.contains("Dalton"));
    }

    @Test
    void testXmlDOMSplit() throws IOException {

        var xml = ResourceLoader.getXmlString(getClass());

        var splitter = new DomSplitter();
        splitter.getConfiguration().setSelector("person");
        var docs = split(xml, splitter);

        Assertions.assertEquals(3, docs.size());

        var content = TestUtil.getContentAsString(docs.get(2));
        Assertions.assertTrue(content.contains("Dalton"));
    }

    @Test
    void testSplitInField() throws IOException {
        var splitter = new DomSplitter();
        splitter.getConfiguration()
                .setSelector("person")
                .setFieldMatcher(TextMatcher.basic("splitme"));
        var xml = ResourceLoader.getXmlString(getClass());
        var metadata = new Properties();
        metadata.add("splitme", xml);

        var is = IOUtils.toInputStream("blah", StandardCharsets.UTF_8);
        var docCtx = TestUtil.newHandlerContext("n/a", is, metadata);
        splitter.handle(docCtx);

        var docs = docCtx.childDocs();

        Assertions.assertEquals(3, docs.size());

        var content = TestUtil.getContentAsString(docs.get(2));
        Assertions.assertTrue(content.contains("Dalton"));
    }

    private List<Doc> split(String text, DomSplitter splitter)
            throws IOException {
        var metadata = new Properties();
        var is = IOUtils.toInputStream(text, StandardCharsets.UTF_8);
        var docCtx = TestUtil.newHandlerContext("n/a", is, metadata);
        splitter.handle(docCtx);
        return docCtx.childDocs();
    }

    @Test
    void testWriteRead() {
        var splitter = new DomSplitter();
        splitter.getConfiguration().setSelector("blah");
        splitter.getConfiguration().setContentTypeMatcher(
                TextMatcher.basic("value").partial().ignoreCase());
        BeanMapper.DEFAULT.assertWriteRead(splitter);
    }
}
