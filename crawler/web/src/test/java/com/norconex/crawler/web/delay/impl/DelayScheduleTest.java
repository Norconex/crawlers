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


package com.norconex.crawler.web.delay.impl;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.Month;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.crawler.CrawlerException;
class DelayScheduleTest {

    @Test
    void testIsDateTimeInSchedule_isInSchedule_returnsTrue() {
        //setup
        final var schedule = new DelaySchedule(
                "from Monday to Tuesday",
                "from 22 to 23",
                "from 14:00 to 18:00",
                ofMillis(100));
        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 22, 16, 44);

        //execute
        var actual = schedule.isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isTrue();
    }

    @Test
    void testIsDateTimeInSchedule_inSchedule_returnsTrue() {
        //setup
        final var schedule = new DelaySchedule(
                "from Wednesday to Thursday",
                "from 24 to 25",
                "from 14:00 to 18:00",
                ofMillis(100));
        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 24, 16, 00);

        //execute
        var actual = schedule.isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isTrue();
    }

    @Test
    void testIsDateTimeInScheduleWithNoTime_NotInSchedule_returnsFalse() {
        //setup
        var fixtureNoTime = new DelaySchedule(
                "from Monday to Tuesday",
                "from 22 to 23",
                null,
                ofMillis(100));

        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        var actual = fixtureNoTime.isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isFalse();
    }

    @Test
    void testIsDateTimeInScheduleWithBlankDOM_NotInSchedule_returnsFalse() {
        //setup
        var fixtureNoTime = new DelaySchedule(
                "from Monday to Tuesday",
                "",
                "from 14:00 to 18:00",
                ofMillis(100));

        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        var actual = fixtureNoTime.isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isFalse();
    }

    @Test
    void testIsDateTimeInScheduleWithBlankDOW_returnsFalse() {
        //setup
        var fixtureNoTime = new DelaySchedule(
                "",
                "from 22 to 23",
                "from 14:00 to 18:00",
                ofMillis(100));

        var myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        var actual = fixtureNoTime.isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isFalse();
    }

    @Test
    void testConstructorWithInvalidDomRange_throwsException() {
        Exception expectedException = null;

        //execute
        try {
            new DelaySchedule(
                    "from Monday to Tuesday",
                    "22 : 23",
                    "from 14:00 to 18:00",
                    ofMillis(100));
        }
        catch (CrawlerException e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
                .isInstanceOf(CrawlerException.class)
                .hasMessage("Invalid range format: 22 : 23");
    }
}