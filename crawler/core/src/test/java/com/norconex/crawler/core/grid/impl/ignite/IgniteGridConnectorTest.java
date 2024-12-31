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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpec;
import com.norconex.crawler.core.junit.CrawlTest;

class IgniteGridConnectorTest {

    @TempDir
    private Path tempDir;

    @CrawlTest(
        config = """
            startReferences:
              - "mock:delete1"
            gridConnector:
              class: IgniteGridConnector
              igniteConfig:
                igniteInstanceName: BLAH
                peerClassLoadingEnabled: true
            """,
        gridConnectors = IgniteGridConnector.class
    )
    void testWriteRead(CrawlerConfig cfg) {

        assertThatNoException().isThrownBy(
                () -> new CrawlerSpec().beanMapper().assertWriteRead(cfg,
                        Format.YAML));
    }
}
/*

<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:util="http://www.springframework.org/schema/util"
       xsi:schemaLocation="
        http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans.xsd
        http://www.springframework.org/schema/util
        http://www.springframework.org/schema/util/spring-util.xsd">
  <bean abstract="true" id="ignite.cfg" class="org.apache.ignite.configuration.IgniteConfiguration">
    <!-- Set to true to enable distributed class loading for examples, default is false. -->
    <property name="peerClassLoadingEnabled" value="true"/>

    <!-- Enable task execution events for examples. -->
    <property name="includeEventTypes">
      <list>
        <!--Task execution events-->
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_TASK_STARTED"/>
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_TASK_FINISHED"/>
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_TASK_FAILED"/>
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_TASK_TIMEDOUT"/>
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_TASK_SESSION_ATTR_SET"/>
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_TASK_REDUCED"/>

        <!--Cache events-->
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_CACHE_OBJECT_PUT"/>
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_CACHE_OBJECT_READ"/>
        <util:constant static-field="org.apache.ignite.events.EventType.EVT_CACHE_OBJECT_REMOVED"/>
      </list>
    </property>

    <!-- Explicitly configure TCP discovery SPI to provide list of initial nodes. -->
    <property name="discoverySpi">
      <bean class="org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi">
        <property name="ipFinder">
          <!--
              Ignite provides several options for automatic discovery that can be used
              instead os static IP based discovery. For information on all options refer
              to our documentation: http://apacheignite.readme.io/docs/cluster-config
          -->
          <!-- Uncomment static IP finder to enable static-based discovery of initial nodes. -->
          <!--<bean class="org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder">-->
          <bean class="org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder">
            <property name="addresses">
              <list>
                <!-- In distributed environment, replace with actual host IP address. -->
                <value>127.0.0.1:47500..47509</value>
              </list>
            </property>
          </bean>
        </property>
      </bean>
    </property>
  </bean>
</beans>

*/