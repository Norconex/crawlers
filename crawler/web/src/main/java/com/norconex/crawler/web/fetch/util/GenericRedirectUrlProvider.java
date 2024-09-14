/* Copyright 2015-2024 Norconex Inc.
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

import static com.norconex.crawler.web.fetch.util.GenericRedirectUrlProviderConfig.DEFAULT_FALLBACK_CHARSET;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.tika.utils.CharsetUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.url.HttpURL;

import lombok.Data;
import lombok.Getter;
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
 * @since 2.4.0
 */
@Slf4j
@Data
public class GenericRedirectUrlProvider implements
        RedirectUrlProvider, Configurable<GenericRedirectUrlProviderConfig> {

    private static final int ASCII_MAX_CODEPOINT = 128;

    @Getter
    private final GenericRedirectUrlProviderConfig configuration =
            new GenericRedirectUrlProviderConfig();

    @Override
    public String provideRedirectURL(
            HttpRequest request,
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

        //--- Build/fix redirect URL ---
        var targetURL = HttpURL.toAbsolute(originalURL, redirectLocation);
        targetURL = resolveRedirectURL(response, targetURL);

        if (LOG.isDebugEnabled()) {
            LOG.debug("URL redirect: {} -> {}", originalURL, targetURL);
        }
        return targetURL;
    }

    //MAYBE: is there value in moving this method to somewhere re-usable?
    private String resolveRedirectURL(
            HttpResponse response, String redirectURL) {

        var url = redirectURL;

        // Is string containing only ASCII as it should?
        var isAscii = true;
        final var length = url.length();
        var offset = 0;
        while (offset < length) {
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
                Redirect URI not made of 7-bit clean ASCII.\s\
                It probably is not encoded properly.\s\
                Will try to fix. Redirect URL: {}""", redirectURL);

        // try to fix if non ascii charset is non UTF8.
        return new String(url.getBytes(resolveCharset(response, redirectURL)));
    }

    // Detect charset from response header or use fallback
    private Charset resolveCharset(HttpResponse response, String redirectUrl) {
        return ofNullable(response.getLastHeader("Content-Type"))
                .map(Header::getValue)
                .filter(ct -> ct.contains(";"))
                .map(ct -> trimToNull(substringAfterLast(ct, "charset=")))
                .map(chset -> {
                    try {
                        return CharsetUtils.forName(chset);
                    } catch (RuntimeException e) {
                        LOG.warn("""
                            Could not fix badly encoded URL with charset \
                            "{}". Redirect URL: "{}". Will use fallback.""",
                                chset, redirectUrl);
                        return null;
                    }
                }).orElseGet(() -> ofNullable(
                        configuration.getFallbackCharset())
                                .orElse(DEFAULT_FALLBACK_CHARSET));
    }
}
