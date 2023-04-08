package com.norconex.crawler.web.sitemap;

import java.util.function.Consumer;

import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcher;

import lombok.Builder;
import lombok.Data;
import lombok.With;

@Data
@Builder
public class SitemapContext {

    @With
    private final String location;
    private final HttpFetcher fetcher;
    private final Consumer<WebDocRecord> urlConsumer;
}
