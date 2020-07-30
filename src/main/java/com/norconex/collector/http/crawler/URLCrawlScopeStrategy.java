/* Copyright 2015-2019 Norconex Inc.
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
package com.norconex.collector.http.crawler;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.url.HttpURL;

/**
 * <p>By default a crawler will try to follow all links it discovers. You can
 * define your own filters to limit the scope of the pages being crawled.
 * When you have multiple URLs defined as start URLs, it can be tricky to
 * perform global filtering that apply to each URLs without causing
 * URL filtering conflicts.  This class offers an easy way to address
 * a frequent URL filtering need: to "stay on site". That is,
 * when following a page and extracting URLs found in it, make sure to
 * only keep URLs that are on the same site as the page URL we are on.
 * </p>
 * <p>
 * By default this class does not request to stay on a site.
 * </p>
 * @author Pascal Essiembre
 * @since 2.3.0
 */
//TODO make this an interface so developers can provide their own?
public class URLCrawlScopeStrategy {


    private static final Logger LOG =
            LoggerFactory.getLogger(URLCrawlScopeStrategy.class);

    private boolean stayOnDomain;
    private boolean includeSubdomains;
    private boolean stayOnPort;
    private boolean stayOnProtocol = false;

    /**
     * Whether the crawler should always stay on the same domain name as
     * the domain for each URL specified as a start URL.  By default (false)
     * the crawler will try follow any discovered links not otherwise rejected
     * by other settings (like regular filtering rules you may have).
     * @return <code>true</code> if the crawler should stay on a domain
     */
    public boolean isStayOnDomain() {
        return stayOnDomain;
    }
    /**
     * Sets whether the crawler should always stay on the same domain name as
     * the domain for each URL specified as a start URL.
     * @param stayOnDomain <code>true</code> for the crawler to stay on domain
     */
    public void setStayOnDomain(boolean stayOnDomain) {
        this.stayOnDomain = stayOnDomain;
    }

    /**
     * Gets whether sub-domains are considered to be the same as a URL domain.
     * Only applicable when "stayOnDomain" is <code>true</code>.
     * @return <code>true</code> if including sub-domains
     * @since 2.9.0
     */
    public boolean isIncludeSubdomains() {
        return includeSubdomains;
    }
    /**
     * Sets whether sub-domains are considered to be the same as a URL domain.
     * Only applicable when "stayOnDomain" is <code>true</code>.
     * @param includeSubdomains <code>true</code> to include sub-domains
     * @since 2.9.0
     */
    public void setIncludeSubdomains(boolean includeSubdomains) {
        this.includeSubdomains = includeSubdomains;
    }

    /**
     * Gets whether the crawler should always stay on the same port as
     * the port for each URL specified as a start URL.  By default (false)
     * the crawler will try follow any discovered links not otherwise rejected
     * by other settings (like regular filtering rules you may have).
     * @return <code>true</code> if the crawler should stay on a port
     */
    public boolean isStayOnPort() {
        return stayOnPort;
    }
    /**
     * Sets whether the crawler should always stay on the same port as
     * the port for each URL specified as a start URL.
     * @param stayOnPort <code>true</code> for the crawler to stay on port
     */
    public void setStayOnPort(boolean stayOnPort) {
        this.stayOnPort = stayOnPort;
    }

    /**
     * Whether the crawler should always stay on the same protocol as
     * the protocol for each URL specified as a start URL.  By default (false)
     * the crawler will try follow any discovered links not otherwise rejected
     * by other settings (like regular filtering rules you may have).
     * @return <code>true</code> if the crawler should stay on protocol
     */
    public boolean isStayOnProtocol() {
        return stayOnProtocol;
    }
    /**
     * Sets whether the crawler should always stay on the same protocol as
     * the protocol for each URL specified as a start URL.
     * @param stayOnProtocol
     *        <code>true</code> for the crawler to stay on protocol
     */
    public void setStayOnProtocol(boolean stayOnProtocol) {
        this.stayOnProtocol = stayOnProtocol;
    }


    public boolean isInScope(String inScopeURL, String candidateURL) {
        // if not specifying any scope, candidate URL is good
        if (!stayOnProtocol && !stayOnDomain && !stayOnPort) {
            return true;
        }

        try {
            HttpURL inScope = new HttpURL(inScopeURL);
            HttpURL candidate;
            if (candidateURL.startsWith("//")) {
                candidate = new HttpURL(
                        inScope.getProtocol() + ':' + candidateURL);
            } else {
                candidate = new HttpURL(candidateURL);
            }
            if (stayOnProtocol && !inScope.getProtocol().equalsIgnoreCase(
                    candidate.getProtocol())) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Rejected protocol for: {}", candidateURL);
                }
                return false;
            }
            if (stayOnDomain
                    && !isOnDomain(inScope.getHost(), candidate.getHost())) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Rejected domain for: {}", candidateURL);
                }
                return false;
            }
            if (stayOnPort && inScope.getPort() != candidate.getPort()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Rejected port for: {}", candidateURL);
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            LOG.debug("Unsupported URL \"{}\".", candidateURL, e);
            return false;
        }
    }

    private boolean isOnDomain(String inScope, String candidate) {
        // if domains are the same, we are good. Covers zero depth too.
        if (inScope.equalsIgnoreCase(candidate)) {
            return true;
        }

        // if accepting sub-domains, check if it ends the same.
        return includeSubdomains
                && StringUtils.endsWithIgnoreCase(candidate, "." + inScope);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
