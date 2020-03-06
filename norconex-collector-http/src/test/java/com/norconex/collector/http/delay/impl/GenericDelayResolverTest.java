/* Copyright 2015-2020 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;

import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.delay.impl.GenericDelayResolver.DelaySchedule;
import com.norconex.commons.lang.xml.XML;
public class GenericDelayResolverTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(GenericDelayResolverTest.class);

    @Test
    public void testWriteRead() {
        GenericDelayResolver r = new GenericDelayResolver();
        r.setDefaultDelay(10000);
        r.setIgnoreRobotsCrawlDelay(true);
        r.setScope("thread");
        List<DelaySchedule> schedules = new ArrayList<>();
        schedules.add(new DelaySchedule(
                "from Monday to Wednesday",
                "from 1 to 15",
                "from 1:00 to 2:00",
                1000));
        r.setSchedules(schedules);

        LOG.debug("Writing/Reading this: {}", r);
        XML.assertWriteRead(r, "delay");
    }


    @Test
    public void testDelayScheduleBoundaries() {
        //FYI: Jan 1, 2000 was a Saturday
        DelaySchedule schedule = null;

        schedule = new DelaySchedule(
                "Mon to Wed", "1 to 15", "13:00 to 14:00", 0);
        Assertions.assertTrue(schedule.isDateTimeInSchedule(
                LocalDateTime.parse("2000-01-03T13:30")));
        Assertions.assertFalse(schedule.isDateTimeInSchedule(
                LocalDateTime.parse("2000-01-03T1:30")));

        schedule = new DelaySchedule(
                "Fri to Tue", "25 to 5", "22:00 to 6:00", 0);
        Assertions.assertTrue(schedule.isDateTimeInSchedule(
                LocalDateTime.parse("2000-01-01T23:30")));

        schedule = new DelaySchedule(
                "Sat to Tue", "25 to 1", "23:30 to 23:30", 0);
        Assertions.assertTrue(schedule.isDateTimeInSchedule(
                LocalDateTime.parse("2000-01-01T23:30")));

    }
}
