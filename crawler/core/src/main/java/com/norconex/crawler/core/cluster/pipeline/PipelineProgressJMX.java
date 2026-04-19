/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster.pipeline;

import static javax.management.ObjectName.quote;

import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.session.CrawlContext;

/**
 * Registrar for the coordinator-only PipelineProgress MXBean.
 */
public final class PipelineProgressJMX {

    private PipelineProgressJMX() {
    }

    public static void register(CrawlContext ctx,
            PipelineManager manager, String pipelineId) {
        var mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            mbs.registerMBean(new PipelineProgressBean(manager, pipelineId),
                    objectName(ctx));
        } catch (MalformedObjectNameException
                | InstanceAlreadyExistsException
                | MBeanRegistrationException
                | NotCompliantMBeanException e) {
            throw new CrawlerException(e);
        }
    }

    public static void unregister(CrawlContext ctx) {
        var mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            var name = objectName(ctx);
            if (mbs.isRegistered(name)) {
                mbs.unregisterMBean(name);
            }
        } catch (MalformedObjectNameException
                | MBeanRegistrationException
                | InstanceNotFoundException e) {
            throw new CrawlerException(e);
        }
    }

    private static ObjectName objectName(CrawlContext ctx)
            throws MalformedObjectNameException {
        return new ObjectName(
                ctx.getClass().getName()
                        + ":type=PipelineProgress"
                        + ",crawler=" + quote(ctx.getId())
                        + ",scope=Cluster");
    }
}
