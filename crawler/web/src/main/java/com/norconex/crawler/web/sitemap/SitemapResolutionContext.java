package com.norconex.crawler.web.sitemap;

import java.util.List;
import java.util.function.Consumer;

import com.norconex.crawler.web.doc.HttpDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcher;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SitemapResolutionContext {

    private final HttpFetcher fetcher;
    private final String urlRoot;
    private final List<String> sitemapLocations;
    private final Consumer<HttpDocRecord> urlConsumer;
    private final boolean startURLs;

}
