/* Copyright 2017 Norconex Inc.
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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.http.client.HttpClient;

import com.norconex.collector.core.ICollector;
import com.norconex.collector.core.ICollectorLifeCycleListener;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

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
public class HttpClientProxyCollectorListener 
        implements ICollectorLifeCycleListener, IXMLConfigurable {

    private int port;

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
    
    @Override
    public void onCollectorStart(ICollector collector) {
        HttpClientProxy.start(port);
    }
    @Override
    public void onCollectorFinish(ICollector collector) {
        HttpClientProxy.stop();
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        setPort(xml.getInt("[@port]", getPort()));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {        
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);         
            writer.writeStartElement("listener");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttributeInteger("port", getPort());
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }        
    }
}
