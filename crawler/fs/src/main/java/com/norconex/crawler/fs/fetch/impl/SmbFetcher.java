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

import org.apache.commons.vfs2.FileSystemOptions;

import com.norconex.commons.lang.xml.XML;

import lombok.Data;

/**
 *
 */
@Data
public class SmbFetcher extends AbstractVfsFetcher {



    /*
    private void resolveFileException(String ref, Exception e) {
        Throwable t = ExceptionUtils.getRootCause(e);
        if (t instanceof MalformedURLException) {
            if (StringUtils.containsIgnoreCase(t.getMessage(), "smb")) {
                LOG.error("SMB protocol requires you to have this library in "
                        + "your classpath (e.g. \"lib\" folder): "
                        + "http://central.maven.org/maven2/jcifs/jcifs/"
                        + "1.3.17/jcifs-1.3.17.jar");
            } else if (StringUtils.containsIgnoreCase(
                    t.getMessage(), "unknown protocol")) {
                LOG.error("The protocol used may be unsupported or requires "
                        + "you to install missing dependencies.");
            }
        }
        throw new CollectorException("Cannot resolve: " + ref, e);
    }
    */

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        // TODO Auto-generated method stub
    }



    @Override
    protected void loadFetcherFromXML(XML xml) {
        // TODO Auto-generated method stub

    }
    @Override
    protected void saveFetcherToXML(XML xml) {
        // TODO Auto-generated method stub

    }
}
