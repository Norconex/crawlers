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

import java.io.StringReader;

import org.apache.ignite.Ignition;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerSpecProvider;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IgniteGridCrawlerContextServiceImpl
        implements IgniteGridCrawlerContextService {

    private static final long serialVersionUID = 1L;

    @Getter
    private transient CrawlerContext context;
    @Getter
    private transient CrawlerConfig crawlerConfig;

    private final Class<? extends CrawlerSpecProvider> specProviderClass;
    private final String jsonConfig;

    @Override
    public void init() throws Exception {

        var spec = ClassUtil.newInstance(specProviderClass).get();
        crawlerConfig = spec.beanMapper().read(
                spec.crawlerConfigClass(),
                new StringReader(jsonConfig),
                Format.JSON);
        context = new CrawlerContext(
                spec,
                crawlerConfig,
                new IgniteGrid(Ignition.localIgnite()));
        context.init();
        context.getGrid().storage().getCache(
                IgniteGridKeys.RUN_ONCE_CACHE, String.class).clear();
    }

    @Override
    public void cancel() {
        context.close();
    }
}
