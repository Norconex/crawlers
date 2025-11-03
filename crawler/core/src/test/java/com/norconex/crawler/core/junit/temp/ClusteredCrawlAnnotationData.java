/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.junit.temp;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.extension.ExtensionContext;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.junit.crawler.ClusteredCrawlTest;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ClusteredCrawlAnnotationData {
    private int nodes = 2;
    private Class<Supplier<CrawlDriver>> driverSupplierClass;
    private String[] extraArgs = { "start" };

    public static ClusteredCrawlAnnotationData from(ExtensionContext ctx) {
        new ClusteredCrawlAnnotationData();

        // // Check class
        // if (ctx.getTestClass().isPresent()) {
        //     var annot = ctx.getTestClass().get()
        //             .getAnnotation(ClusteredCrawlTest.class);
        //     if ((annot != null) && !annot.driverSupplierClass()
        //             .equals(ClusteredCrawlTest.UNDEFINED_SUPPLIER.class)) {
        //         //data.driverSupplierClass(annot.driverSupplierClass());
        //     }
        // }

        return null;

    }

    // Looks for a @ClusteredCrawlTest annotation on the current test method
    // first, else, moves to class.
    public static Optional<ClusteredCrawlTest>
            findAnnotation(ExtensionContext ctx) {
        //TODO Maybe have class-level one act as "defaults" and methods only
        // overwrite some, returning a new merged construct?

        // Check method first
        if (ctx.getElement().isPresent()) {
            var ann = ctx.getElement().get()
                    .getAnnotation(ClusteredCrawlTest.class);
            if (ann != null) {
                return Optional.of(ann);
            }
        }
        // Then check class
        if (ctx.getTestClass().isPresent()) {
            var ann = ctx.getTestClass().get()
                    .getAnnotation(ClusteredCrawlTest.class);
            if (ann != null) {
                return Optional.of(ann);
            }
        }
        return Optional.empty();
    }
}
