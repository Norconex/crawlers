package com.norconex.crawler.beam.frontier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlNormalizerTest {
    
    @Mock
    private FrontierConfig mockConfig;
    
    private UrlNormalizer normalizer;
    
    @BeforeEach
    void setUp() {
        normalizer = new UrlNormalizer(mockConfig);
    }

    @Test
    void testNormalizeUrl() {
        // Given URLs with various non-standard formats
        String[] urls = {
            "HTTP://Example.COM/path/",
            "http://example.com/path/index.html#fragment",
            "http://example.com:80/path/",
            "http://example.com/path//to///resource",
            "http://example.com/path/./../to/resource",
            "http://example.com/path/?a=1&b=2",
            "http://example.com/path/?b=2&a=1",  // Same params, different order
            "http://user:pass@example.com/"
        };
        
        // When normalizing each URL
        for (String url : urls) {
            String normalized = normalizer.normalize(url);
            
            // Then result should be normalized
            assertThat(normalized).isNotNull();
            assertThat(normalized).isNotEqualTo(url).unless(url::equals);
            
            // Specific normalization checks
            // These will depend on your exact normalization rules
            assertThat(normalized).doesNotContain("#fragment");
            assertThat(normalized.toLowerCase()).isEqualTo(normalized); // Check lowercase
            assertThat(normalized).doesNotContain("//path");  // No double slashes
        }
    }
    
    @Test
    void testNormalizeSpecialCases() {
        // Given
        String emptyUrl = "";
        String nullUrl = null;
        String malformedUrl = "not a url";
        
        // When/Then
        // Test how your normalizer handles edge cases
        assertThat(normalizer.normalize(emptyUrl)).isEqualTo(emptyUrl);
        assertThat(normalizer.normalize(nullUrl)).isNull();
        // Depending on implementation, might throw exception or return as-is
        // assertThat(normalizer.normalize(malformedUrl)).isEqualTo(malformedUrl);
    }
    
    @Test
    void testNormalizeWithConfig() {
        // Given configuration settings
        // when(mockConfig.getUrlNormalizationSettings()).thenReturn(...);
        
        // When normalizing with those settings
        
        // Then normalization should respect configuration
        // Add assertions based on your configuration implementation
    }
}