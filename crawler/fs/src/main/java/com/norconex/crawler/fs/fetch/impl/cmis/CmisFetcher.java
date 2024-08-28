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
package com.norconex.crawler.fs.fetch.impl.cmis;

import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;

import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

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
@ToString
@EqualsAndHashCode
public class CmisFetcher extends AbstractAuthVfsFetcher<CmisFetcherConfig> {

    private static final String CMIS_PREFIX = CrawlDocMetadata.PREFIX + "cmis.";

    @Getter
    private final CmisFetcherConfig configuration = new CmisFetcherConfig();

    @Override
    protected void fetchMetadata(CrawlDoc doc, @NonNull FileObject fileObject)
            throws FileSystemException {
        super.fetchMetadata(doc, fileObject);

        if (fileObject instanceof CmisAtomFileObject cmisObject) {
            var ctx = new Context(cmisObject, doc.getMetadata());

            if (ctx.document != null) {
                fetchCoreMeta(ctx);
                fetchProperties(ctx);
                if (!configuration.isAclDisabled()) {
                    fetchAcl(ctx);
                }
            }
        }
    }

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(fetchRequest, "cmis:");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        var cfg = CmisAtomFileSystemConfigBuilder.getInstance();
        cfg.setRepositoryId(opts, configuration.getRepositoryId());
        cfg.setXmlTargetField(opts, configuration.getXmlTargetField());
    }

    private void fetchCoreMeta(Context ctx) {
        ctx.addMetaXpath("author.name", "/entry/author/name/text()");
        ctx.addMetaXpath("id", "/entry/id/text()");
        ctx.addMetaXpath("published", "/entry/published/text()");
        ctx.addMetaXpath("title", "/entry/title/text()");
        ctx.addMetaXpath("edited", "/entry/edited/text()");
        ctx.addMetaXpath("updated", "/entry/updated/text()");
        ctx.addMetaXpath("content", "/entry/content/@src");

        ctx.addMeta("repository.id", ctx.session.getRepoId());
        ctx.addMeta("repository.name", ctx.session.getRepoName());

        var xTargetField = ctx.cfg.getXmlTargetField(ctx.vfsOptions);
        if (StringUtils.isNotBlank(xTargetField)) {
            ctx.metadata.add(xTargetField, ctx.fileObject.toXmlString());
        }
    }

    private void fetchProperties(Context ctx) {
        var propXmlList = ctx.document.getXMLList(
                "/entry/object/properties//"
                        + "*[starts-with(local-name(), 'property')]"
        );
        for (XML propXml : propXmlList) {
            var propId = propXml.getString("@propertyDefinitionId");
            if (StringUtils.isBlank(propId)) {
                propId = "undefined_property";
            }
            ctx.addMeta(
                    "property." + propId,
                    propXml.getString("value/text()")
            );
        }
    }

    private void fetchAcl(Context ctx) {
        var permissions = new Properties();
        var permXmlList = ctx.document.getXMLList(
                "/entry/object/acl/permission"
        );
        for (XML permXml : permXmlList) {
            var principalId = permXml.getString("principal/principalId");
            permXml.getStringList("permission").forEach(p -> {
                if (StringUtils.isNotBlank(p)) {
                    permissions.add("acl." + p, principalId);
                }
            });
        }

        for (Entry<String, List<String>> en : permissions.entrySet()) {
            for (String val : en.getValue()) {
                ctx.addMeta(en.getKey(), val);
            }
        }
    }

    private static class Context {
        private final FileSystemOptions vfsOptions;
        private final XML document;
        private final Properties metadata;
        private final CmisAtomSession session;
        private final CmisAtomFileSystemConfigBuilder cfg =
                CmisAtomFileSystemConfigBuilder.getInstance();
        private final CmisAtomFileObject fileObject;

        public Context(CmisAtomFileObject vfsFile, Properties metadata) {
            fileObject = vfsFile;
            session = vfsFile.getSession();
            document = vfsFile.getDocument();
            vfsOptions = vfsFile.getFileSystem().getFileSystemOptions();
            this.metadata = metadata;
        }

        private void addMeta(String key, Object value) {
            var val = Objects.toString(value, null);
            if (StringUtils.isBlank(val)) {
                return;
            }
            metadata.add(CMIS_PREFIX + key, val);
        }

        private void addMetaXpath(String key, String exp) {
            addMeta(key, document.getString(exp));
        }
    }
}
