/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.operations.delay.impl;

import java.time.Duration;
import java.time.LocalTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DelaySchedule {

    public enum DOW {
        MON, TUE, WED, THU, FRI, SAT, SUN
    }

    // ranges are circular (e.g., could be from 26 to 2 for DoM)
    private DelayRange<DOW> dayOfWeekRange;
    private DelayRange<Integer> dayOfMonthRange;
    private DelayRange<LocalTime> timeRange;
    private Duration delay;

    //--- Serialization helpers ------------------------------------------------

    @JsonSetter(value = "timeRange")
    void setTimeRangeSerial(DelayRange<String> range) {
        if (range == null) {
            timeRange = null;
            return;
        }
        setTimeRange(new DelayRange<LocalTime>()
                .setStart(parseTime(range.getStart()))
                .setEnd(parseTime(range.getEnd())));
    }

    @JsonGetter(value = "timeRange")
    DelayRange<String> getTimeRangeSerial() {
        if (timeRange == null) {
            return null;
        }
        return new DelayRange<String>()
                .setStart(formatTime(timeRange.getStart()))
                .setEnd(formatTime(timeRange.getEnd()));
    }

    private String formatTime(LocalTime time) {
        if (time == null) {
            return null;
        }
        return time.toString();
    }

    private LocalTime parseTime(String str) {
        if (StringUtils.isBlank(str)) {
            return null;
        }
        var time = Stream
                .of(str.split(":"))
                .map(unit -> StringUtils.leftPad(unit, 2, '0'))
                .collect(Collectors.joining(":"));
        if (time.length() == 2) {
            time += ":00";
        }
        return LocalTime.parse(time);
    }
}
