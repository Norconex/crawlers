/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.http.crawler.HttpCrawlerEventFirer;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.ref.HttpDocReference;
import com.norconex.collector.http.ref.pipe.ReferencePipeline;
import com.norconex.collector.http.ref.pipe.ReferencePipelineContext;
import com.norconex.collector.http.url.IURLExtractor;

/**
 * Extract URLs before sending to importer (because the importer may
 * strip some "valid" urls in producing content-centric material.
 * Plus, any additional urls could be added to Metadata and they will
 * be considered.
 */
/*default*/ class URLExtractorStage 
        implements IPipelineStage<DocumentPipelineContext> {

    private static final Logger LOG = LogManager
            .getLogger(URLExtractorStage.class);
    
    @Override
    public boolean process(DocumentPipelineContext ctx) {
        if (ctx.getRobotsMeta() != null 
                && ctx.getRobotsMeta().isNofollow()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No URLs extracted due to Robots nofollow rule "
                        + "for URL: " + ctx.getReference().getReference());
            }
            return true;
        }
        
        Set<String> urls = null;
        try {
            Reader reader = ctx.getContentReader();
            IURLExtractor urlExtractor = ctx.getConfig().getUrlExtractor();
            urls = urlExtractor.extractURLs(
                    reader, ctx.getReference().getReference(), 
                    ctx.getDocument().getContentType());
            IOUtils.closeQuietly(reader);
        } catch (IOException e) {
            throw new CollectorException(
                    "Cannot extract URLs from: " + ctx.getReference().getReference(), e);
        }

        Set<String> uniqueURLs = new HashSet<String>();
        if (urls != null) {
            for (String url : urls) {
                HttpDocReference newURL = new HttpDocReference(
                        url, ctx.getReference().getDepth() + 1);
                ReferencePipelineContext context = new ReferencePipelineContext(
                        ctx.getCrawler(), ctx.getReferenceStore(), newURL);
                if (new ReferencePipeline().process(context)) {
                    uniqueURLs.add(newURL.getReference());
                }
            }
        }
        if (!uniqueURLs.isEmpty()) {
            ctx.getMetadata().addString(HttpMetadata.COLLECTOR_REFERNCED_URLS, 
                    uniqueURLs.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
        }
        HttpCrawlerEventFirer.fireDocumentURLsExtracted(
                ctx.getCrawler(), ctx.getDocument());
        return true;
    }
}