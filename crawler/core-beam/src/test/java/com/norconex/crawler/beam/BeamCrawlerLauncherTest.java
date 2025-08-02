package com.norconex.crawler.beam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.runners.direct.DirectRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BeamCrawlerLauncherTest {

    @Mock
    private BeamCrawlerOptions mockOptions;

    @Test
    void testCreatePipeline() {
        // Given
        when(mockOptions.getRunner()).thenReturn(DirectRunner.class);
        
        // When
        Pipeline pipeline = BeamCrawlerLauncher.createPipeline(mockOptions);
        
        // Then
        assertThat(pipeline).isNotNull();
        assertThat(pipeline.getOptions().getRunner()).isEqualTo(DirectRunner.class);
    }
    
    @Test
    void testMain_withValidArgs() {
        // This test verifies that the main method doesn't throw exceptions with valid arguments
        String[] args = {"--runner=DirectRunner"};
        
        assertThatNoException().isThrownBy(() -> {
            // Use a mock or test implementation to avoid actually running the pipeline
            // This might require refactoring BeamCrawlerLauncher for better testability
            // For now, we'll just verify it doesn't throw exceptions
            BeamCrawlerLauncher.main(args);
        });
    }
}