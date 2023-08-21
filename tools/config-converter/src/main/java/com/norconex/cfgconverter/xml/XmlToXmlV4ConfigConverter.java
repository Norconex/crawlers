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
import static org.apache.commons.lang3.StringUtils.replace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

import org.apache.commons.io.IOUtils;

import com.norconex.cfgconverter.ConfigConverter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLFormatter;
import com.norconex.commons.lang.xml.XMLFormatter.Builder.AttributeWrap;

import lombok.NonNull;

/**
 * Converter for V3 HTTP Collector XML or just Importer XML.
 */
public class XmlToXmlV4ConfigConverter implements ConfigConverter {

    @Override
    public void convert(@NonNull XML input, @NonNull Writer output) {
        convertAllClassAttributes(input);
        convertDisabledElements(input);
        if ("httpcollector".equals(input.getName())) {
            convertSession(input);
        } else if ("importer".equals(input.getName())) {
            convertImporter(input);
        }
        writeXml(input, output);
    }

    private void convertAllClassAttributes(XML xml) {
        // update package name
        xml.forEach("//*[@class]", x -> {
            setClass(x, v -> v.replace("collector.http", "crawler.web"));
            setClass(x, v -> v.replace("collector.core", "crawler.core"));
            setClass(x, v -> v.replace("committer.core3", "committer.core"));
        });
    }
    private void convertDisabledElements(XML xml) {
        // "disabled" no longer supported, we use self-closing tag to
        // indicate null, which equates disabled.
        // So if an item is disabled, we make it self-closed.
        // In either case, we remove the "disabled" attribute
        xml.forEach("//*[@disabled]", x -> {
            if (x.isDisabled()) {
                x.clear();
            } else {
                x.removeAttribute("disabled");
            }
        });
    }

    private void convertSession(XML sessionXml) {
        sessionXml.rename("crawlSession");
        sessionXml.forEach("eventListeners/listener", xml ->
            setClass(xml, v -> v.replace("collector.http", "crawler.web"))
        );
        sessionXml.ifXML("crawlerDefaults", this::convertCrawler);
        sessionXml.forEach("crawlers/crawler", this::convertCrawler);
    }

    private void convertCrawler(XML crawlerXml) {
        crawlerXml.ifXML("startURLs", xml -> {
            xml.rename("start");
            xml.forEach("url", x -> x.rename("ref"));
            xml.forEach("urlsFile", x -> x.rename("refsFile"));
        });
        if (Boolean.TRUE.equals( crawlerXml.getBoolean("keepDownloads"))) {
            // as there are no importer generated yes, this handler will
            // be added as the first, as we expect.
            crawlerXml.computeElementIfAbsent("importer", null)
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
        crawlerXml.forEach("referenceFilters/filter", f -> {
            setClass(f, v -> v.replaceFirst(
                    "\\bReferenceFilter\\b", "GenericReferenceFilter"));
            if (f.getString("@class").contains("SegmentCountURLFilter")) {
                f.addElement("separator", f.getString("@separator"));
                f.removeAttribute("separator");
            }
        });


        //TODO the rest

    }

    private void convertImporter(XML importerXml) {

    }


    private void writeXml(XML xml, Writer output) {
        try {
            IOUtils.write(XMLFormatter.builder()
                .attributeWrapping(AttributeWrap.AT_MAX_ALL)
                .maxLineLength(80)
                .build()
                .format(xml), output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
