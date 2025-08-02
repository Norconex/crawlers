package com.norconex.crawler.beam.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlDiscoveryTest {
    
    @Mock
    private DiscoveryConfig mockConfig;
    
    private UrlDiscovery discovery;
    
    @BeforeEach
    void setUp() {
        discovery = new UrlDiscovery(mockConfig);
    }
    
    @Test
    void testDiscoverUrls() {
        // Given a webpage content with links
        String html = "<html><body>"
                + "<a href='http://example.com/page1'>Link 1</a>"
                + "<a href='http://example.com/page2'>Link 2</a>"
                + "</body></html>";
        
        // When discovering URLs
        Iterable<String> urls = discovery.discoverUrls(html, "http://example.com");
        
        // Then all URLs should be discovered
        assertThat(urls).containsExactlyInAnyOrder(
                "http://example.com/page1", 
                "http://example.com/page2");
    }
    
    @Test
    void testDiscoverUrlsWithBaseUrl() {
        // Given a webpage content with relative links and a base URL
        String html = "<html><head>"
                + "<base href='http://example.com/subdir/'>"
                + "</head><body>"
                + "<a href='page1.html'>Link 1</a>"
                + "<a href='/rootpage.html'>Link 2</a>"
                + "<a href='http://other.com/page'>Link 3</a>"
                + "</body></html>";
        
        // When discovering URLs
        Iterable<String> urls = discovery.discoverUrls(html, "http://example.com");
        
        // Then URLs should be properly resolved against base URL
        assertThat(urls).containsExactlyInAnyOrder(
                "http://example.com/subdir/page1.html", 
                "http://example.com/rootpage.html",
                "http://other.com/page");
    }
    
    @Test
    void testRespectMaxDepth() {
        // Given a config with max depth
        when(mockConfig.getMaxDepth()).thenReturn(2);
        
        // And a URL at the max depth
        String url = "http://example.com/level1/level2";
        when(mockConfig.getDepthOf(url)).thenReturn(2);
        
        // When discovering URLs from content at max depth
        String html = "<html><body>"
                + "<a href='http://example.com/level1/level2/level3'>Link 1</a>"
                + "</body></html>";
        Iterable<String> urls = discovery.discoverUrls(html, url);
        
        // Then no URLs should be discovered (because they would exceed max depth)
        assertThat(urls).isEmpty();
    }
    
    @Test
    void testApplyUrlFilters() {
        // Given a config with URL filters
        when(mockConfig.isUrlAllowed("http://example.com/allowed")).thenReturn(true);
        when(mockConfig.isUrlAllowed("http://example.com/blocked")).thenReturn(false);
        
        // When discovering URLs
        String html = "<html><body>"
                + "<a href='http://example.com/allowed'>Link 1</a>"
                + "<a href='http://example.com/blocked'>Link 2</a>"
                + "</body></html>";
        Iterable<String> urls = discovery.discoverUrls(html, "http://example.com");
        
        // Then only allowed URLs should be discovered
        assertThat(urls).containsExactly("http://example.com/allowed");
    }
}