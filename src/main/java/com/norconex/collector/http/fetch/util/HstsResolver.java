/* Copyright 2021 Norconex Inc.
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
package com.norconex.collector.http.fetch.util;

import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.url.impl.GenericURLNormalizer.Normalization;

/**
 * <p>
 * Class handling HSTS support for servers supporting it.
 * Upon encountering the first secure URL from a site, it caches whether the URL
 * root domain returns an HTTP response containing a
 * "Strict-Transport-Security" entry and if it includes sub-domains.
 * Any non secure URL on that same site will be converted to https if requested
 * by the HSTS server directive (if any).
 * </p>
 * <p>
 * To always convert "http" to "https" regardless of a site support for HSTS,
 * you should rely on {@link Normalization#secureScheme}
 * instead.
 * </p>
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public final class HstsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(
            HstsResolver.class);

    private static final String HSTS_HEADER = "Strict-Transport-Security";

    private enum HstsSupport { NO, DOMAIN_ONLY, INCLUDE_SUBDOMAINS }

    private static final Map<String, HstsSupport> DOMAIN_HSTS = new HashMap<>();

    private HstsResolver() { }

    public static void resolve(HttpClient httpClient, HttpDocInfo docInfo) {

        String domain = docInfo.getReference().replaceFirst(
                "(?i)^https?://([^/\\?#]+).*", "$1");
        boolean isSubdomain = false;
        // if there are sub-domains, get just the domain
        if (StringUtils.countMatches(domain, '.') > 1) {
            isSubdomain = true;
            domain = domain.replaceFirst(".*\\.([^\\.]+\\.[^\\.]+)$", "$1");
        }

        // If secure, cache HSTS support settings
        if (startsWithIgnoreCase(docInfo.getReference(), "https:")) {
            resolveHstsSupport(httpClient, /* ctx, */domain);
        } else {
            applyHstsSupport(docInfo, domain, isSubdomain);
        }
    }

    private static synchronized void applyHstsSupport(
            HttpDocInfo docInfo, String domain, boolean isSubdomain) {
        HstsSupport support = DOMAIN_HSTS.getOrDefault(domain, HstsSupport.NO);
        if (support == HstsSupport.INCLUDE_SUBDOMAINS
                || (support == HstsSupport.DOMAIN_ONLY && !isSubdomain)) {
            LOG.debug("Converting protocol to https according to "
                    + "domain Strict-Transport-Security (HSTS) settings "
                    + "for URL: {}", docInfo.getReference());
            docInfo.setOriginalReference(docInfo.getReference());
            docInfo.setReference(docInfo.getReference().replaceFirst(
                    "(?i)^http://", "https://"));
        }
    }

    private static synchronized void resolveHstsSupport(
            HttpClient httpClient, String domain) {

        DOMAIN_HSTS.computeIfAbsent(domain, d -> {
            HttpHead req = new HttpHead("https://" + d);
            try {
                // case-insensitive look-up
                Header header = Stream.of(
                        httpClient.execute(req).getAllHeaders())
                    .filter(h -> HSTS_HEADER.equalsIgnoreCase(h.getName()))
                    .findAny()
                    .orElse(null);
                if (header == null) {
                    LOG.info("No Strict-Transport-Security (HSTS) support "
                            + "detected for domain \"{}\".", domain);
                    return HstsSupport.NO;
                }
                if (header.getValue().matches("(?i).*\\bincludeSubDomains\\b.*")) {
                    LOG.info("Strict-Transport-Security (HSTS) support "
                            + "detected for domain \"{}\" and its sub-domains.",
                            domain);
                    return HstsSupport.INCLUDE_SUBDOMAINS;
                }
                LOG.info("Strict-Transport-Security (HSTS) support "
                        + "detected for domain \"{}\" (sub-domains excluded).",
                        domain);
                return HstsSupport.DOMAIN_ONLY;
            } catch (IOException e) {
                LOG.warn("Attempt to verify if the site supports "
                        + "Strict-Transport-Security (HSTS) failed for domain "
                        + "\"{}\". We'll assumume HSTS is not supported for "
                        + "all URLs on that domain.",
                        domain, e);
                return HstsSupport.NO;
            }
        });
    }
}
