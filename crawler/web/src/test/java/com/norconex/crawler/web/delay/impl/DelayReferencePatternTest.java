package com.norconex.crawler.web.delay.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DelayReferencePatternTest {

    @Test
    void testMatches_emptyPattern_isMatched() {
        //setup
        String pattern = "";
        long delay = 100;
        var artifact = new DelayReferencePattern(pattern, delay);
        boolean expected = true;

        //execute
        boolean actual = artifact.matches("");

        //verify
        assertThat(actual).isTrue();
    }

    @Test
    void testMatches_emptyPatternOnNonEmptyInput_isNotMatched() {
        //setup
        String pattern = "";
        long delay = 100;
        var artifact = new DelayReferencePattern(pattern, delay);
        boolean expected = true;

        //execute
        boolean actual = artifact.matches("www.simpsons.com");

        //verify
        assertThat(actual).isFalse();
    }

    @Test
    void testMatches_nonEmptyPatternOnEmptyInput_isNotMatched() {
        //setup
        String pattern = "www.simpsons.com";
        long delay = 100;
        var artifact = new DelayReferencePattern(pattern, delay);
        boolean expected = true;

        //execute
        boolean actual = artifact.matches("");

        //verify
        assertThat(actual).isFalse();
    }

    @Test
    void testMatches_regexPattern_isMatched() {
        //setup
        String pattern = "^w{3}\\.[a-z]{8}\\.com";
        long delay = 100;
        var artifact = new DelayReferencePattern(pattern, delay);
        boolean expected = true;

        //execute
        boolean actual = artifact.matches("www.simpsons.com");

        //verify
        assertThat(actual).isTrue();
    }

    @Test
    void testMatches_regexPattern_isNotMatched() {
        //setup
        String pattern = "^w{3}\\.[a-z]{8}\\.ca";
        long delay = 100;
        var artifact = new DelayReferencePattern(pattern, delay);
        boolean expected = true;

        //execute
        boolean actual = artifact.matches("www.simpsons.com");

        //verify
        assertThat(actual).isFalse();
    }
}
