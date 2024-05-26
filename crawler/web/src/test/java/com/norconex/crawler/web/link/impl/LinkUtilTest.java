package com.norconex.crawler.web.link.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkUtilTest {

    @Test
    void extractHttpEquivRefreshContentUrl_noUrl_returnsEmptyString() {
        //setup
        var content = "3;url=https://www.norconex.com";

        //execute
        var actual = LinkUtil.extractHttpEquivRefreshContentUrl(content);

        //verify
        assertThat(actual).isEqualTo("https://www.norconex.com");

    }
}