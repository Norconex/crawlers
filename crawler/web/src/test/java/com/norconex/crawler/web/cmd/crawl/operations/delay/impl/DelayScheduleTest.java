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

package com.norconex.crawler.web.cmd.crawl.operations.delay.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;

import org.junit.jupiter.api.Test;

class DelayScheduleTest {

    @Test
    void testIsDateTimeInSchedule_isInSchedule_returnsTrue() {
        //setup
        final var schedule = new DelaySchedule()
                .setDayOfWeekRange(
                        new DelayRange<>(
                                DelaySchedule.DOW.MON,
                                DelaySchedule.DOW.TUE))
                .setDayOfMonthRange(new DelayRange<>(22, 23))
                .setTimeRange(
                        new DelayRange<>(
                                LocalTime.parse("14:00"),
                                LocalTime.parse("18:00")))
                .setDelay(Duration.ofMillis(100));
        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 22, 16, 44);

        //execute
        var actual = GenericDelayResolver
                .toCircularSchedule(schedule)
                .isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isTrue();
    }

    @Test
    void testIsDateTimeInSchedule_inSchedule_returnsTrue() {
        //setup
        final var schedule = new DelaySchedule()
                .setDayOfWeekRange(
                        new DelayRange<>(
                                DelaySchedule.DOW.WED,
                                DelaySchedule.DOW.THU))
                .setDayOfMonthRange(new DelayRange<>(24, 25))
                .setTimeRange(
                        new DelayRange<>(
                                LocalTime.parse("14:00"),
                                LocalTime.parse("18:00")))
                .setDelay(Duration.ofMillis(100));

        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 24, 16, 00);

        //execute
        var actual = GenericDelayResolver
                .toCircularSchedule(schedule)
                .isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isTrue();
    }

    @Test
    void testIsDateTimeInScheduleWithNoTime_NotInSchedule_returnsFalse() {
        //setup
        final var schedule = new DelaySchedule()
                .setDayOfWeekRange(
                        new DelayRange<>(
                                DelaySchedule.DOW.MON,
                                DelaySchedule.DOW.TUE))
                .setDayOfMonthRange(new DelayRange<>(22, 23))
                .setDelay(Duration.ofMillis(100));

        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        var actual = GenericDelayResolver
                .toCircularSchedule(schedule)
                .isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isFalse();
    }

    @Test
    void testIsDateTimeInScheduleWithBlankDOM_NotInSchedule_returnsFalse() {
        //setup
        final var schedule = new DelaySchedule()
                .setDayOfWeekRange(
                        new DelayRange<>(
                                DelaySchedule.DOW.MON,
                                DelaySchedule.DOW.TUE))
                .setTimeRange(
                        new DelayRange<>(
                                LocalTime.parse("14:00"),
                                LocalTime.parse("18:00")))
                .setDelay(Duration.ofMillis(100));

        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        var actual = GenericDelayResolver
                .toCircularSchedule(schedule)
                .isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isFalse();
    }

    @Test
    void testIsDateTimeInScheduleWithBlankDOW_returnsFalse() {
        //setup
        final var schedule = new DelaySchedule()
                .setDayOfMonthRange(new DelayRange<>(22, 23))
                .setTimeRange(
                        new DelayRange<>(
                                LocalTime.parse("14:00"),
                                LocalTime.parse("18:00")))
                .setDelay(Duration.ofMillis(100));

        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        var actual = GenericDelayResolver
                .toCircularSchedule(schedule)
                .isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isFalse();
    }
}
