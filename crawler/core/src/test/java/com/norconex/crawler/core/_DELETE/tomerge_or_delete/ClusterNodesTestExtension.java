package com.norconex.crawler.core._DELETE.tomerge_or_delete;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * Provides a test template invocation for each requested node count.
 */
class ClusterNodesTestExtension
        implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> m.isAnnotationPresent(ClusterNodesTest.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext>
            provideTestTemplateInvocationContexts(
                    ExtensionContext context) {
        var ann = context.getRequiredTestMethod()
                .getAnnotation(ClusterNodesTest.class);
        return Arrays.stream(ann.nodes())
                .mapToObj(n -> new ClusterNodesInvocationContext(n, ann));
    }
}
