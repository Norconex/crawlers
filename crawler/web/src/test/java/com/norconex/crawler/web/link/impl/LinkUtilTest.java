package com.norconex.crawler.web.link.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkUtilTest {

    @Test
    void extractHttpEquivRefreshContentUrl_noUrl_returnsEmptyString() {
        //setup
        String content = "3;url=https://www.norconex.com";

        //execute
        String actual = LinkUtil.extractHttpEquivRefreshContentUrl(content);

        //verify
        assertThat(actual).isEqualTo("https://www.norconex.com");

    }
}