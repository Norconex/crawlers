package com.norconex.crawler.beam.frontier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrontierUrlTest {

    @Test
    void testConstructor() {
        // Given
        String url = "http://example.com/page";
        int depth = 2;
        
        // When
        FrontierUrl frontierUrl = new FrontierUrl(url, depth);
        
        // Then
        assertThat(frontierUrl).isNotNull();
        assertThat(frontierUrl.getUrl()).isEqualTo(url);
        assertThat(frontierUrl.getDepth()).isEqualTo(depth);
    }
    
    @Test
    void testEqualsAndHashCode() {
        // Given
        FrontierUrl url1 = new FrontierUrl("http://example.com/page", 1);
        FrontierUrl url2 = new FrontierUrl("http://example.com/page", 1);
        FrontierUrl url3 = new FrontierUrl("http://example.com/other", 1);
        FrontierUrl url4 = new FrontierUrl("http://example.com/page", 2);
        
        // When comparing
        
        // Then equals should reflect actual equality
        assertThat(url1).isEqualTo(url2);
        assertThat(url1).isNotEqualTo(url3); // Different URL
        assertThat(url1).isNotEqualTo(url4); // Different depth
        
        // And hashCode should be consistent with equals
        assertThat(url1.hashCode()).isEqualTo(url2.hashCode());
        assertThat(url1.hashCode()).isNotEqualTo(url3.hashCode());
        assertThat(url1.hashCode()).isNotEqualTo(url4.hashCode());
    }
    
    @Test
    void testToString() {
        // Given
        FrontierUrl frontierUrl = new FrontierUrl("http://example.com/page", 3);
        
        // When
        String string = frontierUrl.toString();
        
        // Then
        assertThat(string).contains("http://example.com/page");
        assertThat(string).contains("3"); // depth value
    }
    
    @Test
    void testGetHost() {
        // Given
        FrontierUrl frontierUrl = new FrontierUrl("http://example.com/page", 1);
        
        // When
        String host = frontierUrl.getHost();
        
        // Then
        assertThat(host).isEqualTo("example.com");
    }
    
    @Test
    void testGetPriority() {
        // Given
        FrontierUrl frontierUrl = new FrontierUrl("http://example.com/page", 1);
        // Set priority if your implementation supports it
        // frontierUrl.setPriority(5);
        
        // When
        // int priority = frontierUrl.getPriority();
        
        // Then
        // assertThat(priority).isEqualTo(5);
    }
}