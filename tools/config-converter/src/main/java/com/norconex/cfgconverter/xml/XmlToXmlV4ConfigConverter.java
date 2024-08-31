/* Copyright 2023 Norconex Inc.
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
package com.norconex.cfgconverter.xml;

import static com.norconex.cfgconverter.xml.Util.setClass;
import static com.norconex.cfgconverter.xml.Util.setElemValue;
import static com.norconex.commons.lang.xml.XpathUtil.attr;
import static org.apache.commons.lang3.StringUtils.replace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

import org.apache.commons.io.IOUtils;

import com.norconex.cfgconverter.ConfigConverter;
import com.norconex.commons.lang.xml.Xml;
import com.norconex.commons.lang.xml.XmlFormatter;
import com.norconex.commons.lang.xml.XmlFormatter.Builder.AttributeWrap;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Converter for V3 HTTP Collector XML or just Importer XML.
 */
@Slf4j
public class XmlToXmlV4ConfigConverter implements ConfigConverter {

    private static final String IMPORTER = "importer";
    private static final String TEMP_DIR = "tempDir";
    private static final String IGNORE_CASE = "ignoreCase";

    @Override
    public void convert(@NonNull Xml input, @NonNull Writer output) {
        convertAllClassAttributes(input);

        // "disabled" and "ignore" are no longer supported, we use self-closing
        // tag to indicate null, which equates disabled/ignored.
        // So if an item is disabled/ignored, we make it self-closed.
        // In either case, we remove the "disabled" and "ignore" attributes.
        convertAllDisabledElements(input);
        convertAllIgnoreElements(input);

        convertAllCaseSensitive(input);

        if ("httpcollector".equals(input.getName())) {
            convertSession(input);
        } else if (IMPORTER.equals(input.getName())) {
            convertImporter(input);
        }
        writeXml(input, output);
    }

    private void convertAllClassAttributes(Xml xml) {
        // update package name
        xml.forEach("//*[@class]", x -> {
            setClass(x, v -> v.replace("collector.http", "crawler.web"));
            setClass(x, v -> v.replace("collector.core", "crawler.core"));
            setClass(x, v -> v.replace("committer.core3", "committer.core"));
        });
    }
    private void convertAllDisabledElements(Xml xml) {
        xml.forEach("//*[@disabled]", x -> {
            if (x.isDisabled()) {
                x.clear();
            } else {
                x.removeAttribute("disabled");
            }
        });
    }
    private void convertAllIgnoreElements(Xml xml) {
        xml.forEach("//*[@ignore]", x -> {
            if (Boolean.TRUE.equals(x.getBoolean("@ignore"))) {
                x.clear();
            } else {
                x.removeAttribute("ignore");
            }
        });
    }

    // now case sensitive by default, so attribute is now "ignoreCase".
    private void convertAllCaseSensitive(Xml xml) {
        xml.forEach("//*[@caseSensitive]", x -> {
            x.setAttribute(IGNORE_CASE, !x.getBoolean("@caseSensitive"));
            x.removeAttribute("caseSensitive");
        });
    }

    private void convertSession(Xml sessionXml) {
        sessionXml.rename("crawlSession");
        sessionXml.ifXML("crawlerDefaults", this::convertCrawler);
        sessionXml.forEach("crawlers/crawler", this::convertCrawler);
    }

    private void convertCrawler(Xml crawlerXml) {
        crawlerXml.ifXML("startURLs", xml -> {
            xml.rename("start");
            xml.forEach("url", x -> x.rename("ref"));
            xml.forEach("urlsFile", x -> x.rename("refsFile"));
        });
        if (Boolean.TRUE.equals( crawlerXml.getBoolean("keepDownloads"))) {
            // as there are no importer generated yes, this handler will
            // be added as the first, as we expect.
            crawlerXml.computeElementIfAbsent(IMPORTER, null)
                .computeElementIfAbsent("preParseHandlers", null)
                .addXML("""
                    <handler class="com.norconex.importer.handler.tagger.impl.\
                    SaveDocumentTagger">
                      <saveDir>./downloads</saveDir>
                    </handler>""");
            crawlerXml.removeElement("keepDownloads");
        }
        crawlerXml.ifXML("fetchHttpHead",
                xml -> xml.rename("metadataFetchSupport"));
        crawlerXml.ifXML("fetchHttpGet",
                xml -> xml.rename("documentFetchSupport"));
        crawlerXml.ifXML("httpFetchers", xml -> {
            xml.rename("fetchers");
            xml.forEach("fetcher", f -> {
                setElemValue(f, "cookieSpec",
                        v -> replace(v, "ignoreCookies", "ignore"));
                f.ifXML("disableSNI", x -> x.rename("sniDisabled"));
                f.ifXML("disableIfModifiedSince",
                        x -> x.rename("ifModifiedSinceDisabled"));
            });
        });
        crawlerXml.forEach("referenceFilters/filter"
                + "|metadataFilters/filter|documentFilters/filter", f -> {
            setClass(f, v -> v.replaceFirst(
                    "\\bReferenceFilter\\b", "GenericReferenceFilter"));
            setClass(f, v -> v.replaceFirst(
                    "\\bMetadataFilter\\b", "GenericMetadataFilter"));
            if (f.getString("@class").contains("SegmentCountURLFilter")) {
                f.addElement("separator", f.getString("@separator"));
                f.removeAttribute("separator");
            }
        });
        crawlerXml.ifXML("sitemapResolver[contains("
                + "@class, 'GenericSitemapResolver')]", xml -> {
            xml.ifXML(TEMP_DIR, x -> LOG.warn(elemNoLongerSupported(
                    TEMP_DIR, "GenericSitemapResolver")));
            var slXml = new Xml("sitemapLocator");
            slXml.setAttribute("class", "com.norconex.crawler.web.sitemap"
                    + ".impl.GenericSitemapLocator");
            var paths = slXml.addElement("paths");
            xml.forEach("path",
                    x -> paths.addElement("path", x.getString(".")));
            xml.removeElement(TEMP_DIR);
            xml.forEach("path", Xml::remove);
            xml.insertAfter(slXml);
        });
        crawlerXml.ifXML("recrawlableResolver[contains("
                + "@class, 'GenericRecrawlableResolver')]", xml ->
            xml.forEach("minFrequency", minFreq -> {
                var matcher = new Xml("matcher");
                matcher.setAttribute("method", "regex");
                matcher.setAttribute(IGNORE_CASE,
                        minFreq.getBoolean(attr(IGNORE_CASE), false));
                minFreq.removeAttribute(IGNORE_CASE);
                matcher.setTextContent(minFreq.getTextContent());
                minFreq.removeTextContent();
                minFreq.addXML(matcher);
            })
        );


        //TODO the rest

        crawlerXml.ifXML(IMPORTER, this::convertImporter);

    }

    private void convertImporter(Xml importerXml) {
        //TODO
    }


    private void writeXml(Xml xml, Writer output) {
        try {
            IOUtils.write(XmlFormatter.builder()
                .attributeWrapping(AttributeWrap.AT_MAX_ALL)
                .maxLineLength(80)
                .build()
                .format(xml), output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String elemNoLongerSupported(String elemName, String className) {
        return ("Element \"%s\" of class %s is no longer supported, and has no "
                + "alternatives. It has been removed.").formatted(
                        elemName, className);
    }
}
