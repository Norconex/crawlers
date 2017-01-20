/* Copyright 2010-2016 Norconex Inc.
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
package com.norconex.collector.http.url.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.url.URLNormalizer;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * <p>
 * Generic implementation of {@link IURLNormalizer} that should satisfy
 * most URL normalization needs.  This implementation relies on 
 * {@link URLNormalizer}.  Please refer to it for complete documentation and 
 * examples. 
 * </p>
 * <p>
 * Since 2.3.0, this class is in effect by default. To skip its usage, you
 * can explicitly set the URL Normalizer to <code>null</code> in the 
 * {@link HttpCrawlerConfig}, or you can disable it using 
 * {@link #setDisabled(boolean)}.
 * </p>
 * <p>
 * By default, this class removes the URL fragment and applies these 
 * <a href="http://tools.ietf.org/html/rfc3986">RFC 3986</a>
 * normalizations: 
 * </p>
 * <ul>
 *   <li>Converting the scheme and host to lower case</li>
 *   <li>Capitalizing letters in escape sequences</li>
 *   <li>Decoding percent-encoded unreserved characters</li>
 *   <li>Removing the default port</li>
 *   <li>Encoding non-URI characters (since 2.3.0)</li>
 * </ul>
 * <p>
 * To overwrite this default, you have to specify a new list of normalizations
 * to apply, via the {@link #setNormalizations(Normalization...)} method, 
 * or via XML configuration.  Each 
 * normalizations is identified by a code name.  The following is the
 * complete code name list for supported normalizations.  Click on any code
 * name to get a full description from {@link URLNormalizer}:
 * </p>
 * <ul>
 *   <li>{@link URLNormalizer#addDirectoryTrailingSlash() addDirectoryTrailingSlash} (since 2.6.0)</li>
 *   <li>{@link URLNormalizer#addDomainTrailingSlash() addDomainTrailingSlash} (since 2.6.1)</li>
 *   <li>{@link URLNormalizer#addWWW() addWWW}</li>
 *   <li>{@link URLNormalizer#decodeUnreservedCharacters() decodeUnreservedCharacters}</li>
 *   <li>{@link URLNormalizer#encodeNonURICharacters() encodeNonURICharacters} (since 2.3.0)</li>
 *   <li>{@link URLNormalizer#encodeSpaces() encodeSpaces} (since 2.3.0)</li>
 *   <li>{@link URLNormalizer#lowerCaseSchemeHost() lowerCaseSchemeHost}</li>
 *   <li>{@link URLNormalizer#removeDefaultPort() removeDefaultPort}</li>
 *   <li>{@link URLNormalizer#removeDirectoryIndex() removeDirectoryIndex}</li>
 *   <li>{@link URLNormalizer#removeDotSegments() removeDotSegments}</li>
 *   <li>{@link URLNormalizer#removeDuplicateSlashes() removeDuplicateSlashes}</li>
 *   <li>{@link URLNormalizer#removeEmptyParameters() removeEmptyParameters}</li>
 *   <li>{@link URLNormalizer#removeFragment() removeFragment}</li>
 *   <li>{@link URLNormalizer#removeSessionIds() removeSessionIds}</li> 
 *   <li>{@link URLNormalizer#removeTrailingQuestionMark() removeTrailingQuestionMark}</li>
 *   <li>{@link URLNormalizer#removeTrailingSlash() removeTrailingSlash} (since 2.6.0)</li>
 *   <li>{@link URLNormalizer#removeWWW() removeWWW}</li>
 *   <li>{@link URLNormalizer#replaceIPWithDomainName() replaceIPWithDomainName}</li>
 *   <li>{@link URLNormalizer#secureScheme() secureScheme}</li>
 *   <li>{@link URLNormalizer#sortQueryParameters() sortQueryParameters}</li>
 *   <li>{@link URLNormalizer#unsecureScheme() unsecureScheme}</li>
 *   <li>{@link URLNormalizer#upperCaseEscapeSequence() upperCaseEscapeSequence}</li>
 * </ul>
 * <p>
 *   In addition, this class allows you to specify any number of URL 
 *   value replacements using regular expressions.
 * </p>
 * 
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;urlNormalizer
 *      class="com.norconex.collector.http.url.impl.GenericURLNormalizer"
 *      disabled="[false|true]"&gt;
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
 * <h3>Example:</h3>
 * <p>
 * The following adds a normalization to add "www." to URL domains when
 * missing, to the default set of normalizations. It also add custom
 * URL "search-and-replace" to remove any "&amp;view=print" strings from URLs
 * as well as replace "&amp;type=summary" with "&amp;type=full". 
 * </p>
 * <pre>
 *  &lt;urlNormalizer class="com.norconex.collector.http.url.impl.GenericURLNormalizer"&gt;
 *    &lt;normalizations&gt;
 *        removeFragment, lowerCaseSchemeHost, upperCaseEscapeSequence,
 *        decodeUnreservedCharacters, removeDefaultPort, 
 *        encodeNonURICharacters, addWWW 
 *    &lt;/normalizations&gt;
 *    &lt;replacements&gt;
 *      &lt;replace&gt;&lt;match&gt;&amp;amp;view=print&lt;/match&gt;&lt;/replace&gt;
 *      &lt;replace&gt;
 *         &lt;match&gt;(&amp;amp;type=)(summary)&lt;/match&gt;
 *         &lt;replacement&gt;$1full&lt;/replacement&gt;
 *      &lt;/replace&gt;
 *    &lt;/replacements&gt;
 *  &lt;/urlNormalizer&gt;
 * </pre>
 * @author Pascal Essiembre
 * @see Pattern
 */
public class GenericURLNormalizer implements IURLNormalizer, IXMLConfigurable {

    private static final Logger LOG = LogManager.getLogger(
            GenericURLNormalizer.class);
  
    public enum Normalization {
        addDirectoryTrailingSlash,
        addDomainTrailingSlash,
        /**
         * @deprecated Since 1.11.0, use {@link #addDirectoryTrailingSlash}
         */
        @Deprecated
        addTrailingSlash, 
        addWWW, 
        decodeUnreservedCharacters, 
        encodeNonURICharacters,
        encodeSpaces,
        lowerCaseSchemeHost,
        removeDefaultPort, 
        removeDirectoryIndex, 
        removeDotSegments, 
        removeDuplicateSlashes, 
        removeEmptyParameters, 
        removeFragment, 
        removeSessionIds,
        removeTrailingQuestionMark, 
        removeTrailingSlash, 
        removeWWW, 
        replaceIPWithDomainName, 
        secureScheme, 
        sortQueryParameters, 
        unsecureScheme, 
        upperCaseEscapeSequence, 
    }
    
    
    private final List<Normalization> normalizations = 
            new ArrayList<Normalization>();
    private final List<Replace> replaces = new ArrayList<Replace>();
    private boolean disabled;
    
    public GenericURLNormalizer() {
        super();
        setNormalizations(
                Normalization.removeFragment,
                Normalization.lowerCaseSchemeHost,
                Normalization.upperCaseEscapeSequence,
                Normalization.decodeUnreservedCharacters,
                Normalization.removeDefaultPort,
                Normalization.encodeNonURICharacters);
    }

    @Override
    public String normalizeURL(String url) {
        if (disabled) {
            return url;
        }
        
        URLNormalizer normalizer = new URLNormalizer(url);
        for (Normalization n : normalizations) {
            try {
                MethodUtils.invokeExactMethod(
                        normalizer, n.toString(), (Object[]) null);
            } catch (Exception e) {
                LOG.error("Could not apply normalization \"" + n + "\".", e);
            }
        }
        String normedURL = normalizer.toString();
        for (Replace replace : replaces) {
            if (replace == null || StringUtils.isBlank(replace.getMatch())) {
                continue;
            }
            String replacement = replace.getReplacement();
            if (StringUtils.isBlank(replacement)) {
                replacement = StringUtils.EMPTY;
            }
            normedURL = normedURL.replaceAll(replace.getMatch(), replacement);
        }
        return normedURL;
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
    
    /**
     * Whether this URL Normalizer is disabled or not.
     * @return <code>true</code> if disabled
     * @since 2.3.0
     */
    public boolean isDisabled() {
        return disabled;
    }
    /**
     * Sets whether this URL Normalizer is disabled or not.
     * @param disabled <code>true</code> if disabled
     * @since 2.3.0
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public void loadFromXML(Reader in) {
        
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        
        setDisabled(xml.getBoolean("[@disabled]", disabled));
        
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
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out); 
            writer.writeStartElement("urlNormalizer");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttributeBoolean("disabled", isDisabled());
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
    public boolean equals(final Object other) {
        if (!(other instanceof GenericURLNormalizer)) {
            return false;
        }
        GenericURLNormalizer castOther = (GenericURLNormalizer) other;
        return new EqualsBuilder()
                .append(disabled, castOther.disabled)
                .append(normalizations, castOther.normalizations)
                .append(replaces, castOther.replaces)
                .isEquals();
    }
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(disabled)
                .append(normalizations)
                .append(replaces)
                .toHashCode();
    }
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("disabled", disabled)
                .append("normalizations", normalizations)
                .append("replaces", replaces)
                .toString();
    }        

    public static class Replace {
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
        public boolean equals(final Object other) {
            if (!(other instanceof Replace)) {
                return false;
            }
            Replace castOther = (Replace) other;
            return new EqualsBuilder()
                    .append(match, castOther.match)
                    .append(replacement, castOther.replacement)
                    .isEquals();
        }
        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .append(match)
                    .append(replacement)
                    .toHashCode();
        }
        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("match", match)
                    .append("replacement", replacement)
                    .toString();
        }        
    }
}
