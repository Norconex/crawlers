/* Copyright 2017-2018 Norconex Inc.
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
package com.norconex.collector.http.fetch.impl;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.client.HttpClient;

import com.norconex.collector.http.HttpCollectorEvent;
import com.norconex.commons.lang.event.IEventListener;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Starts and stops an HTTP proxy that uses Apache {@link HttpClient} to
 * make HTTP requests.  To be used by external applications, such as PhantomJS.
 * </p>
 * <p>
 * The proxy server started uses a random port by default.  You can
 * specify which port to use with {@link #setPort(int)}.
 * </p>
 * <h3>XML configuration usage:</h3>
 *
 * <pre>
 *  &lt;httpcollector id="MyHttpCollector"&gt;
 *    ...
 *    &lt;collectorListeners&gt;
 *      &lt;listener
 *          class="com.norconex.collector.http.fetch.impl.HttpClientProxyCollectorListener"
 *          port="(Optional port. Default is 0, which means random.)" /&gt;
 *    &lt;/collectorListeners&gt;
 *    ...
 *  &lt;/httpcollector&gt;
 * </pre>
 *
 * @see PhantomJSDocumentFetcher
 * @author Pascal Essiembre
 * @since 2.7.0
 */

//TODO deprecate??

public class HttpClientProxyCollectorListener
        implements IEventListener<HttpCollectorEvent>, IXMLConfigurable {

    private int port;

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public void accept(HttpCollectorEvent event) {
        if (event.is(HttpCollectorEvent.COLLECTOR_STARTED)) {
            HttpClientProxy.start(port);
        } else if (event.is(HttpCollectorEvent.COLLECTOR_ENDED)) {
            HttpClientProxy.stop();
        }
    }

    @Override
    public void loadFromXML(XML xml) {
        setPort(xml.getInteger("@port", port));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("port", port);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
