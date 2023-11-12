/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.fs.fetch.impl.cmis;

import com.norconex.commons.lang.time.DurationParser;
import com.norconex.crawler.fs.fetch.impl.BaseAuthVfsFetcherConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * CMIS-enabled Content Management Systems (CMS) fetcher
 * (Atom end-point).
 * The start path can be specified as:
 * <code>cmis:http://yourhost:port/path/to/atom</code>.
 * Optionally you can have a non-root starting path by adding the path
 * name to the base URL, with an exclamation mark as a separator:
 * <code>cmis:http://yourhost:port/path/to/atom!/MyFolder/MySubFolder</code>.
 * Start paths are assumed to be Atom URLs.
 * </p>
 *
 * {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher#doc}
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.cmis.CmisFetcher">
 *
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *
 *   {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher@nx.xml.usage}
 *
 *   <repositoryId>
 *     (Optional repository ID, defaults to first one found.)
 *   </repositoryId>
 *   <xmlTargetField>
 *     (Optional target field name where to store the raw CMIS REST API
 *      XML. Default does not store the raw XML in a field.)
 *   </xmlTargetField>
 *   <aclDisabled>[false|true]</aclDisabled>
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="CmisFetcher" />
 * }
 * <p>
 * The above example the SFTP time out to 2 minutes.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class CmisFetcherConfig extends BaseAuthVfsFetcherConfig {

    /**
     * The CMIS repository ID. Defaults to first one found.
     * @param repositoryId repository ID
     * @return repository id
     */
    private String repositoryId;
    /**
     * The name of the field where the raw XML obtained from
     * the CMIS REST API will be stored. Defaults to <code>null</code>
     * (does not store the raw XML in a field).
     * @param cmisXmlTargetField target field
     * @return field name
     */
    private String xmlTargetField;

    private boolean aclDisabled;

}
