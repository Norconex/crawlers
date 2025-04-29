package com.norconex.grid.core.cluster_working;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Alias for using <code>{@literal @}{@link ClusterTest}(numNodes = 1)</code>.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ClusterTest(numNodes = 1)
public @interface NodeTest {
}
