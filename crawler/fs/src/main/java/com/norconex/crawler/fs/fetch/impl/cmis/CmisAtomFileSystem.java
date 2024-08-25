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

import java.util.Collection;

import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileSystem;

/**
 * An CMIS Atom file system.
 */
public class CmisAtomFileSystem extends AbstractFileSystem {

    private final CmisAtomSession session;

    protected CmisAtomFileSystem(
            final FileName rootName,
            final FileObject parentLayer,
            final FileSystemOptions fileSystemOptions,
            final CmisAtomSession session
    ) {
        super(rootName, parentLayer, fileSystemOptions);
        this.session = session;
    }

    public CmisAtomSession getSession() {
        return session;
    }

    /**
     * Creates a file object.
     */
    @Override
    protected FileObject createFile(final AbstractFileName name)
            throws FileSystemException {
        return new CmisAtomFileObject(name, this);
    }

    /**
     * Returns the capabilities of this file system.
     */
    @Override
    protected void addCapabilities(final Collection<Capability> caps) {
        caps.addAll(CmisAtomFileProvider.CAPABILITIES);
    }

    @Override
    protected void doCloseCommunicationLink() {
        session.close();
    }
}
