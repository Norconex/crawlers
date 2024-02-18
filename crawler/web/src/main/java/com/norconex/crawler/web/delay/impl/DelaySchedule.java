/* Copyright 2023 Norconex Inc.
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.CircularRange;
import com.norconex.crawler.core.crawler.CrawlerException;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class DelaySchedule {
    private static final int MIN_DOW_LENGTH = 3;
    private final CircularRange<DelaySchedule.DOW> dayOfWeekRange;
    private final CircularRange<Integer> dayOfMonthRange;
    // time is 4 digits. E.g., 16:34 is 1634
    //MAYBE: use LocalTime (and make this class top-level)?
    private final CircularRange<Integer> timeRange;
    private final Duration delay;
    public enum DOW {MON,TUE,WED,THU,FRI,SAT,SUN}

    @JsonCreator
    public DelaySchedule(
            @JsonProperty("dayOfWeekRange") String dow,
            @JsonProperty("dayOfMonthRange") String dom,
            @JsonProperty("timeRange") String time,
            @JsonProperty("delay") Duration delay) {
        dayOfWeekRange = parseDayOfWeekRange(dow);
        dayOfMonthRange = parseDayOfMonthRange(dom);
        timeRange = parseTime(time);
        this.delay = Optional.ofNullable(delay).orElse(Duration.ZERO);
    }
    public boolean isCurrentTimeInSchedule() {
        return isDateTimeInSchedule(LocalDateTime.now());
    }
    boolean isDateTimeInSchedule(LocalDateTime dt) {
        if ((dayOfWeekRange != null && !dayOfWeekRange.contains(
                DOW.values()[dt.getDayOfWeek().ordinal()]))
                || (dayOfMonthRange != null
                && !dayOfMonthRange.contains(dt.getDayOfMonth()))) {
            return false;
        }
        return timeRange == null || timeRange.contains(
                (dt.getHour() * 100) + dt.getMinute());
    }
    @JsonIgnore
    public CircularRange<DelaySchedule.DOW> getDayOfWeekRange() {
        return dayOfWeekRange;
    }
    @JsonIgnore
    public CircularRange<Integer> getDayOfMonthRange() {
        return dayOfMonthRange;
    }
    @JsonIgnore
    public CircularRange<Integer> getTimeRange() {
        return timeRange;
    }

    public Duration getDelay() {
        return delay;
    }
    private CircularRange<Integer> parseTime(String time) {
        if (StringUtils.isBlank(time)) {
            return null;
        }
        var localTime = normalize(time);
        var parts = StringUtils.split(localTime, '-');
        return CircularRange.between(
                0, 2359, toTimeInt(parts[0]), toTimeInt(parts[1]));
    }
    private CircularRange<DelaySchedule.DOW> parseDayOfWeekRange(
            String dayOfWeek) {
        if (StringUtils.isBlank(dayOfWeek)) {
            return null;
        }
        var dow = normalize(dayOfWeek);
        var parts = StringUtils.split(dow, '-');
        return CircularRange.between(
                DOW.MON, DOW.SUN, toDow(parts[0]), toDow(parts[1]));
    }
    private CircularRange<Integer> parseDayOfMonthRange(String dayOfMonth) {
        if (StringUtils.isBlank(dayOfMonth)) {
            return null;
        }
        var dom = normalize(dayOfMonth);
        var parts = StringUtils.split(dom, '-');
        return CircularRange.between(1, 31,
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
    private Integer toTimeInt(String str) {
        if (str.contains(":")) {
            var parts = StringUtils.split(str, ':');
            return (Integer.parseInt(parts[0]) * 100)
                    + Integer.parseInt(parts[1]);
        }
        return Integer.parseInt(str) * 100;
    }
    private static DelaySchedule.DOW toDow(String str) {
        if (str.length() < MIN_DOW_LENGTH) {
            throw new CrawlerException("Invalid day of week: " + str);
        }
        var dow = str.substring(0, MIN_DOW_LENGTH);
        return DOW.valueOf(dow.toUpperCase());
    }
    private String normalize(String str) {
        var out = str.toLowerCase();
        out = StringUtils.remove(out, "from");
        out = out.replace("to", "-");
        out = StringUtils.remove(out, " ");
        if (!out.contains("-")) {
            throw new CrawlerException("Invalid range format: " + str);
        }
        return out;
    }

    //NOTE: The following methods are so Api schema generation
    // references are generated to CircularReference as opposed
    // to CircularReferenceInteger, CircularReferenceDOW, etc.
    // If there is a better way delete these.
    @SuppressWarnings("rawtypes")
    @JsonProperty("dayOfWeekRange")
    CircularRange dayOfWeekRange() {
        return dayOfWeekRange;
    }
    @SuppressWarnings("rawtypes")
    @JsonProperty("dayOfMonthRange")
    CircularRange dayOfMonthRange() {
        return dayOfMonthRange;
    }
    @SuppressWarnings("rawtypes")
    @JsonProperty("timeRange")
    CircularRange timeRange() {
        return timeRange;
    }
}