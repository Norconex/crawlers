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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URLEncoder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import com.norconex.commons.lang.xml.XML;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@RequiredArgsConstructor
public class CmisAtomSession {

    private final CloseableHttpClient httpClient;
    private String endpointURL;
    private String repoId;
    private String repoName;
    private String objectByPathTemplate;
    private String queryTemplate;

    public XML getDocumentByPath(String path) throws FileSystemException {
        return getDocument(objectByPathTemplate.replace("{path}",
               URLEncoder.encode(path, UTF_8)));
    }
    public XML getDocument(String fullURL) throws FileSystemException {
        return new XML(new InputStreamReader(getStream(fullURL), UTF_8));
    }
    public InputStream getStream(String fullURL) throws FileSystemException {
        try {
            var resp = httpClient.execute(new HttpGet(fullURL));
            if (resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                var consumedContent = IOUtils.toString(
                        resp.getEntity().getContent(), UTF_8);
                LOG.debug("Could not consume HTTP content. Response content: "
                        + consumedContent);
                throw new IOException("Invalid HTTP response \""
                        +  resp.getStatusLine() + "\" from " + fullURL);
            }
            return resp.getEntity().getContent();
        } catch (UnsupportedOperationException | IOException e) {
            throw new FileSystemException(
                    "Could not get stream from " + fullURL, e);
        }
    }

    public void close() {
        if (httpClient instanceof CloseableHttpClient) {
            try {
                httpClient.close();
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Error closing CMIS Atom HTTP client", e);
            }
        }
    }
}
