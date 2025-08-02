package com.norconex.crawler.beam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.junit.jupiter.api.Test;

class BeamCrawlerOptionsTest {

    @Test
    void testDefaultOptionsCreation() {
        // When creating default options
        BeamCrawlerOptions options = PipelineOptionsFactory.as(BeamCrawlerOptions.class);

        // Then options should not be null and have expected defaults
        assertThat(options).isNotNull();
        // Add assertions for default values once they are defined in BeamCrawlerOptions
    }

    @Test
    void testRegisterType() {
        // When registering options type
        assertThatNoException().isThrownBy(() -> 
            PipelineOptionsFactory.register(BeamCrawlerOptions.class));

        // Then options can be created via command line arguments
        String[] args = {"--runner=DirectRunner"};
        BeamCrawlerOptions options = PipelineOptionsFactory
                .fromArgs(args)
                .as(BeamCrawlerOptions.class);

        assertThat(options.getRunner().getName()).isEqualTo("DirectRunner");
    }
    
    @Test
    void testOptionsWithCustomValues() {
        // When creating options with custom values
        String[] args = {
                "--runner=DirectRunner",
                // Add other custom options here based on BeamCrawlerOptions implementation
        };
        
        BeamCrawlerOptions options = PipelineOptionsFactory
                .fromArgs(args)
                .as(BeamCrawlerOptions.class);
        
        // Then options should have the expected custom values
        assertThat(options.getRunner().getName()).isEqualTo("DirectRunner");
        // Add more assertions for custom values
    }
}