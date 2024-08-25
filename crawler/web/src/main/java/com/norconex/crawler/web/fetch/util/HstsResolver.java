/* Copyright 2021-2024 Norconex Inc.
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

import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpHead;

import com.google.common.net.InternetDomainName;
import com.norconex.commons.lang.url.URLNormalizer;
import com.norconex.crawler.web.doc.WebCrawlDocContext;

import lombok.extern.slf4j.Slf4j;

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
 * you should rely on {@link URLNormalizer#secureScheme}
 * instead.
 * </p>
 * @since 3.0.0
 */
@Slf4j
public final class HstsResolver {

    private static final String HSTS_HEADER = "Strict-Transport-Security";

    private enum HstsSupport {
        NO, DOMAIN_ONLY, INCLUDE_SUBDOMAINS
    }

    private static final Map<String, HstsSupport> DOMAIN_HSTS =
            new HashMap<>();

    private HstsResolver() {
    }

    public static synchronized void clearCache() {
        DOMAIN_HSTS.clear();
    }

    public static void resolve(
            HttpClient httpClient,
            WebCrawlDocContext docRecord
    ) {

        // The idea: "public" suffixes are "effective" top-level domains
        // under which new domains can be registered. When considering a root
        // domain name for a site (and checking for HSTS), we consider that
        // domain to be the first level before a public suffix.
        // Google Guava is being well-maintained, we rely on it instead of
        // loading and caching the list from https://publicsuffix.org ourselves.
        // We only perform the public suffix resolution if a valid domain.
        // See: https://github.com/Norconex/collector-http/issues/785

        var rootDomain = docRecord.getReference().replaceFirst(
                "(?i)^https?://([^/\\?#]+).*", "$1"
        );
        var isSubdomain = false;
        if (InternetDomainName.isValid(rootDomain)) {
            var dn = InternetDomainName.from(rootDomain);

            if (dn.isTopPrivateDomain()) {
                dn = dn.topPrivateDomain();
                rootDomain = dn.toString();
            } else if (!dn.isPublicSuffix()) {
                isSubdomain = true;
            }
            // Plan B, just in case:
        } else if (StringUtils.countMatches(rootDomain, '.') > 1) {
            isSubdomain = true;
            rootDomain =
                    rootDomain.replaceFirst("^.*\\.([^\\.]+\\.[^\\.]+)$", "$1");
        }

        // If secure, cache HSTS support settings
        if (startsWithIgnoreCase(docRecord.getReference(), "https:")) {
            resolveHstsSupport(httpClient, rootDomain);
        } else {
            applyHstsSupport(docRecord, rootDomain, isSubdomain);
        }
    }

    private static synchronized void applyHstsSupport(
            WebCrawlDocContext docRecord, String domain, boolean isSubdomain
    ) {
        var support = DOMAIN_HSTS.getOrDefault(domain, HstsSupport.NO);
        if (support == HstsSupport.INCLUDE_SUBDOMAINS
                || (support == HstsSupport.DOMAIN_ONLY && !isSubdomain)) {
            LOG.debug("""
                    Converting protocol to https according to\s\
                    domain Strict-Transport-Security (HSTS) settings\s\
                    for effective top-level domain: {}
                    """, domain);
            docRecord.setOriginalReference(docRecord.getReference());
            docRecord.setReference(
                    docRecord.getReference().replaceFirst(
                            "(?i)^http://", "https://"
                    )
            );
        }
    }

    private static synchronized void resolveHstsSupport(
            HttpClient httpClient, String domain
    ) {

        var exceptionMsg = """
                Attempt to verify if the site supports\s\
                Strict-Transport-Security (HSTS) failed for domain\s\
                "%s". We'll assumume HSTS is not supported for\s\
                all URLs on that domain
                """.formatted(domain);

        DOMAIN_HSTS.computeIfAbsent(domain, d -> {
            var req = new HttpHead("https://" + d);
            try {
                // case-insensitive look-up
                var header = httpClient.execute(
                        req, response -> Stream
                                .of(response.getHeaders())
                                .filter(
                                        h -> HSTS_HEADER
                                                .equalsIgnoreCase(h.getName())
                                )
                                .findAny()
                                .orElse(null)
                );
                if (header == null) {
                    LOG.info(
                            "No Strict-Transport-Security (HSTS) support "
                                    + "detected for domain \"{}\".",
                            domain
                    );
                    return HstsSupport.NO;
                }
                if (header.getValue().matches(
                        "(?i).*\\bincludeSubDomains\\b.*"
                )) {
                    LOG.info(
                            "Strict-Transport-Security (HSTS) support "
                                    + "detected for domain \"{}\" and its sub-domains.",
                            domain
                    );
                    return HstsSupport.INCLUDE_SUBDOMAINS;
                }
                LOG.info(
                        "Strict-Transport-Security (HSTS) support "
                                + "detected for domain \"{}\" (sub-domains excluded).",
                        domain
                );
                return HstsSupport.DOMAIN_ONLY;
            } catch (IOException e) {
                LOG.warn(exceptionMsg, e);
                return HstsSupport.NO;
            }
        });
    }
}
