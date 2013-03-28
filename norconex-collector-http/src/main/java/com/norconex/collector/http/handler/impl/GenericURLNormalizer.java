package com.norconex.collector.http.handler.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.handler.IURLNormalizer;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.url.URLNormalizer;

/**
 * <p>
 * Generic implementation of {@link IURLNormalizer} that should satisfy
 * most URL normalization needs.  This impleemntation relies on 
 * {@link URLNormalizer}.  Please refer to it for complete documentation and 
 * examples. 
 * </p>
 * <p>
 * By default, this class
 * applies these <a href="http://tools.ietf.org/html/rfc3986">RFC 3986</a>
 * normalizations: 
 * </p>
 * <ul>
 *   <li>Converting the scheme and host to lower case</li>
 *   <li>Capitalizing letters in escape sequences</li>
 *   <li>Decoding percent-encoded unreserved characters</li>
 *   <li>Removing the default port</li>
 * </ul>
 * <p>
 * To overwrite this default, you have to specify a new list of normalizations
 * to apply, via the XXXXX method, or via XML configuration.  Each 
 * normalizations is identified by a code name.  The following is the
 * complete code name list for supported normalizations.  Click on any code
 * name to get a full description from {@link URLNormalizer}:
 * </p>
 * <ul>
 *   <li>{@link URLNormalizer#lowerCaseSchemeHost() lowerCaseSchemeHost}</li>
 *   <li>{@link URLNormalizer#upperCaseEscapeSequence() upperCaseEscapeSequence}</li>
 *   <li>{@link URLNormalizer#decodeUnreservedCharacters() decodeUnreservedCharacters}</li>
 *   <li>{@link URLNormalizer#removeDefaultPort() removeDefaultPort}</li>
 *   <li>{@link URLNormalizer#addTrailingSlash() addTrailingSlash}</li>
 *   <li>{@link URLNormalizer#removeDotSegments() removeDotSegments}</li>
 *   <li>{@link URLNormalizer#removeDirectoryIndex() removeDirectoryIndex}</li>
 *   <li>{@link URLNormalizer#removeFragment() removeFragment}</li>
 *   <li>{@link URLNormalizer#replaceIPWithDomainName() replaceIPWithDomainName}</li>
 *   <li>{@link URLNormalizer#unsecureScheme() unsecureScheme}</li>
 *   <li>{@link URLNormalizer#secureScheme() secureScheme}</li>
 *   <li>{@link URLNormalizer#removeDuplicateSlashes() removeDuplicateSlashes}</li>
 *   <li>{@link URLNormalizer#removeWWW() removeWWW}</li>
 *   <li>{@link URLNormalizer#addWWW() addWWW}</li>
 *   <li>{@link URLNormalizer#sortQueryParameters() sortQueryParameters}</li>
 *   <li>{@link URLNormalizer#removeEmptyParameters() removeEmptyParameters}</li>
 *   <li>{@link URLNormalizer#removeTrailingQuestionMark() removeTrailingQuestionMark}</li>
 *   <li>{@link URLNormalizer#removeSessionIds() removeSessionIds}</li> 
 * </ul>
 * <p>
 *   In addition, this class allows you to specify any number of URL 
 *   value replacements using regular expressions.
 * </p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;urlNormalizer class="com.norconex.collector.http.handler.impl.GenericURLNormalizer"&gt;
 *    &lt;normalizations&gt;
 *      (normalization code names, coma separated) 
 *    &lt;/normalizations&gt;
 *    &lt;replacements&gt;
 *      &lt;replace&gt;
 *         &lt;match&gt;(regex pattern to match)&lt;/match&gt;
 *         &lt;replacement&gt;(optional replacement value, default to blank)&lt;/replacement&gt;
 *      &lt;/replace&gt;
 *      (... repeat replace tag  as needed ...)
 *    &lt;/replacements&gt;
 *  &lt;/urlNormalizer&gt;
 * </pre>
 * <p>Example:</p>
 * <pre>
 *  &lt;urlNormalizer class="com.norconex.collector.http.handler.impl.GenericURLNormalizer"&gt;
 *    &lt;normalizations&gt;
 *      lowerCaseSchemeHost, upperCaseEscapeSequence, removeDefaultPort, 
 *      removeDotSegments, removeDirectoryIndex, removeFragment, addWWW 
 *    &lt;/normalizations&gt;
 *    &lt;replacements&gt;
 *      &lt;replace&gt;&lt;match&gt;&amp;view=print&lt;/match&gt;&lt;/replace&gt;
 *      &lt;replace&gt;
 *         &lt;match&gt;(&amp;type=)(summary)&lt;/match&gt;
 *         &lt;replacement&gt;$1full&lt;/replacement&gt;
 *      &lt;/replace&gt;
 *    &lt;/replacements&gt;
 *  &lt;/urlNormalizer&gt;
 * @author Pascal Essiembre
 */
public class GenericURLNormalizer 
        implements IURLNormalizer, IXMLConfigurable {

    private static final long serialVersionUID = 3847717776792688065L;
    private static final Logger LOG = LogManager.getLogger(
            GenericURLNormalizer.class);
  
    public enum Normalization {
        lowerCaseSchemeHost,
        upperCaseEscapeSequence, 
        decodeUnreservedCharacters, 
        removeDefaultPort, 
        addTrailingSlash, 
        removeDotSegments, 
        removeDirectoryIndex, 
        removeFragment, 
        replaceIPWithDomainName, 
        unsecureScheme, 
        secureScheme, 
        removeDuplicateSlashes, 
        removeWWW, 
        addWWW, 
        sortQueryParameters, 
        removeEmptyParameters, 
        removeTrailingQuestionMark, 
        removeSessionIds 
    };
    
    
    public final List<Normalization> normalizations = 
            new ArrayList<Normalization>();
    public final List<Replace> replaces = new ArrayList<Replace>();
    
    public String normalizeURL(String url) {
        URLNormalizer normalizer = new URLNormalizer(url);
        for (Normalization n : normalizations) {
            try {
                MethodUtils.invokeExactMethod(
                        normalizer, n.toString(), (Object[]) null);
            } catch (Exception e) {
                LOG.error("Could not apply normalization \"" + n + "\".", e);
            }
        }
        return normalizer.toString();
    }
    
    public Normalization[] getNormalizations() {
        return normalizations.toArray(new Normalization[] {});
    }
    public void setNormalizations(Normalization... normalizations) {
        this.normalizations.clear();
        this.normalizations.addAll(Arrays.asList(normalizations));
    }

    public Replace[] getReplaces() {
        return replaces.toArray(new Replace[] {});
    }
    public void setReplaces(Replace... replaces) {
        this.replaces.clear();
        this.replaces.addAll(Arrays.asList(replaces));
    }
    

    @Override
    public void loadFromXML(Reader in) {
        
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        String xmlNorms = xml.getString("normalizations");
        if (StringUtils.isNotBlank(xmlNorms)) {
            normalizations.clear();
            for (String norm : StringUtils.split(xmlNorms, ',')) {
                try {
                    normalizations.add(Normalization.valueOf(norm.trim()));
                } catch (Exception e) {
                    LOG.error("Invalid normalization: \"" + norm + "\".", e);
                }
            }
        }
        List<HierarchicalConfiguration> xmlReplaces = 
                xml.configurationsAt("replacements.replace");
        if (!replaces.isEmpty()) {
            replaces.clear();
        }
        for (HierarchicalConfiguration node : xmlReplaces) {
             String match = node.getString("match", "");
             String replacement = node.getString("replacement", "");
             replaces.add(new Replace(match, replacement));
        }
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("urlNormalizer");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeStartElement("normalizations");
            writer.writeCharacters(StringUtils.join(normalizations, ","));
            writer.writeEndElement();
            writer.writeStartElement("replacements");
            for (Replace replace : replaces) {
                writer.writeStartElement("replace");
                writer.writeStartElement("match");
                writer.writeCharacters(replace.getMatch());
                writer.writeEndElement();
                writer.writeStartElement("replacement");
                writer.writeCharacters(replace.getReplacement());
                writer.writeEndElement();
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((normalizations == null) ? 0 : normalizations.hashCode());
        result = prime * result
                + ((replaces == null) ? 0 : replaces.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GenericURLNormalizer other = (GenericURLNormalizer) obj;
        if (normalizations == null) {
            if (other.normalizations != null)
                return false;
        } else if (!normalizations.equals(other.normalizations))
            return false;
        if (replaces == null) {
            if (other.replaces != null)
                return false;
        } else if (!replaces.equals(other.replaces))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "GenericURLNormalizer [normalizations=" + normalizations
                + ", replaces=" + replaces + "]";
    }

    public class Replace {
        private final String match;
        private final String replacement;
        public Replace(String match) {
            super();
            this.match = match;
            this.replacement = "";
        }
        public Replace(String match, String replacement) {
            super();
            this.match = match;
            this.replacement = replacement;
        }
        public String getMatch() {
            return match;
        }
        public String getReplacement() {
            return replacement;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((match == null) ? 0 : match.hashCode());
            result = prime * result
                    + ((replacement == null) ? 0 : replacement.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Replace other = (Replace) obj;
            if (match == null) {
                if (other.match != null)
                    return false;
            } else if (!match.equals(other.match))
                return false;
            if (replacement == null) {
                if (other.replacement != null)
                    return false;
            } else if (!replacement.equals(other.replacement))
                return false;
            return true;
        }
        @Override
        public String toString() {
            return "Replace [match=" + match + ", replacement=" + replacement
                    + "]";
        }
    }
}
