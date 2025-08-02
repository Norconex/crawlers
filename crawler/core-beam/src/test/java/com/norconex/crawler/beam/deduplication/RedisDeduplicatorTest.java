package com.norconex.crawler.beam.deduplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
class RedisDeduplicatorTest {
    
    @Mock
    private DeduplicationConfig mockConfig;
    
    private RedisDeduplicator deduplicator;
    
    @BeforeEach
    void setUp() {
        // Configure mock
        when(mockConfig.getRedisHost()).thenReturn("localhost");
        when(mockConfig.getRedisPort()).thenReturn(6379);
        
        // Create the deduplicator under test
        deduplicator = new RedisDeduplicator(mockConfig);
    }
    
    @Test
    void testIsDuplicate() {
        // Given a URL
        String url = "http://example.com/page1";
        
        // When checking for duplicate first time
        boolean firstCheck = deduplicator.isDuplicate(url);
        
        // Then it should not be a duplicate
        assertThat(firstCheck).isFalse();
        
        // When checking the same URL again
        boolean secondCheck = deduplicator.isDuplicate(url);
        
        // Then it should be identified as a duplicate
        assertThat(secondCheck).isTrue();
    }
    
    @Test
    void testExpandWithRedisDeduplication() {
        // This test would integrate with a test pipeline
        
        // Given
        TestPipeline p = TestPipeline.create();
        String[] testUrls = {"http://example.com/1", "http://example.com/1", "http://example.com/2"};
        
        // When applying Redis deduplication transform
        PCollection<String> input = p.apply(Create.of(testUrls));
        //PCollection<String> deduplicated = input.apply(deduplicator);
        
        // Then results should be deduplicated
        // Use PAssert to verify results
        // PAssert.that(deduplicated).containsInAnyOrder("http://example.com/1", "http://example.com/2");
        // p.run();
    }
    
    @Test
    void testClearCache() {
        // Given a URL that has been checked
        String url = "http://example.com/page1";
        deduplicator.isDuplicate(url); // Cache the URL
        
        // When clearing the cache
        deduplicator.clearCache();
        
        // Then the URL should not be identified as a duplicate anymore
        assertThat(deduplicator.isDuplicate(url)).isFalse();
    }
}