/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.junit;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import com.norconex.crawler.core.util.ExtensibleAnnotationFinder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CrawlTestExtension implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return findAnnotation(context.getTestClass()).isPresent()
                || findAnnotation(context.getTestMethod()).isPresent();
    }

    @Override
    public Stream<TestTemplateInvocationContext>
            provideTestTemplateInvocationContexts(ExtensionContext context) {
        var annotation = findAnnotation(context.getTestMethod()).orElseThrow();
        var connectorClasses = annotation.gridConnectors();
        return Stream.of(connectorClasses).map(
                conn -> new CrawlTestInvocationContext(conn, annotation));
    }

    private Optional<CrawlTest> findAnnotation(
            Optional<? extends AnnotatedElement> el) {
        return ExtensibleAnnotationFinder.find(
                el.orElse(null), CrawlTest.class);
    }
}
