/* Copyright 2017-2019 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.lang3.ClassUtils;
import org.junit.Assert;

import com.norconex.commons.lang.xml.XML;


/**
 * @author Pascal Essiembre
 * @since 1.8.0
 */
public final class TestUtil {

    private TestUtil() {
        super();
    }

    // get config of the same name as class, but with .xml extension.
    public static HttpCollectorConfig loadCollectorConfig(
            Class<?> clazz) throws IOException {
        return loadCollectorConfig(clazz, clazz.getSimpleName() + ".xml");
    }
    // get config from resource relative to class
    public static HttpCollectorConfig loadCollectorConfig(
            Class<?> clazz, String xmlResource) throws IOException {
        HttpCollectorConfig cfg = new HttpCollectorConfig();
        try (Reader r = new InputStreamReader(
                clazz.getResourceAsStream(xmlResource))) {

            new XML(r).populate(cfg);

//            cfg.loadFromXML(xml);
//            XML ConfigurationUtil.loadFromXML(cfg, r);
        }
        return cfg;
    }
    public static void testValidation(String xmlResource) throws IOException {
        testValidation(TestUtil.class.getResourceAsStream(xmlResource));

    }
    public static void testValidation(Class<?> clazz) throws IOException {
        testValidation(clazz, ClassUtils.getShortClassName(clazz) + ".xml");
    }
    public static void testValidation(Class<?> clazz, String xmlResource)
            throws IOException {
        testValidation(clazz.getResourceAsStream(xmlResource));

    }
    public static void testValidation(
            InputStream xmlStream) throws IOException {

        try (Reader r = new InputStreamReader(xmlStream)) {
            Assert.assertTrue("Validation warnings/errors were found.",
                    new XML(r).validate().isEmpty());
        }

//        CountingConsoleAppender appender = new CountingConsoleAppender();
//        appender.startCountingFor(XMLConfigurationUtil.class, Level.WARN);
//        try (Reader r = new InputStreamReader(xmlStream)) {
//            XMLConfigurationUtil.newInstance(r);
//        } finally {
//            appender.stopCountingFor(XMLConfigurationUtil.class);
//        }
//        Assert.assertEquals("Validation warnings/errors were found.",
//                0, appender.getCount());
    }
}
