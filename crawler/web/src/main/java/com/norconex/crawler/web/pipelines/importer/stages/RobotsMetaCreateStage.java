/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.pipelines.importer.stages;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.pipelines.importer.WebImporterPipelineContext;
import com.norconex.crawler.web.util.Web;

/**
 * Robots meata creation.
 */
public class RobotsMetaCreateStage extends AbstractImporterStage {

    @Override
    protected boolean executeStage(ImporterPipelineContext context) {
        var ctx = (WebImporterPipelineContext) context;
        if (Web.config(ctx.getCrawlerContext())
                .getRobotsMetaProvider() == null) {
            return true;
        }

        try (var reader = new InputStreamReader(
                context.getDoc().getInputStream(), StandardCharsets.UTF_8)) {
            ctx.setRobotsMeta(
                    Web.config(ctx.getCrawlerContext())
                            .getRobotsMetaProvider()
                            .getRobotsMeta(
                                    reader, ctx
                                            .getDoc()
                                            .getDocContext()
                                            .getReference(),
                                    ctx.getDoc().getDocContext()
                                            .getContentType(),
                                    ctx.getDoc().getMetadata()));
            if (ctx.getRobotsMeta() != null) {
                ctx.getCrawlerContext().fire(
                        CrawlerEvent.builder()
                                .name(WebCrawlerEvent.EXTRACTED_ROBOTS_META)
                                .source(ctx.getCrawlerContext())
                                .subject(ctx.getRobotsMeta())
                                .docContext(ctx.getDoc().getDocContext())
                                .build());
            }
        } catch (IOException e) {
            throw new CrawlerException(
                    "Cannot create RobotsMeta for : "
                            + ctx.getDoc().getDocContext().getReference(),
                    e);
        }
        return true;
    }
}
