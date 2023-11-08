/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.crawler.web.delay.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.web.delay.impl.GenericDelayResolver.DelaySchedule;

class GenericDelayResolverTest {

    @Test
    void testWriteRead() {
        var r = new GenericDelayResolver();
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

        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(r));
    }

    @Test
    void testDelayScheduleBoundaries() {
        //FYI: Jan 1, 2000 was a Saturday
        var schedule = new DelaySchedule(
                "Mon to Wed", "1 to 15", "13:00 to 14:00", 0);

        Assertions.assertTrue(schedule.isDateTimeInSchedule(
                LocalDateTime.parse("2000-01-03T13:30")));
        Assertions.assertFalse(schedule.isDateTimeInSchedule(
                LocalDateTime.parse("2000-01-03T01:30")));

        schedule = new DelaySchedule(
                "Fri to Tue", "25 to 5", "22:00 to 6:00", 0);
        Assertions.assertTrue(schedule.isDateTimeInSchedule(
                LocalDateTime.parse("2000-01-01T23:30")));

        schedule = new DelaySchedule(
                "Sat to Tue", "25 to 1", "23:30 to 23:30", 0);
        Assertions.assertTrue(schedule.isDateTimeInSchedule(
                LocalDateTime.parse("2000-01-01T23:30")));
    }

    @Test
    void testDelay() {
        var r = new GenericDelayResolver();
        r.setDefaultDelay(0);
        assertThatNoException().isThrownBy(
                () -> r.delay(null, "http://somewhere.com"));

        r.setDefaultDelay(50);
        assertThatNoException().isThrownBy(
                () -> r.delay(null, "http://somewhere.com"));
        // doing twice in a row to trigger within elapsed time
        assertThatNoException().isThrownBy(
                () -> r.delay(null, "http://somewhere.com"));
    }
}
