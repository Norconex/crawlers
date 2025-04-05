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
package com.norconex.crawler.core.init;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.CrawlerContext;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

//TODO is this for "Command" initializers?  Before we can perform grid actions?
// If so, document it clearly and maybe move under .cmd.*?
@Slf4j
public class CrawlerInitializers implements Consumer<CrawlerContext> {

    private final Predicates<CrawlerContext> initializers;
    @Getter
    private final Function<CrawlerContext,
            ? extends CrawlerContext> contextAdapter;

    @Builder
    private CrawlerInitializers(
            @Singular
            @NonNull List<Predicate<CrawlerContext>> initializers,
            Function<CrawlerContext, ? extends CrawlerContext> contextAdapter) {
        this.initializers = new Predicates<>(initializers);
        this.contextAdapter = contextAdapter;
    }

    @Override
    public void accept(CrawlerContext context) {
        var ctx = contextAdapter != null
                ? contextAdapter.apply(context)
                : context;
        initializers.test(ctx);
    }
}
