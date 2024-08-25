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

import static org.assertj.core.api.Assertions.assertThatException;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class CmisAtomSessionTest {

    @Mock
    private CloseableHttpClient httpClient;
    @Mock
    private CloseableHttpResponse httpResponse;

    @Test
    void test() throws ClientProtocolException, IOException {
        when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 500, "Too bad")
        );
        when(httpResponse.getEntity()).thenReturn(
                new StringEntity("Really too bad")
        );

        var cmis = new CmisAtomSession(httpClient);
        assertThatException().isThrownBy(() -> cmis.getStream("badone"));
    }
}
