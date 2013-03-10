package com.norconex.collector.http.filter.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.meta.Metadata;
import com.norconex.collector.http.filter.AbstractOnMatchFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.OnMatch;
import com.norconex.collector.http.util.QuietConfigurationLoader;
/**
 * Accepts or rejects one or more HTTP header values using regular expression.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;filter class="com.norconex.collector.http.filter.impl.RegexHeaderFilter"
 *          onMatch="[include|exclude]" 
 *          caseSensitive="[false|true]" &gt;
 *          header="(name of header to match)"
 *      (regular expression of value to match)
 *  &lt;/filter&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class RegexHeaderFilter extends AbstractOnMatchFilter
        implements IHttpHeadersFilter, IXMLConfigurable {

    private static final long serialVersionUID = -8029862304058855686L;

    private boolean caseSensitive;
    private String header;
    private String regex;
    private Pattern pattern;

    public RegexHeaderFilter() {
        this(null, null, OnMatch.INCLUDE);
    }
    public RegexHeaderFilter(String header, String regex) {
        this(header, regex, OnMatch.INCLUDE);
    }
    public RegexHeaderFilter(String header, String regex, OnMatch onMatch) {
        this(header, regex, onMatch, false);
    }
    public RegexHeaderFilter(
            String header, String regex, 
            OnMatch onMatch, boolean caseSensitive) {
        super();
        setCaseSensitive(caseSensitive);
        setHeader(header);
        setOnMatch(onMatch);
        setRegex(regex);
    }
    
    public String getRegex() {
        return regex;
    }
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    public String getHeader() {
        return header;
    }
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    public void setHeader(String header) {
        this.header = header;
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
    public boolean acceptHeaders(String url, Metadata headers) {
        if (StringUtils.isBlank(regex)) {
            return getOnMatch() == OnMatch.INCLUDE;
        }

        Collection<String> values = headers.getPropertyValues(header);
        for (Object value : values) {
            String strVal = ObjectUtils.toString(value);
            if (pattern.matcher(strVal).matches()) {
                return getOnMatch() == OnMatch.INCLUDE;
            }
        }
        return getOnMatch() == OnMatch.EXCLUDE;
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = QuietConfigurationLoader.load(in);
        setHeader(xml.getString("[@header]"));
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
        builder.append("RegexHeaderFilter [header=")
                .append(header)
                .append(", regex=").append(regex)
                .append(", caseSensitive=").append(caseSensitive)
                .append(", onMatch=").append(getOnMatch()).append("]");
        return builder.toString();
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (caseSensitive ? 1231 : 1237);
        result = prime * result + ((header == null) ? 0 : header.hashCode());
        result = prime * result + ((regex == null) ? 0 : regex.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        RegexHeaderFilter other = (RegexHeaderFilter) obj;
        if (caseSensitive != other.caseSensitive)
            return false;
        if (header == null) {
            if (other.header != null)
                return false;
        } else if (!header.equals(other.header))
            return false;
        if (regex == null) {
            if (other.regex != null)
                return false;
        } else if (!regex.equals(other.regex))
            return false;
        return true;
    }
    
}

