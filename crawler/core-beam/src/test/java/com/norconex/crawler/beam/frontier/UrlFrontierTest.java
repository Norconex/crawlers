package com.norconex.crawler.beam.frontier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlFrontierTest {

    @Mock
    private FrontierConfig mockConfig;
    
    @Mock
    private UrlNormalizer mockNormalizer;
    
    @Mock
    private UrlPrioritizer mockPrioritizer;
    
    private UrlFrontier frontier;
    
    @BeforeEach
    void setUp() {
        // Configure mocks
        when(mockNormalizer.normalize("http://example.com/page")).thenReturn("http://example.com/page");
        when(mockPrioritizer.assignPriority(org.mockito.ArgumentMatchers.any(FrontierUrl.class))).thenReturn(5);
        
        frontier = new UrlFrontier(mockConfig, mockNormalizer, mockPrioritizer);
    }
    
    @Test
    void testAddUrl() {
        // Given a URL
        String url = "http://example.com/page";
        int depth = 1;
        
        // When adding to frontier
        frontier.addUrl(url, depth);
        
        // Then frontier should contain the URL
        assertThat(frontier.hasNext()).isTrue();
        
        // And the next URL should be the one we added
        FrontierUrl next = frontier.next();
        assertThat(next).isNotNull();
        assertThat(next.getUrl()).isEqualTo(url);
        assertThat(next.getDepth()).isEqualTo(depth);
    }
    
    @Test
    void testAddUrlsInBulk() {
        // Given multiple URLs
        String[] urls = {
            "http://example.com/page1",
            "http://example.com/page2",
            "http://example.com/page3"
        };
        int depth = 1;
        
        // When adding them in bulk
        frontier.addUrls(urls, depth);
        
        // Then frontier should contain all URLs
        assertThat(frontier.size()).isEqualTo(urls.length);
        
        // And all URLs should be retrievable
        for (int i = 0; i < urls.length; i++) {
            assertThat(frontier.hasNext()).isTrue();
            FrontierUrl next = frontier.next();
            assertThat(next).isNotNull();
            // We can't assert exact order without knowing the queue implementation
        }
    }
    
    @Test
    void testUrlNormalization() {
        // Given a URL that needs normalization
        String originalUrl = "HTTP://EXAMPLE.COM/page";
        String normalizedUrl = "http://example.com/page";
        when(mockNormalizer.normalize(originalUrl)).thenReturn(normalizedUrl);
        
        // When adding to frontier
        frontier.addUrl(originalUrl, 1);
        
        // Then the normalized URL should be used
        FrontierUrl next = frontier.next();
        assertThat(next.getUrl()).isEqualTo(normalizedUrl);
    }
    
    @Test
    void testUrlPrioritization() {
        // Given URLs with different assigned priorities
        String urlHigh = "http://example.com/high";
        String urlLow = "http://example.com/low";
        
        when(mockPrioritizer.assignPriority(org.mockito.ArgumentMatchers.argThat(
                frontierUrl -> frontierUrl.getUrl().equals(urlHigh)))).thenReturn(1);
        when(mockPrioritizer.assignPriority(org.mockito.ArgumentMatchers.argThat(
                frontierUrl -> frontierUrl.getUrl().equals(urlLow)))).thenReturn(10);
        
        // When adding to frontier
        frontier.addUrl(urlLow, 1);
        frontier.addUrl(urlHigh, 1);
        
        // Then higher priority URL should be retrieved first
        assertThat(frontier.next().getUrl()).isEqualTo(urlHigh);
        assertThat(frontier.next().getUrl()).isEqualTo(urlLow);
    }
    
    @Test
    void testIsEmpty() {
        // Given an initially empty frontier
        assertThat(frontier.isEmpty()).isTrue();
        assertThat(frontier.hasNext()).isFalse();
        
        // When adding a URL
        frontier.addUrl("http://example.com/page", 1);
        
        // Then frontier should not be empty
        assertThat(frontier.isEmpty()).isFalse();
        assertThat(frontier.hasNext()).isTrue();
        
        // When retrieving the URL
        frontier.next();
        
        // Then frontier should be empty again
        assertThat(frontier.isEmpty()).isTrue();
        assertThat(frontier.hasNext()).isFalse();
    }
    
    @Test
    void testSize() {
        // Given an initially empty frontier
        assertThat(frontier.size()).isZero();
        
        // When adding URLs
        frontier.addUrl("http://example.com/page1", 1);
        frontier.addUrl("http://example.com/page2", 1);
        
        // Then size should reflect number of URLs
        assertThat(frontier.size()).isEqualTo(2);
        
        // When retrieving a URL
        frontier.next();
        
        // Then size should be reduced
        assertThat(frontier.size()).isEqualTo(1);
    }
}