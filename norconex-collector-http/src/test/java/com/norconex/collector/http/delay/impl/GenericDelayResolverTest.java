/* Copyright 2015 Norconex Inc.
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
package com.norconex.collector.http.delay.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.norconex.collector.http.delay.impl.GenericDelayResolver.DelaySchedule;
import com.norconex.commons.lang.config.XMLConfigurationUtil;

public class GenericDelayResolverTest {

    @Test
    public void testWriteRead() throws IOException {
        GenericDelayResolver r = new GenericDelayResolver();
        r.setDefaultDelay(10000);
        r.setIgnoreRobotsCrawlDelay(true);
        r.setScope("thread");
        List<DelaySchedule> schedules = new ArrayList<>();
        schedules.add(new DelaySchedule(null, null, "from 1:00 to 2:00", 1000));
        r.setSchedules(schedules);

        System.out.println("Writing/Reading this: " + r);
        XMLConfigurationUtil.assertWriteRead(r);
    }

}
