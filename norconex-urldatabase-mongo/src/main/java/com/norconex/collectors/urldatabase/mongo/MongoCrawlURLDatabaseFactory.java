/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collectors.urldatabase.mongo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.ICrawlURLDatabase;
import com.norconex.collector.http.db.ICrawlURLDatabaseFactory;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;

public class MongoCrawlURLDatabaseFactory implements ICrawlURLDatabaseFactory,
        IXMLConfigurable {

    private static final long serialVersionUID = 2798011257427173733L;

    private int port = 27017;
    private String host = "localhost";
    private String dbName;

    @Override
    public ICrawlURLDatabase createCrawlURLDatabase(HttpCrawlerConfig config,
            boolean resume) {
        return new MongoCrawlURLDatabase(config, resume, port, host, dbName);
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        port = xml.getInt("port", port);
        host = xml.getString("host", host);
        dbName = xml.getString("dbname", dbName);
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);

            writer.writeStartElement("port");
            writer.writeCharacters(String.valueOf(port));
            writer.writeEndElement();

            writer.writeStartElement("host");
            writer.writeCharacters(host);
            writer.writeEndElement();

            if (dbName != null && dbName.length() > 0) {
                writer.writeStartElement("dbname");
                writer.writeCharacters(dbName);
                writer.writeEndElement();
            }

            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
}
