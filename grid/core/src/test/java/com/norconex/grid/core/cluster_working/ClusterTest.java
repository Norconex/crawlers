package com.norconex.grid.core.cluster_working;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnector;

@Test
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ClusterExtension.class)
public @interface ClusterTest {

    Class<? extends ClusterConnectorFactory> connectorFactory() default ClusterTest.Default.class;

    /**
     * Optionally specify one to receive a {@link Grid} instance as a method
     * argument instead of a {@link Cluster}. Specify more than one to get a
     * list of {@link Grid} instead.
     * @return number of node
     */
    int numNodes() default -1;

    public static final class Default implements ClusterConnectorFactory {
        private Default() {
            throw new UnsupportedOperationException("""
            This is a marker class, not a real connector factory. \
            Make sure to specify one, or if you are a nested test, \
            make sure a parent has one specified.""");
        }

        @Override
        public GridConnector create(String gridName, String nodeName) {
            return null;
        }
    }
}
