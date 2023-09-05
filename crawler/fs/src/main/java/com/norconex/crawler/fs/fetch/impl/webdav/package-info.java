@XmlJavaTypeAdapters({
    @XmlJavaTypeAdapter(
            value=DurationConverter.XmlAdapter.class, type=Duration.class)
})
package com.norconex.crawler.fs.fetch.impl.webdav;

import java.time.Duration;

import com.norconex.commons.lang.convert.DurationConverter;

import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapters;
