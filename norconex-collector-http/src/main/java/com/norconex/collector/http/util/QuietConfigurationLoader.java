package com.norconex.collector.http.util;

import java.io.Reader;

import org.apache.commons.configuration.XMLConfiguration;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.collector.http.HttpCollectorException;

public final class QuietConfigurationLoader {

    private QuietConfigurationLoader() {
        super();
    }

    @SuppressWarnings("nls")
    public static XMLConfiguration load(Reader in) {
        try {
            return ConfigurationLoader.loadXML(in);
        } catch (com.norconex.commons.lang.config.ConfigurationException e) {
            throw new HttpCollectorException("Cannot load from XML", e);
        }
    }
}
