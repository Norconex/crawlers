/* Copyright 2015 Norconex Inc.
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
package com.norconex.collector.http.crawler.event.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.ICrawler;
import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.core.crawler.event.ICrawlerEventListener;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * <p>
 * Store on file all URLs that were "fetched", along with their HTTP response
 * code, usually for reporting purposes (e.g. finding broken links). A short
 * summary of all HTTP status codes can be found 
 * <a href="http://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml">here</a>.
 * </p>
 * <h3>Filter by status codes</h3>
 * <p>
 * By default, the status of all fetched URLs are stored by this listener,
 * regardless what were those statuses.  This can generate very lengthy reports
 * on large crawls. If you are only interested in certain status codes, you can
 * listen only for those using the {@link #setStatusCodes(String)} method
 * or XML configuration equivalent. You specify the codes you want to listen
 * for as coma-separated values. Ranges are also supported: specify two range
 * values (both inclusive) separated by an hyphen.  For instance, if you want
 * to store all "bad" URLs, you can quickly specify all codes except 
 * 200 (OK) this way:
 * </p>
 * <pre>
 *   100-199,201-599</pre>
 *   
 * <h3>Output location</h3>
 * <p>
 * The generated report will be stored in the directory specified by 
 * using {@link #setOutputDir(String)}. By default, the file
 * generated will use this naming pattern:
 * </p>
 * <pre>
 *   urlstatuses-[crawlerId]-[timestamp].tsv
 * </pre>
 * <p>
 * The filename prefix can be changed from "urlstatuses-" to anything else
 * using {@link #setFileNamePrefix(String)}.
 * </p>
 * 
 * <h3>XML Configuration Usage</h3>
 * <pre>
 *  &lt;listener  
 *      class="ccom.norconex.collector.http.crawler.event.impl.URLStatusCrawlerEventListener"&gt;
 *      &lt;statusCodes&gt;(CSV list of status codes)&lt;/statusCodes&gt;
 *      &lt;outputDir&gt;(path to a directory of your choice)&lt;/outputDir&gt;
 *      &lt;fileNamePrefix&gt;(report file name prefix)&lt;/fileNamePrefix&gt;
 *  &lt;/listener&gt;
 * </pre>
 * @author Pascal Essiembre
 * @since 2.2.0
 */
public class URLStatusCrawlerEventListener 
        implements ICrawlerEventListener, IXMLConfigurable {

    public static final String DEFAULT_FILENAME_PREFIX = "urlstatuses-";
    
    private String statusCodes;
    private String outputDir;
    private String fileNamePrefix;
    
    // variables set when crawler starts/resumes
    private File outputFile;
    private final List<Integer> parsedCodes = new ArrayList<>();
    
    /**
     * Gets the status codes to listen for. Default is <code>null</code>
     * (listens for all status codes). 
     * @return status codes
     */
    public String getStatusCodes() {
        return statusCodes;
    }
    /**
     * Sets a coma-separated list of status codes to listen to. 
     * See class documentation for how to specify code ranges.
     * @param statusCodes the status codes to listen for
     */
    public void setStatusCodes(String statusCodes) {
        this.statusCodes = statusCodes;
    }

    /**
     * Gets the local directory where this listener report will be written.
     * @return directory path
     */
    public String getOutputDir() {
        return outputDir;
    }
    /**
     * Sets the local directory where this listener report will be written.
     * @param outputDir directory path
     */
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Gets the generated report file name prefix. See class documentation 
     * for default prefix.
     * @return file name prefix
     */
    public String getFileNamePrefix() {
        return fileNamePrefix;
    }
    /**
     * Sets the generated report file name prefix. 
     * @param fileNamePrefix file name prefix
     */
    public void setFileNamePrefix(String fileNamePrefix) {
        this.fileNamePrefix = fileNamePrefix;
    }
    
    @Override
    public void crawlerEvent(ICrawler crawler, CrawlerEvent event) {
        String type = event.getEventType();
        HttpCrawlData crawlData = (HttpCrawlData) event.getCrawlData();

        initializeOnStartOrResume(type, crawler.getId());
        
        if (event.getSubject() instanceof HttpFetchResponse) {
            HttpFetchResponse response = (HttpFetchResponse) event.getSubject();
            
            if (parsedCodes.isEmpty() 
                    || parsedCodes.contains(response.getStatusCode())) {
                writeLine(crawlData.getReferrerReference(),
                        crawlData.getReference(), 
                        Integer.toString(response.getStatusCode()),
                        response.getReasonPhrase(),
                        true);
            }
        }
    }
    
    private void initializeOnStartOrResume(String type, String crawlerId) {
        if (!CrawlerEvent.CRAWLER_STARTED.equals(type)
                && !CrawlerEvent.CRAWLER_RESUMED.equals(type)) {
            return;
        }

        // Create new file on crawler start/resume
        outputFile = new File(outputDir, fileNamePrefix
                + FileUtil.toSafeFileName(crawlerId)
                + "-" + System.currentTimeMillis() + ".tsv");
        try {
            FileUtil.createDirsForFile(outputFile);
        } catch (IOException e) {
            throw new CollectorException(
                    "Cannot create output directory for file: "
                            + outputFile, e);
        }
        writeLine("Referrer", "URL", "Status", "Reason", false);

        // Parse status codes
        if (StringUtils.isBlank(statusCodes)) {
            parsedCodes.clear();
            return;
        }
        String[] ranges = statusCodes.split("\\s*,\\s*");
        for (String range : ranges) {
            String[] endPoints = range.split("\\s*-\\s*");
            if (endPoints.length == 1) {
                parsedCodes.add(toInt(endPoints[0]));
            } else if (endPoints.length == 2) {
                int start = toInt(endPoints[0]);
                int end = toInt(endPoints[1]);
                if (start >= end) {
                    throw new IllegalArgumentException(
                            "Invalid statusCode range: " + range
                          + ". Start value must be higher than end value.");
                }
                while (start <= end) {
                    parsedCodes.add(start);
                    start++;
                }
            } else {
                throw new IllegalArgumentException(
                        "Invalid statusCode range: " + range);
            }
        }
    }
    
    private int toInt(String num) {
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The statusCodes attribute "
                    + "can only contain valid numbers. This number is invalid: "
                    + num);
        }
    }
    
    private void writeLine(
            String referrer, String url, String status, 
            String cause, boolean append) {
        try (FileWriter out = new FileWriter(outputFile, append)) {
            out.write(StringUtils.trimToEmpty(referrer));
            out.write('\t');
            out.write(StringUtils.trimToEmpty(url));
            out.write('\t');
            out.write(StringUtils.trimToEmpty(status));
            out.write('\t');
            out.write(StringUtils.trimToEmpty(cause));
            out.write('\n');
        } catch (IOException e) {
            throw new CollectorException(
                    "Cannot write link report to file: " + outputFile, e);
        }
    }
    
    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        setStatusCodes(xml.getString("statusCodes", getStatusCodes()));
        setOutputDir(xml.getString("outputDir", getOutputDir()));
        setFileNamePrefix(xml.getString("fileNamePrefix", getFileNamePrefix()));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeStartElement("listener");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeElementString("statusCodes", statusCodes);
            writer.writeElementString("outputDir", outputDir);
            writer.writeElementString("fileNamePrefix", fileNamePrefix);
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }        
    }


}
