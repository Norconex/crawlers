/**
 * 
 */
package com.norconex.collector.http.crawler.pipe.doc;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.HttpCrawlerEventFirer;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ final class DocumentPipelineUtil {

    private static final Logger LOG = 
            LogManager.getLogger(DocumentPipelineUtil.class);
    
    /**
     * Constructor.
     */
    private DocumentPipelineUtil() {
    }

    public static void enhanceHTTPHeaders(Properties metadata) {
        String contentType = metadata.getString("Content-Type");
        if (contentType != null) {
            String mimeType = contentType.replaceFirst("(.*?)(;.*)", "$1");
            String charset = contentType.replaceFirst("(.*?)(; )(.*)", "$3");
            charset = charset.replaceFirst("(charset=)(.*)", "$2");
            metadata.addString(HttpMetadata.DOC_MIMETYPE, mimeType);
            metadata.addString(HttpMetadata.DOC_CHARSET, charset);
        }
    }

    public static boolean isHeadersRejected(DocumentPipelineContext ctx) {
        IHttpHeadersFilter[] filters = ctx.getConfig().getHttpHeadersFilters();
        if (filters == null) {
            return false;
        }
        HttpMetadata headers = ctx.getMetadata();
        boolean hasIncludes = false;
        boolean atLeastOneIncludeMatch = false;
        for (IHttpHeadersFilter filter : filters) {
            boolean accepted = filter.acceptDocument(
                    ctx.getReference().getReference(), headers);
            boolean isInclude = filter instanceof IOnMatchFilter
                   && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
            if (isInclude) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }
            if (accepted) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "ACCEPTED document http headers. URL=%s Filter=%s",
                            ctx.getReference().getReference(), filter));
                }
            } else {
                HttpCrawlerEventFirer.fireDocumentHeadersRejected(
                        ctx.getCrawler(), 
                        ctx.getReference().getReference(), filter, headers);
                return true;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            HttpCrawlerEventFirer.fireDocumentHeadersRejected(
                    ctx.getCrawler(), 
                    ctx.getReference().getReference(), null, headers);
            return true;
        }
        return false;        
    }

}
