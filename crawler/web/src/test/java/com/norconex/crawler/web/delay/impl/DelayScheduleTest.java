package com.norconex.crawler.web.delay.impl;

import com.norconex.crawler.core.crawler.CrawlerException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
class DelayScheduleTest {

    @Test
    void testIsDateTimeInSchedule_isInSchedule_returnsTrue() {
        //setup
        final DelaySchedule schedule = new DelaySchedule(
                "from Monday to Tuesday",
                "from 22 to 23",
                "from 14:00 to 18:00",
                ofMillis(100));
        LocalDateTime myDateTime = LocalDateTime.of(2024, Month.JANUARY, 22, 16, 44);

        //execute
        boolean actual = schedule.isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isTrue();
    }

    @Test
    void testIsDateTimeInSchedule_inSchedule_returnsTrue() {
        //setup
        final DelaySchedule schedule = new DelaySchedule(
                "from Wednesday to Thursday",
                "from 24 to 25",
                "from 14:00 to 18:00",
                ofMillis(100));
        LocalDateTime myDateTime = LocalDateTime.of(2024, Month.JANUARY, 24, 16, 00);

        //execute
        boolean actual = schedule.isDateTimeInSchedule(myDateTime);

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

        LocalDateTime myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        boolean actual = fixtureNoTime.isDateTimeInSchedule(myDateTime);

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

        LocalDateTime myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        boolean actual = fixtureNoTime.isDateTimeInSchedule(myDateTime);

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

        LocalDateTime myDateTime = LocalDateTime.of(2024, Month.JANUARY, 31, 16, 44);

        //execute
        boolean actual = fixtureNoTime.isDateTimeInSchedule(myDateTime);

        //verify
        assertThat(actual).isFalse();
    }

    @Test
    void testConstructorWithInvalidDomRange_throwsException() {
        //setup
        DelaySchedule fixtureNoTime = null;
        Exception expectedException = null;

        //execute
        try {
            fixtureNoTime = new DelaySchedule(
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