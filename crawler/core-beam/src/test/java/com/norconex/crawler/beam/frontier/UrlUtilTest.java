package com.norconex.crawler.beam.frontier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlUtilTest {

    @Test
    void testExtractHost() {
        // Given various URLs
        String[] urls = {
            "http://example.com/page",
            "https://sub.example.com/page?param=value",
            "http://example.co.uk:8080/page",
            "https://user:pass@example.com/page",
            "ftp://example.com/file.txt"
        };
        
        // When extracting hosts
        String[] hosts = {
            UrlUtil.extractHost(urls[0]),
            UrlUtil.extractHost(urls[1]),
            UrlUtil.extractHost(urls[2]),
            UrlUtil.extractHost(urls[3]),
            UrlUtil.extractHost(urls[4])
        };
        
        // Then correct hosts should be extracted
        assertThat(hosts[0]).isEqualTo("example.com");
        assertThat(hosts[1]).isEqualTo("sub.example.com");
        assertThat(hosts[2]).isEqualTo("example.co.uk");
        assertThat(hosts[3]).isEqualTo("example.com");
        assertThat(hosts[4]).isEqualTo("example.com");
    }
    
    @Test
    void testExtractHostFromInvalidUrl() {
        // Given invalid URLs
        String[] invalidUrls = {
            "",
            null,
            "not a url",
            "://missing-protocol.com",
            "http://"
        };
        
        // When extracting hosts from invalid URLs
        // Then appropriate fallback behavior should occur
        for (String url : invalidUrls) {
            String host = UrlUtil.extractHost(url);
            // Default behavior might be to return null, empty string, or the URL itself
            // Adjust assertion based on your implementation
            if (url == null) {
                assertThat(host).isNull();
            } else {
                // This is just one possible implementation - adjust based on your actual behavior
                assertThat(host).isEmpty();
            }
        }
    }
    
    @Test
    void testIsValidUrl() {
        // Given a mix of valid and invalid URLs
        String[] validUrls = {
            "http://example.com",
            "https://sub.domain.com/path?query=value#fragment",
            "ftp://example.com/file.txt"
        };
        
        String[] invalidUrls = {
            "",
            null,
            "not a url",
            "http:/missing-slash.com",
            "://no-protocol.com"
        };
        
        // When checking URL validity
        
        // Then valid URLs should pass validation
        for (String url : validUrls) {
            assertThat(UrlUtil.isValidUrl(url)).isTrue();
        }
        
        // And invalid URLs should fail validation
        for (String url : invalidUrls) {
            assertThat(UrlUtil.isValidUrl(url)).isFalse();
        }
    }
    
    @Test
    void testNormalizeUrl() {
        // Given URLs that need normalization
        String url1 = "HTTP://Example.COM/path/";
        String url2 = "http://example.com/path/index.html#fragment";
        String url3 = "http://example.com:80/path/";
        
        // When normalizing
        String normalized1 = UrlUtil.normalizeUrl(url1);
        String normalized2 = UrlUtil.normalizeUrl(url2);
        String normalized3 = UrlUtil.normalizeUrl(url3);
        
        // Then URLs should be properly normalized
        // Exact assertions will depend on your normalization rules
        assertThat(normalized1).isEqualTo("http://example.com/path/");
        assertThat(normalized2).doesNotContain("#fragment");
        assertThat(normalized3).isEqualTo("http://example.com/path/");
    }
}