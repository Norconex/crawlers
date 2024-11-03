/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite;

import org.apache.ignite.Ignite;

import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridServices;
import com.norconex.crawler.core.grid.GridStorage;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * Uses Apache Ignite to run the crawler on a cluster (grid).
 * </p>
 */
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public class IgniteGrid implements Grid {

    //    @NonNull
    //    private final IgniteGridInstance igniteGridInstance;
    @NonNull
    @Getter(value = AccessLevel.PACKAGE)
    private final Ignite ignite;

    @Override
    public GridStorage storage() {
        return new IgniteGridStorage(this);
    }

    @Override
    public GridCompute compute() {
        return new IgniteGridCompute(this);
    }

    @Override
    public GridServices services() {
        return new IgniteGridServices(this);
    }

    @Override
    public void close() {
        ExceptionSwallower.swallow(() -> {
            //TODO fine another location where to get rid of service?
            // because an exception is thrown when already taken care of
            ignite.services().cancel(IgniteGridKeys.CONTEXT_SERVICE);
        });

        //        var serviceName = IgniteGridKeys.CONTEXT_SERVICE;
        //        var services = ignite.services();
        //        var serviceDescriptor = services.serviceDescriptors()
        //                .stream()
        //                .filter(sd -> serviceName.equals(sd.name()))
        //                .findAny();
        //
        //        if (serviceDescriptor.isPresent()) {
        //            System.err.println(
        //                    "Service is active. Cancelling service: " + serviceName);
        //
        //            services.cancel(IgniteGridKeys.CONTEXT_SERVICE);
        //
        //            //            services.cancel(serviceName);
        //        } else {
        //            System.err.println("Service is not active: " + serviceName);
        //        }
        //        System.err.println("XXX About to cancel service.");
        //        ignite.services().cancel(IgniteGridKeys.CONTEXT_SERVICE);
        //        System.err.println("XXX service cancelled.");
        //        Ignition.stopAll(false);
        //        igniteGridInstance.get().close();
        // NOOP: Nothing to close
    }
}
