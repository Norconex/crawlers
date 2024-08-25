/* Copyright 2019-2024 Norconex Inc.
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

import org.apache.commons.vfs2.FileSystemConfigBuilder;
import org.apache.commons.vfs2.FileSystemOptions;

public class CmisAtomFileSystemConfigBuilder extends FileSystemConfigBuilder {
    private static final CmisAtomFileSystemConfigBuilder INSTANCE =
            new CmisAtomFileSystemConfigBuilder();

    private static final String PARAM_RESPOSITORY_ID =
            CmisAtomFileSystemConfigBuilder.class.getName() + ".repositoryId";
    private static final String PARAM_XML_TARGET_FIELD =
            CmisAtomFileSystemConfigBuilder.class.getName() + ".xmlTargetField";

    public static CmisAtomFileSystemConfigBuilder getInstance() {
        return INSTANCE;
    }

    public void setXmlTargetField(FileSystemOptions opts, String field) {
        setParam(opts, PARAM_XML_TARGET_FIELD, field);
    }

    public String getXmlTargetField(FileSystemOptions opts) {
        return (String) getParam(opts, PARAM_XML_TARGET_FIELD);
    }

    public void setRepositoryId(FileSystemOptions opts, String repositoryId) {
        setParam(opts, PARAM_RESPOSITORY_ID, repositoryId);
    }

    public String getRepositoryId(FileSystemOptions opts) {
        return (String) getParam(opts, PARAM_RESPOSITORY_ID);
    }

    @Override
    protected Class<CmisAtomFileSystem> getConfigClass() {
        return CmisAtomFileSystem.class;
    }
}
