/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.crawler.web.fetch.util;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.tika.utils.CharsetUtils;

import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Provide redirect URLs by grabbing them from the HTTP Response
 * <code>Location</code> header value. The URL is made absolute and
 * an attempt is made to fix possible character encoding issues.</p>
 *
 * <p>
 * The <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a>
 * specification mentions that the <code>Location</code> header
 * should contain a URI as defined by the
 * <a href="http://www.ietf.org/rfc/rfc1630.txt">RFC 1630</a> specification.
 * The later requires that a URI be 7-bit ASCII with any special characters
 * URL encoded.
 * Some redirect URLs do not conform to that so we apply the following logic
 * in an attempt to fix them:
 * </p>
 * <ul>
 *   <li>
 *     Does the URL contains only ASCII characters (code points &lt;= 128)?
 *     <ul>
 *       <li>
 *         Yes: No attempt to fix it is made.
 *       </li>
 *       <li>
 *         No: Does the HTTP response specify a character encoding in the
 *         <code>Content-Type</code> header?
 *         <ul>
 *           <li>
 *             Yes: Treat URL as being encoded in that character encoding.
 *           </li>
 *           <li>
 *             No: Is the fallback character encoding set?
 *             <ul>
 *               <li>
 *                 Yes: Treat URL as being encoded with the fallback character
 *                 encoding.
 *               </li>
 *               <li>
 *                 No: Treat URL as being encoded with UTF-8.
 *               </li>
 *             </ul>
 *           </li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * {@nx.xml.usage
 * <redirectURLProvider
 *     class="com.norconex.crawler.web.redirect.impl.GenericRedirectURLProvider"
 *     fallbackCharset="(character encoding)" />
 * }
 *
 * {@nx.xml.example
 * <pre>
 * <redirectURLProvider fallbackCharset="ISO-8859-1" />
 * }
 * <p>
 * The above example sets the default character encoding to be "ISO-8859-1"
 * when it could not be detected.
 * </p>
 *
 * @since 2.4.0
 */
@Slf4j
@Data
public class GenericRedirectURLProvider
        implements RedirectURLProvider, XMLConfigurable {

    public static final String DEFAULT_FALLBACK_CHARSET =
            StandardCharsets.UTF_8.toString();

    private static final int ASCII_MAX_CODEPOINT = 128;

    private String fallbackCharset = DEFAULT_FALLBACK_CHARSET;

    @Override
    public String provideRedirectURL(HttpRequest request,
            HttpResponse response, HttpContext context) {
        var currentReq = (HttpRequest) context.getAttribute(
                HttpCoreContext.HTTP_REQUEST);
        String originalURL = null;
        try {
            originalURL = currentReq.getUri().toString();
        } catch (URISyntaxException e) {
            LOG.error("Could not provide redirect URL.", e);
            return null;
        }

        //--- Location ---
        var hl = response.getLastHeader(HttpHeaders.LOCATION);
        if (hl == null) {
            //TODO should throw exception instead?
            LOG.error("Redirect detected to a null Location for: {}",
                    originalURL);
            return null;
        }
        var redirectLocation = hl.getValue();

        //--- Charset ---
        String charset = null;
        var hc = response.getLastHeader("Content-Type");
        if (hc != null) {
            var contentType = hc.getValue();
            if (contentType.contains(";")) {
                charset = StringUtils.substringAfterLast(
                        contentType, "charset=");
            }
        }
        if (StringUtils.isBlank(charset)) {
            charset = fallbackCharset;
        }

        //--- Build/fix redirect URL ---
        var targetURL = HttpURL.toAbsolute(originalURL, redirectLocation);
        targetURL = resolveRedirectURL(targetURL, charset);

        if (LOG.isDebugEnabled()) {
            LOG.debug("URL redirect: {} -> {}", originalURL, targetURL);
        }
        return targetURL;
    }

    //TODO is there value in moving this method to somewhere re-usable?
    private String resolveRedirectURL(
            final String redirectURL, final String nonAsciiCharset) {

        var url = redirectURL;

        // Is string containing only ASCII as it should?
        var isAscii = true;
        final var length = url.length();
        for (var offset = 0; offset < length; ) {
           final var codepoint = url.codePointAt(offset);
           if (codepoint > ASCII_MAX_CODEPOINT) {
               isAscii = false;
               break;
           }
           offset += Character.charCount(codepoint);
        }
        if (isAscii) {
            return url;
        }
        LOG.warn("""
            Redirect URI made of 7-bit clean ASCII.\s\
            It probably is not encoded properly.\s\
            Will try to fix. Redirect URL: {}""", redirectURL);

        // try to fix if non ascii charset is non UTF8.
        if (StringUtils.isNotBlank(nonAsciiCharset)) {
            var charset = CharsetUtils.clean(nonAsciiCharset);
            if (!StandardCharsets.UTF_8.toString().equals(charset)) {
                try {
                    return new String(url.getBytes(charset));
                } catch (UnsupportedEncodingException e) {
                    LOG.warn("Could not fix badly encoded URL with charset "
                            + "\"{}\". Redirect URL: {}",
                            charset, redirectURL, e);
                }
            }
        }

        return new String(url.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void loadFromXML(XML xml) {
        setFallbackCharset(xml.getString("@fallbackCharset", fallbackCharset));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("fallbackCharset", fallbackCharset);
    }
}
