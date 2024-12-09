/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.commands.crawl.task.operations.delay.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.web.commands.crawl.task.operations.delay.impl.DelayRange;
import com.norconex.crawler.web.commands.crawl.task.operations.delay.impl.DelaySchedule;
import com.norconex.crawler.web.commands.crawl.task.operations.delay.impl.GenericDelayResolver;
import com.norconex.crawler.web.commands.crawl.task.operations.delay.impl.BaseDelayResolverConfig.DelayResolverScope;
import com.norconex.crawler.web.commands.crawl.task.operations.robot.RobotsTxt;

class GenericDelayResolverTest {

    @Test
    void testWriteRead() {
        List<DelaySchedule> schedules = new ArrayList<>();
        schedules.add(
                new DelaySchedule()
                        .setDayOfWeekRange(
                                new DelayRange<>(
                                        DelaySchedule.DOW.MON,
                                        DelaySchedule.DOW.WED))
                        .setDayOfMonthRange(new DelayRange<>(1, 15))
                        .setTimeRange(
                                new DelayRange<>(
                                        LocalTime.parse("13:00"),
                                        LocalTime.parse("14:00")))
                        .setDelay(Duration.ofSeconds(1)));

        var r = new GenericDelayResolver();
        r.getConfiguration()
                .setSchedules(schedules)
                .setDefaultDelay(Duration.ofSeconds(10))
                .setIgnoreRobotsCrawlDelay(true)
                .setScope(DelayResolverScope.THREAD);

        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(r));
    }

    @Test
    void testNullDelays() {
        var r = new GenericDelayResolver();
        r.getConfiguration()
                .setScope(null);
        assertThatNoException().isThrownBy(
                () -> r.delay(null, "http://somewhere.com"));

    }

    @Test
    void testWithRobotsTxt() {
        var r = new GenericDelayResolver();
        //        r.getConfiguration()
        //                .setScope(null);
        var robotsTxt = RobotsTxt.builder().crawlDelay(1000f).build();
        assertThatNoException().isThrownBy(
                () -> r.delay(robotsTxt, "http://somewhere.com"));

    }

    @Test
    void testDelayScheduleBoundaries() {
        //FYI: Jan 1, 2000 was a Saturday
        var schedule = new DelaySchedule()
                .setDayOfWeekRange(
                        new DelayRange<>(
                                DelaySchedule.DOW.MON,
                                DelaySchedule.DOW.WED))
                .setDayOfMonthRange(new DelayRange<>(1, 15))
                .setTimeRange(
                        new DelayRange<>(
                                LocalTime.parse("13:00"),
                                LocalTime.parse("14:00")))
                .setDelay(Duration.ZERO);
        Assertions.assertTrue(
                GenericDelayResolver
                        .toCircularSchedule(schedule)
                        .isDateTimeInSchedule(
                                LocalDateTime.parse("2000-01-03T13:30")));
        Assertions.assertFalse(
                GenericDelayResolver
                        .toCircularSchedule(schedule).isDateTimeInSchedule(
                                LocalDateTime.parse("2000-01-03T01:30")));

        schedule = new DelaySchedule()
                .setDayOfWeekRange(
                        new DelayRange<>(
                                DelaySchedule.DOW.FRI,
                                DelaySchedule.DOW.TUE))
                .setDayOfMonthRange(new DelayRange<>(25, 5))
                .setTimeRange(
                        new DelayRange<>(
                                LocalTime.parse("22:00"),
                                LocalTime.parse("06:00")))
                .setDelay(Duration.ZERO);
        Assertions.assertTrue(
                GenericDelayResolver
                        .toCircularSchedule(schedule)
                        .isDateTimeInSchedule(
                                LocalDateTime.parse("2000-01-01T23:30")));

        schedule = new DelaySchedule()
                .setDayOfWeekRange(
                        new DelayRange<>(
                                DelaySchedule.DOW.SAT,
                                DelaySchedule.DOW.TUE))
                .setDayOfMonthRange(new DelayRange<>(25, 1))
                .setTimeRange(
                        new DelayRange<>(
                                LocalTime.parse("23:30"),
                                LocalTime.parse("23:30")))
                .setDelay(Duration.ZERO);
        Assertions.assertTrue(
                GenericDelayResolver
                        .toCircularSchedule(schedule)
                        .isDateTimeInSchedule(
                                LocalDateTime.parse("2000-01-01T23:30")));
    }

    @Test
    void testDelay() {
        var r = new GenericDelayResolver();
        r.getConfiguration().setDefaultDelay(Duration.ZERO);
        assertThatNoException().isThrownBy(
                () -> r.delay(null, "http://somewhere.com"));

        r.getConfiguration().setDefaultDelay(Duration.ofMillis(50));
        assertThatNoException().isThrownBy(
                () -> r.delay(null, "http://somewhere.com"));
        // doing twice in a row to trigger within elapsed time
        assertThatNoException().isThrownBy(
                () -> r.delay(null, "http://somewhere.com"));
    }
}
