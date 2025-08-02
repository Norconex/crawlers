package com.norconex.crawler.beam.frontier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostBasedQueueTest {

    private HostBasedQueue queue;
    
    @BeforeEach
    void setUp() {
        queue = new HostBasedQueue();
    }
    
    @Test
    void testAddAndPoll() {
        // Given
        FrontierUrl url1 = new FrontierUrl("http://example.com/page1", 1);
        FrontierUrl url2 = new FrontierUrl("http://example.com/page2", 1);
        
        // When adding URLs to the queue
        queue.add(url1);
        queue.add(url2);
        
        // Then they should be retrievable in the order they were added
        assertThat(queue.poll()).isEqualTo(url1);
        assertThat(queue.poll()).isEqualTo(url2);
        assertThat(queue.poll()).isNull(); // Queue should be empty
    }
    
    @Test
    void testPriorityOrdering() {
        // Given URLs with different priorities
        FrontierUrl highPriority = new FrontierUrl("http://example.com/high", 1);
        highPriority.setPriority(1);
        
        FrontierUrl mediumPriority = new FrontierUrl("http://example.com/medium", 1);
        mediumPriority.setPriority(5);
        
        FrontierUrl lowPriority = new FrontierUrl("http://example.com/low", 1);
        lowPriority.setPriority(10);
        
        // When adding URLs in reverse priority order
        queue.add(lowPriority);
        queue.add(mediumPriority);
        queue.add(highPriority);
        
        // Then they should be retrieved in priority order (highest first)
        assertThat(queue.poll()).isEqualTo(highPriority);
        assertThat(queue.poll()).isEqualTo(mediumPriority);
        assertThat(queue.poll()).isEqualTo(lowPriority);
    }
    
    @Test
    void testMultipleHosts() {
        // Given URLs from different hosts
        FrontierUrl url1 = new FrontierUrl("http://example1.com/page", 1);
        FrontierUrl url2 = new FrontierUrl("http://example2.com/page", 1);
        FrontierUrl url3 = new FrontierUrl("http://example3.com/page", 1);
        FrontierUrl url4 = new FrontierUrl("http://example1.com/another", 1);
        
        // When adding them to the queue
        queue.add(url1);
        queue.add(url2);
        queue.add(url3);
        queue.add(url4);
        
        // Then they should be retrieved in a way that alternates between hosts
        FrontierUrl first = queue.poll();
        FrontierUrl second = queue.poll();
        FrontierUrl third = queue.poll();
        FrontierUrl fourth = queue.poll();
        
        // Host of second URL should be different from first
        assertThat(second.getHost()).isNotEqualTo(first.getHost());
        
        // After cycling through all hosts, should come back to first host
        // This assumes the queue uses round-robin between hosts
        assertThat(fourth.getHost()).isEqualTo(first.getHost());
    }
    
    @Test
    void testIsEmpty() {
        // Given an initially empty queue
        assertThat(queue.isEmpty()).isTrue();
        
        // When adding a URL
        queue.add(new FrontierUrl("http://example.com/page", 1));
        
        // Then queue should not be empty
        assertThat(queue.isEmpty()).isFalse();
        
        // When polling the URL
        queue.poll();
        
        // Then queue should be empty again
        assertThat(queue.isEmpty()).isTrue();
    }
    
    @Test
    void testSize() {
        // Given an initially empty queue
        assertThat(queue.size()).isZero();
        
        // When adding URLs
        queue.add(new FrontierUrl("http://example.com/page1", 1));
        queue.add(new FrontierUrl("http://example.com/page2", 1));
        
        // Then size should reflect number of URLs
        assertThat(queue.size()).isEqualTo(2);
        
        // When polling a URL
        queue.poll();
        
        // Then size should be reduced
        assertThat(queue.size()).isEqualTo(1);
    }
}