package com.norconex.crawler.beam.frontier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlPrioritizerTest {
    
    @Mock
    private FrontierConfig mockConfig;
    
    private UrlPrioritizer prioritizer;
    
    @BeforeEach
    void setUp() {
        prioritizer = new UrlPrioritizer(mockConfig);
    }

    @Test
    void testAssignPriority() {
        // Given
        FrontierUrl url = new FrontierUrl("http://example.com/page", 1);
        
        // When
        int priority = prioritizer.assignPriority(url);
        
        // Then
        assertThat(priority).isGreaterThanOrEqualTo(0); // Priority should be non-negative
        // Add more assertions based on your prioritization logic
    }
    
    @Test
    void testPriorityBasedOnDepth() {
        // Given URLs with different depths
        FrontierUrl shallowUrl = new FrontierUrl("http://example.com/shallow", 1);
        FrontierUrl deepUrl = new FrontierUrl("http://example.com/deep", 5);
        
        // When assigning priorities
        int shallowPriority = prioritizer.assignPriority(shallowUrl);
        int deepPriority = prioritizer.assignPriority(deepUrl);
        
        // Then shallower URLs should have higher priority (lower number is higher priority)
        // Note: This assertion might need to be reversed depending on your priority scheme
        assertThat(shallowPriority).isLessThanOrEqualTo(deepPriority);
    }
    
    @Test
    void testPriorityBasedOnPattern() {
        // Given URL patterns with different priorities in config
        when(mockConfig.getUrlPriorityPattern(".*important.*")).thenReturn(1);
        when(mockConfig.getUrlPriorityPattern(".*regular.*")).thenReturn(5);
        
        // And URLs matching those patterns
        FrontierUrl importantUrl = new FrontierUrl("http://example.com/important-page", 1);
        FrontierUrl regularUrl = new FrontierUrl("http://example.com/regular-page", 1);
        
        // When assigning priorities
        int importantPriority = prioritizer.assignPriority(importantUrl);
        int regularPriority = prioritizer.assignPriority(regularUrl);
        
        // Then URLs matching higher priority patterns should get higher priority
        assertThat(importantPriority).isLessThan(regularPriority);
    }
    
    @Test
    void testDefaultPriority() {
        // Given a URL that doesn't match any specific pattern
        FrontierUrl url = new FrontierUrl("http://example.com/page", 1);
        when(mockConfig.getDefaultPriority()).thenReturn(10);
        
        // When
        int priority = prioritizer.assignPriority(url);
        
        // Then it should get the default priority
        assertThat(priority).isEqualTo(10);
    }
}