package com.norconex.collector.http.filter.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;

import com.norconex.commons.lang.meta.Metadata;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.filter.AbstractOnMatchFilter;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.filter.OnMatch;
import com.norconex.collector.http.util.QuietConfigurationLoader;
/**
 * Filters URL based on a regular expression.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;filter class="com.norconex.collector.http.filter.impl.RegexURLFilter"
 *          exclude="[false|true]" 
 *          caseSensitive="[false|true]" &gt;
 *      (regular expression)
 *  &lt;/filter&gt;
 * </pre>
 * @author Pascal Essiembre
 */
@SuppressWarnings("nls")
public class RegexURLFilter extends AbstractOnMatchFilter implements 
        IURLFilter, 
        IHttpDocumentFilter,
        IHttpHeadersFilter,
        IXMLConfigurable {

    private static final long serialVersionUID = -8029862304058855686L;

    private boolean caseSensitive;
    private String regex;
    private Pattern pattern;

    public RegexURLFilter() {
        this(null, OnMatch.INCLUDE);
    }
    public RegexURLFilter(String regex) {
        this(regex, OnMatch.INCLUDE);
    }
    public RegexURLFilter(String regex, OnMatch onMatch) {
        this(regex, onMatch, false);
    }
    public RegexURLFilter(
            String regex, OnMatch onMatch, boolean caseSensitive) {
        super();
        setOnMatch(onMatch);
        setCaseSensitive(caseSensitive);
        setRegex(regex);
    }
    
    /**
     * @return the regex
     */
    public String getRegex() {
        return regex;
    }
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    public void setRegex(String regex) {
        this.regex = regex;
        if (regex != null) {
            if (caseSensitive) {
                this.pattern = Pattern.compile(regex);
            } else {
                this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            }
        } else {
            this.pattern = Pattern.compile(".*");
        }
    }

    @Override
    public boolean acceptURL(String url) {
        boolean isInclude = getOnMatch() == OnMatch.INCLUDE;;  
        if (StringUtils.isBlank(regex)) {
            return isInclude;
        }
        boolean matches = pattern.matcher(url).matches();
        return matches && isInclude || !matches && !isInclude;
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = QuietConfigurationLoader.load(in);
        setRegex(xml.getString(""));
        setOnMatch(getOnMatch(xml));
        setCaseSensitive(xml.getBoolean("[@caseSensitive]", false));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("filter");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttribute("onMatch",
                    getOnMatch().toString().toLowerCase());
            writer.writeAttribute("caseSensitive", 
                    Boolean.toString(caseSensitive));
            writer.writeCharacters(regex == null ? "" : regex);
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RegexURLFilter [regex=")
                .append(regex)
                .append(", caseSensitive=").append(caseSensitive)
                .append(", onMatch=").append(getOnMatch()).append("]");
        return builder.toString();
    }
    @Override
    public boolean acceptDocument(HttpDocument document) {
        return acceptURL(document.getUrl());
    }
    @Override
    public boolean acceptHeaders(String url, Metadata headers) {
        return acceptURL(url);
    }
}

