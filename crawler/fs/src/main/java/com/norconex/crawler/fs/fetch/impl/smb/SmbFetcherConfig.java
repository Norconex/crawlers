/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.fs.fetch.impl.smb;

import com.norconex.crawler.fs.fetch.impl.BaseAuthVfsFetcherConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * CIFS fetcher (Samba, Windows share).
 * </p>
 *
 * {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher#doc}
 *
 * <h3>Access Control List (ACL)</h3>
 * <p>
 * This fetcher will try to extract access control information for each
 * SMB file. If you have no need for them, you can disable
 * acquiring them with {@link #setAclDisabled(boolean)}.
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.smb.SmbFetcher">
 *
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *
 *   {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher@nx.xml.usage}
 *
 *   <aclDisabled>[false|true]</aclDisabled>
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="SmbFetcher">
 *   <authentication>
 *     <username>joe</username>
 *     <password>joe's-password</password>
 *     <domain>WORKGROUP</domain>
 *   </authentication>
 * </fetcher>
 * }
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class SmbFetcherConfig extends BaseAuthVfsFetcherConfig {

    private boolean aclDisabled;
    //
    //    @Override
    //    protected void loadFetcherFromXML(XML xml) {
    //        super.loadFetcherFromXML(xml);
    //        setAclDisabled(xml.getBoolean(Fields.aclDisabled));
    //    }
    //    @Override
    //    protected void saveFetcherToXML(XML xml) {
    //        super.saveFetcherToXML(xml);
    //        xml.addElement(Fields.aclDisabled, aclDisabled);
    //    }
}
