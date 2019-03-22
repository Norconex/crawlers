package com.norconex.collector.http.pipeline.importer;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.commons.lang.map.Properties;
import org.junit.Test;

import static com.norconex.collector.http.doc.HttpMetadata.*;
import static org.junit.Assert.*;

public class HttpImporterPipelineUtilTest {

    @Test
    public void enhanceHTTPHeaders() {

        assertEnhancedHeaders("text/html; charset=UTF-8",
                null, null,
                "text/html", "UTF-8");

        assertEnhancedHeaders("text/html; charset=UTF-8",
                "our/custom", null,
                "our/custom", "UTF-8");

        assertEnhancedHeaders("text/html; charset=utf-8",
                null, "iso-8859-4",
                "text/html", "iso-8859-4");

        assertEnhancedHeaders("text/html; charset=",
                null, null,
                "text/html", null);

        assertEnhancedHeaders("text/html;",
                null, null,
                "text/html", null);

        assertEnhancedHeaders("text/html",
                null, null,
                "text/html", null);

        assertEnhancedHeaders("",
                null, null,
                null, null);

        assertEnhancedHeaders(null,
                null, null,
                null, null);

        // alternative allowed versions for content-type:
        // https://tools.ietf.org/html/rfc7231#section-3.1.1

        assertEnhancedHeaders("text/html;charset=UTF-8",
                null, null,
                "text/html", "UTF-8");

        assertEnhancedHeaders("text/html;charset=\"UTF-8\"",
                null, null,
                "text/html", "UTF-8");

        assertEnhancedHeaders("text/html;charset=utf-8",
                null, null,
                "text/html", "UTF-8");

        assertEnhancedHeaders("text/html;charset=\"utf-8\"",
                null, null,
                "text/html", "UTF-8");

    }

    private void assertEnhancedHeaders(String httpContentType,
                                       String inputCollectorContentType, String inputCollectorEncoding,
                                       String expectedCollectorContentType, String expectedCollectorContentEncoding) {
        Properties metadata = new Properties();

        if (httpContentType != null) {
            metadata.addString(HTTP_CONTENT_TYPE, httpContentType);
        }
        if (inputCollectorContentType != null) {
            metadata.addString(COLLECTOR_CONTENT_TYPE, inputCollectorContentType);
        }
        if (inputCollectorEncoding != null) {
            metadata.addString(COLLECTOR_CONTENT_ENCODING, inputCollectorEncoding);
        }
        HttpImporterPipelineUtil.enhanceHTTPHeaders(new HttpMetadata(metadata));

        assertEquals(expectedCollectorContentType, metadata.getString(COLLECTOR_CONTENT_TYPE));
        assertEquals(expectedCollectorContentEncoding, metadata.getString(COLLECTOR_CONTENT_ENCODING));
    }
}