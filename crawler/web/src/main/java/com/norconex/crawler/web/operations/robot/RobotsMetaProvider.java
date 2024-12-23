/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.operations.robot;

import java.io.IOException;
import java.io.Reader;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;

/**
 * Responsible for extracting robot information from a page.
 */
public interface RobotsMetaProvider {

    /**
     * Extracts Robots meta information for a page, if any.
     * @param document the document
     * @param documentUrl document url
     * @param contentType the document content type
     * @param httpHeaders the document HTTP Headers
     * @return robots meta instance
     * @throws IOException problem reading the document
     */
    RobotsMeta getRobotsMeta(
            Reader document, String documentUrl, ContentType contentType,
            Properties httpHeaders) throws IOException;
}
