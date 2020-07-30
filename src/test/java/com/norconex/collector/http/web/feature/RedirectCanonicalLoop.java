/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web.feature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;

/**
 * The tail of redirects should be kept as metadata so implementors
 * can know where documents came from. This test starts with a
 * regular HTTP redirect.
 * @author Pascal Essiembre
 */
public class RedirectCanonicalLoop extends CanonicalRedirectLoop {

    private static final Logger LOG =
            LoggerFactory.getLogger(RedirectCanonicalLoop.class);

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg)
            throws Exception {
        cfg.setIgnoreCanonicalLinks(false);

        if (isFirstRun()) {
            LOG.debug("Testing redirect.");
            cfg.setStartURLs(cfg.getStartURLs().get(0) + "?type=redirect");
        } else {
            LOG.debug("Testing canonical.");
            cfg.setStartURLs(cfg.getStartURLs().get(0) + "?type=canonical");
        }
    }
}
