/* Copyright 2019-2023 Norconex Inc.
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

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;

import com.norconex.commons.lang.xml.XML;

import lombok.extern.slf4j.Slf4j;

/**
 * A file in an CMIS file system.
 */
@Slf4j
public class CmisAtomFileObject extends AbstractFileObject<CmisAtomFileSystem> {

    // no paging given Apache Commons VFS does not seem to support it.
    private static final int MAX_ITEMS = 1000000;

    private static final String PROP_OBJECT_TYPE_ID = "cmis:objectTypeId";
    private static final String PROP_BASE_TYPE_ID = "cmis:baseTypeId";
    private static final String PROP_LAST_MODIFICATION_DATE =
            "cmis:lastModificationDate";
    private static final String PROP_CONTENT_STREAM_LENGTH =
            "cmis:contentStreamLength";

    private XML document;

    protected CmisAtomFileObject(
            AbstractFileName name, CmisAtomFileSystem fileSystem) {
        super(name, fileSystem);
    }

    public CmisAtomSession getSession() {
        return getFileSystem().getSession();
    }

    /**
     * Attaches this file object to its file resource.
     */
    @Override
    protected void doAttach() throws Exception {
        // Defer creation of the CmisObject to here
        if (document == null) {
            document = createDocument(getName());
        }
    }

    @Override
    protected void doDetach() throws Exception {
        document = null;
    }

    @Override
    public CmisAtomFileSystem getFileSystem() {
        return (CmisAtomFileSystem) super.getFileSystem();
    }

    public XML getDocument() {
        return document;
    }

    private XML createDocument(final FileName fileName)
            throws FileSystemException {
        return getSession().getDocumentByPath(fileName.getPath());
    }

    public String toXmlString() {
        return document.toString();
    }

    /**
     * Determines the type of the file, returns null if the file does not exist.
     */
    @Override
    protected FileType doGetType() throws Exception {
        var type = getPropertyValue(PROP_OBJECT_TYPE_ID);
        if ("cmis:folder".equalsIgnoreCase(type)) {
            return FileType.FOLDER;
        }
        if ("cmis:document".equalsIgnoreCase(type)) {
            return FileType.FILE;
        }

        type = getPropertyValue(PROP_BASE_TYPE_ID);
        if ("cmis:folder".equalsIgnoreCase(type)) {
            return FileType.FOLDER;
        }
        if ("cmis:document".equalsIgnoreCase(type)) {
            return FileType.FILE;
        }

        LOG.info("Unsupported file type: {} File: {}", type, getName());
        return FileType.IMAGINARY;
    }

    /**
     * Lists the children of the file. Is only called if {@link #doGetType}
     * returns {@link FileType#FOLDER}.
     */
    @Override
    protected String[] doListChildren() throws Exception {
        var session = getSession();

        List<String> children = new ArrayList<>();

        var childrenURL = document.getString(
                "/entry/link[@rel='down' and "
              + "@type='application/atom+xml;type=feed']/@href");

        if (StringUtils.isBlank(childrenURL)) {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
        childrenURL += childrenURL.contains("?") ? "&" : "?";
        childrenURL += "&includeAllowableActions=false"
                + "&includeRelationships=none"
                + "&renditionFilter=cmis%3Anone&includePathSegment=true"
                + "&maxItems=" + MAX_ITEMS + "&skipCount=0&filter=cmis%3Anone";

        var childrenDoc = session.getDocument(childrenURL);

        if (childrenDoc.getInteger("/feed/numItems", -1) > MAX_ITEMS) {
            LOG.warn("TOO many items under {}. Will only process the first {}.",
                    getName().getPathDecoded(), MAX_ITEMS);
        }
        var xmlList = childrenDoc.getXMLList("/feed/entry/pathSegment/text()");
        xmlList.forEach(x -> {
            var fileName = x.getString(".");
            if (StringUtils.isNotBlank(fileName)) {
                children.add(fileName);
            }
        });
        return children.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }

    /**
     * Returns the size of the file content (in bytes).
     */
    @Override
    protected long doGetContentSize() throws Exception {
        return NumberUtils.toLong(
                getPropertyValue(PROP_CONTENT_STREAM_LENGTH), -1);
    }

    /**
     * Returns the last modified time of this file.
     */
    @Override
    protected long doGetLastModifiedTime() throws Exception {
        var date = getPropertyValue(PROP_LAST_MODIFICATION_DATE);
        if (StringUtils.isNotEmpty(date)) {
            return ZonedDateTime.parse(date).toInstant().toEpochMilli();
        }
        return -1;
    }

    /**
     * Creates an input stream to read the file content from.
     */
    @Override
    protected InputStream doGetInputStream() throws Exception {
        var contentURL = document.getString("/entry/content/@src");
        if (StringUtils.isBlank(contentURL)) {
            LOG.warn("Content URL could not be found for {}.", getName());
            return new NullInputStream(0);
        }

        if (doGetContentSize() <= 0) {
            LOG.debug("Document has no content: {}.", getName());
            return new NullInputStream(0);
        }
        return getSession().getStream(contentURL);
    }

    private String getPropertyValue(String propertyDefId) {
        return document.getString("""
            /entry/object/properties/\
            *[starts-with(local-name(), 'property')]\
            [@propertyDefinitionId='%s']/value/text()"""
                .formatted(propertyDefId));
    }
}
