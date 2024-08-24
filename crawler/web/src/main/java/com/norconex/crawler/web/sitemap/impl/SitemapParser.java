/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.sitemap.impl;

import static org.apache.commons.lang3.StringUtils.substringBeforeLast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;

import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLException;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.sitemap.SitemapRecord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//TODO move all processing for a single sitemap here?

@RequiredArgsConstructor
@Slf4j
class SitemapParser {

    private final boolean lenient;
    private final MutableBoolean stopping;

    List<SitemapRecord> parse(
            CrawlDoc sitemapDoc, Consumer<WebCrawlDocContext> urlConsumer) {

        var location = sitemapDoc.getReference();
        List<SitemapRecord> children = new ArrayList<>();

        try (var is = SitemapUtil.uncompressedSitemapStream(sitemapDoc)) {

            var sitemapLocationDir = substringBeforeLast(location, "/");
            XML.stream(is)
                .takeWhile(c -> {
                    if (stopping.isTrue()) {
                        LOG.debug("Sitemap not entirely parsed due to "
                                + "crawler being stopped.");
                        return false;
                    }
                    return true;
                })
                .forEachOrdered(c -> {
                    if ("sitemap".equalsIgnoreCase(c.getLocalName())) {
                        toSitemapRecord(c.readAsXML()).ifPresent(children::add);
                    } else if ("url".equalsIgnoreCase(c.getLocalName())) {
                        toDocRecord(c.readAsXML(), sitemapLocationDir)
                            .ifPresent(urlConsumer::accept);
                    }
                });
        } catch (XMLException e) {
            LOG.error("Cannot fetch sitemap: {} -- Likely an invalid sitemap "
                    + "XML format causing a parsing error (actual error:{}).",
                    location, e.getMessage());
        } catch (IOException e) {
            LOG.error("Cannot fetch sitemap: {} ({})",
                    location, e.getMessage(), e);
        }
        return children;
    }

    private Optional<SitemapRecord> toSitemapRecord(XML xml) {
        var url = xml.getString("loc");
        if (StringUtils.isBlank(url)) {
            return Optional.empty();
        }

        var rec = new SitemapRecord();
        rec.setLocation(url.trim());
        rec.setLastModified(null);

        return Optional.ofNullable(rec);
    }

    private Optional<WebCrawlDocContext> toDocRecord(
            XML xml, String sitemapLocationDir) {
        var url = xml.getString("loc");

        // Is URL valid?
        if (StringUtils.isBlank(url)
                || (!lenient && !url.startsWith(sitemapLocationDir))) {
            LOG.debug("Sitemap URL invalid for location directory."
                    + " URL: {}  Location directory: {}",
                    url, sitemapLocationDir);
            return Optional.empty();
        }

        var doc = new WebCrawlDocContext(url);
        doc.setSitemapLastMod(SitemapUtil.toDateTime(xml.getString("lastmod")));
        doc.setSitemapChangeFreq(xml.getString("changefreq"));
        var priority = xml.getString("priority");
        if (StringUtils.isNotBlank(priority)) {
            try {
                doc.setSitemapPriority(Float.parseFloat(priority));
            } catch (NumberFormatException e) {
                LOG.info("Invalid sitemap urlset/url/priority: {}", priority);
            }
        }
        LOG.debug("Sitemap document url: {}", doc.getReference());
        return Optional.ofNullable(doc);
    }
}