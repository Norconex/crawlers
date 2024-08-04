/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.crawler.core.monitor;

import static javax.management.ObjectName.quote;

import java.lang.management.ManagementFactory;
import java.util.Objects;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.session.CrawlSessionException;

public final class CrawlerMonitorJMX {

    private CrawlerMonitorJMX() {
    }
    public static void register(Crawler crawler) {
        Objects.requireNonNull(crawler, "'crawler' must not be null.");
        var mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            mbs.registerMBean(crawler.getMonitor(), objectName(crawler));
        } catch (MalformedObjectNameException
                | InstanceAlreadyExistsException
                | MBeanRegistrationException
                | NotCompliantMBeanException e) {
           throw new CrawlSessionException(e);
        }
    }
    public static void unregister(Crawler crawler) {
        Objects.requireNonNull(crawler, "'crawler' must not be null.");
        var mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            var objectName = objectName(crawler);
            if (mbs.isRegistered(objectName)) {
                mbs.unregisterMBean(objectName);
            }
        } catch (MalformedObjectNameException
                | MBeanRegistrationException
                | InstanceNotFoundException e) {
           throw new CrawlSessionException(e);
        }
    }
    private static ObjectName objectName(Crawler crawler)
            throws MalformedObjectNameException {
        return new ObjectName(crawler.getClass().getName()
                + ":type=Metrics"
                + ",session=" + quote(crawler.getCrawlSession().getId())
                + ",crawler=" + quote(crawler.getId()));
    }
}
