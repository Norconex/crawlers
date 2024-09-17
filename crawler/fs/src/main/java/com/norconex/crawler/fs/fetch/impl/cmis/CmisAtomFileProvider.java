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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.UserAuthenticationData;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.AbstractLayeredFileProvider;
import org.apache.commons.vfs2.provider.LayeredFileName;
import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;

import com.norconex.commons.lang.xml.Xml;

import lombok.extern.slf4j.Slf4j;

/**
 * A provider for CMIS Atom file systems.
 */
@Slf4j
public class CmisAtomFileProvider extends AbstractLayeredFileProvider {

    private static final UserAuthenticationData.Type[] AUTHENTICATOR_TYPES = {
            UserAuthenticationData.USERNAME,
            UserAuthenticationData.PASSWORD
    };
    private static final String REPOSITORY_XPATH =
            "/service/workspace/repositoryInfo"; //NOSONAR

    static final Collection<Capability> CAPABILITIES =
            Collections.unmodifiableCollection(
                    Arrays.asList(
                            Capability.GET_TYPE,
                            Capability.GET_LAST_MODIFIED,
                            Capability.LIST_CHILDREN,
                            Capability.READ_CONTENT,
                            Capability.URI));

    @Override
    protected FileSystem doCreateFileSystem(
            String scheme, FileObject file, FileSystemOptions fileSystemOptions)
            throws FileSystemException {
        var session = createSession(fileSystemOptions, file);

        final FileName rootName = new LayeredFileName(
                scheme, file.getName(),
                FileName.ROOT_PATH, FileType.FOLDER);

        return new CmisAtomFileSystem(
                rootName, file, fileSystemOptions, session);
    }

    @Override
    public Collection<Capability> getCapabilities() {
        return CAPABILITIES;
    }

    private CmisAtomSession createSession(
            FileSystemOptions opts, FileObject file)
            throws FileSystemException {
        LOG.info("Creating new CMIS Atom connection.");

        var httpBuilder = HttpClientBuilder.create();

        resolveDefaultHeaders(httpBuilder);
        resolveAuth(httpBuilder, opts);
        updateHttpClient(httpBuilder);

        var session = new CmisAtomSession(httpBuilder.build());

        resolveRepo(session, opts, file);

        return session;
    }

    protected void updateHttpClient(HttpClientBuilder httpClientBuilder) {
        //NOOP
    }

    private void resolveDefaultHeaders(HttpClientBuilder httpBuilder) {
        List<Header> headers = new ArrayList<>();
        headers.add(
                new BasicHeader("Accept", "application/atom+xml;type=feed"));
        httpBuilder.setDefaultHeaders(headers);
    }

    private void resolveAuth(
            HttpClientBuilder httpBuilder, FileSystemOptions opts) {
        var auth = DefaultFileSystemConfigBuilder
                .getInstance().getUserAuthenticator(opts);
        if (auth != null) {
            var data = auth.requestAuthentication(AUTHENTICATOR_TYPES);
            if (data == null) {
                return;
            }
            CredentialsProvider cp = new BasicCredentialsProvider();
            cp.setCredentials(
                    AuthScope.ANY, new UsernamePasswordCredentials(
                            new String(
                                    data.getData(
                                            UserAuthenticationData.USERNAME)),
                            new String(
                                    data.getData(
                                            UserAuthenticationData.PASSWORD))));
            httpBuilder.setDefaultCredentialsProvider(cp);
        }
    }

    private void resolveRepo(
            CmisAtomSession session, FileSystemOptions opts, FileObject file)
            throws FileSystemException {
        var cmis =
                CmisAtomFileSystemConfigBuilder.getInstance();
        var atomURL = StringUtils.substringBefore(file.toString(), "!");
        session.setEndpointURL(atomURL);
        LOG.info("CMIS Atom endpoint URL: " + atomURL);

        var repoXPath = REPOSITORY_XPATH;
        var repoId = cmis.getRepositoryId(opts);
        var doc = session.getDocument(atomURL);
        Xml repoNode;
        if (StringUtils.isNotBlank(repoId)) {
            LOG.info("Using CMIS repository matching id: " + repoId);
            repoNode = doc.getXML(
                    "%s[repositoryId=\"%s\"]".formatted(repoXPath, repoId));
        } else {
            LOG.info("Using first CMIS repository found.");
            repoNode = doc.getXML(repoXPath + "[1]");
        }
        if (repoNode == null) {
            throw new FileSystemException("No repository found.");
        }

        session.setRepoId(repoNode.getString("repositoryId"));
        session.setRepoName(repoNode.getString("repositoryName"));
        session.setObjectByPathTemplate(
                getTemplateURL(doc, "objectbypath"));
        session.setQueryTemplate(getTemplateURL(doc, "query"));
    }

    private String getTemplateURL(Xml doc, String type) {
        // We always use some of the same defaults, so we can already replace
        // parts of the URL
        var tmplURL = doc.getString(
                "/service/workspace/uritemplate[type='%s']/template/text()"
                        .formatted(type));
        Map<String, Object> vars = new HashMap<>();
        vars.put("filter", "");
        vars.put("includeAllowableActions", true);
        vars.put("includeACL", true);
        vars.put("includePolicyIds", true);
        vars.put("includeRelationships", "none");
        vars.put("renditionFilter", "cmis%3Anone");
        return StringSubstitutor.replace(tmplURL, vars, "{", "}");
    }
}
