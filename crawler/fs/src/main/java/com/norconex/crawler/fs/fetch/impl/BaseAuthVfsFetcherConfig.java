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
package com.norconex.crawler.fs.fetch.impl;

import org.apache.commons.vfs2.util.EncryptUtil;

import com.norconex.commons.lang.security.Credentials;
import com.norconex.crawler.core.fetch.BaseFetcherConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Extension of {@link AbstractVfsFetcher}, adding authentication support.
 * </p>
 *
 * <h3>Generic authentication settings</h3>
 * <p>
 * The following is available to all implementing classes.
 * </p>
 *
 * {@nx.xml.usage
 * <!-- Optional authentication details. -->
 * <authentication>
 *   {@nx.include com.norconex.commons.lang.security.Credentials@nx.xml.usage}
 *   <domain>(If required to authenticate, the user's domain.)</domain>
 * </authentication>
 * }
 *
 * {@nx.block #doc
 * {@nx.include com.norconex.commons.lang.security.Credentials#doc}
 * <p>
 * You can also have password set on the URL, Apache
 * Commons VFS offers a way to encrypt it there using their own
 * {@link EncryptUtil}. More info under the "Naming" section here:
 * <a href="http://commons.apache.org/proper/commons-vfs/filesystems.html">
 * http://commons.apache.org/proper/commons-vfs/filesystems.html</a>
 * </p>
 * }
 *
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public abstract class BaseAuthVfsFetcherConfig extends BaseFetcherConfig{

    private final Credentials credentials = new Credentials();
    private String domain;

//    @Override
//    protected void loadFetcherFromXML(XML xml) {
//        xml.ifXML("authentication", authXml -> {
//            authXml.ifXML("credentials", credentials::loadFromXML);
//            setDomain(authXml.getString("domain", domain));
//        });
//    }
//    @Override
//    protected void saveFetcherToXML(XML xml) {
//        var authXml = xml.addElement("authentication");
//        credentials.saveToXML(authXml.addElement("credentials"));
//        authXml.addElement("domain", domain);
//    }
}
