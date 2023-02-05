/* Copyright 2019 Norconex Inc.
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
package org.apache.pdfbox.jbig2.util.log;

import org.slf4j.LoggerFactory;

/**
 * This class only purpose is to route PDFBox JBig2 logging to SLF4J.
 * Loaded from /META-INF/services/org.apache.pdfbox.jbig2.util.log.LoggerBridge
 * Will no longer be needed if PDFBox JBig2 drops their custom logging
 * abstraction.
 */
public class SLF4JLoggerBridge implements LoggerBridge {

    @Override
    public Logger getLogger(Class<?> classToBeLogged) {
        return new SLF4JLogger(LoggerFactory.getLogger(classToBeLogged));
    }
}

