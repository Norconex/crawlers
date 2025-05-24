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


package com.norconex.grid.core.cluster;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnector;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ClusterExtension.class)
public @interface WithCluster {

    Class<? extends ClusterConnectorFactory> connectorFactory() default WithCluster.Default.class;

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
