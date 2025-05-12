/* Copyright 2021-2025 Norconex Inc.
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
package com.norconex.crawler.core.metrics;

import static javax.management.ObjectName.quote;

import java.lang.management.ManagementFactory;
import java.util.Objects;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.session.CrawlContext;

/**
 * Offers useful methods for registering and unregistering MXBean to JMX.
 */
public final class CrawlerMetricsJMX {

    private CrawlerMetricsJMX() {
    }

    public static void register(CrawlContext crawlContext) {
        Objects.requireNonNull(crawlContext, "'crawler' must not be null.");
        var mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            mbs.registerMBean(crawlContext.getMetrics(),
                    objectName(crawlContext));
        } catch (MalformedObjectNameException
                | InstanceAlreadyExistsException
                | MBeanRegistrationException
                | NotCompliantMBeanException e) {
            throw new CrawlerException(e);
        }
    }

    public static void unregister(CrawlContext crawlContext) {
        Objects.requireNonNull(crawlContext, "'crawler' must not be null.");
        var mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            var objectName = objectName(crawlContext);
            if (mbs.isRegistered(objectName)) {
                mbs.unregisterMBean(objectName);
            }
        } catch (MalformedObjectNameException
                | MBeanRegistrationException
                | InstanceNotFoundException e) {
            throw new CrawlerException(e);
        }
    }

    private static ObjectName objectName(CrawlContext crawlContext)
            throws MalformedObjectNameException {
        return new ObjectName(
                crawlContext.getClass().getName()
                        + ":type=Metrics"
                        + ",crawler=" + quote(crawlContext.getId()));
    }
}
