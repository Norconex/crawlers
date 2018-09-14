/* Copyright 2010-2018 Norconex Inc.
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.joda.time.LocalDateTime;

import com.norconex.collector.core.CollectorException;
import com.norconex.commons.lang.CircularRange;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Default implementation for creating voluntary delays between URL downloads.
 * There are a few ways the actual delay value can be defined (in order):
 * </p>
 * <ol>
 *   <li>Takes the delay specify by a robots.txt file.
 *       Only applicable if robots.txt files and its robots crawl delays
 *       are not ignored.</li>
 *   <li>Takes an explicitly scheduled delay, if any (picks the first
 *       one matching).</li>
 *   <li>Use the specified default delay or 3 seconds, if none is
 *       specified.</li>
 * </ol>
 * <p>
 * In a delay schedule, the days of weeks are spelled out (in English):
 * Monday, Tuesday, etc.  Time ranges are using the 24h format.
 * </p>
 * <p>
 * One of these following scope dictates how the delay is applied, listed
 * in order from the best behaved to the least.
 * </p>
 * <ul>
 *   <li><b>crawler</b>: the delay is applied between each URL download
 *       within a crawler instance, regardless how many threads are defined
 *       within that crawler, or whether URLs are from the
 *       same site or not.  This is the default scope.</li>
 *   <li><b>site</b>: the delay is applied between each URL download
 *       from the same site within a crawler instance, regardless how many
 *       threads are defined. A site is defined by a URL protocol and its
 *       domain (e.g. http://example.com).</li>
 *   <li><b>thread</b>: the delay is applied between each URL download from
 *       any given thread.  The more threads you have the less of an
 *       impact the delay will have.</li>
 * </ul>
 *
 * <p>
 * As of 2.7.0, XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;delay class="com.norconex.collector.http.delay.impl.GenericDelayResolver"
 *          default="(milliseconds)"
 *          ignoreRobotsCrawlDelay="[false|true]"
 *          scope="[crawler|site|thread]" &gt;
 *      &lt;schedule
 *          dayOfWeek="from (week day) to (week day)"
 *          dayOfMonth="from [1-31] to [1-31]"
 *          time="from (HH:mm) to (HH:mm)"&gt;
 *        (delay in milliseconds)
 *      &lt;/schedule&gt;
 *
 *      (... repeat schedule tag as needed ...)
 *  &lt;/delay&gt;
 * </pre>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following set the minimum delay between each document download
 * on a given site to 5 seconds, no matter what the crawler robots.txt may
 * say, except on weekend, where it is more agressive (1 second).
 * </p>
 * <pre>
 *  &lt;delay class="com.norconex.collector.http.delay.impl.GenericDelayResolver"
 *          default="5 seconds" ignoreRobotsCrawlDelay="true" scope="site" &gt;
 *      &lt;schedule dayOfWeek="from Saturday to Sunday"&gt;1 second&lt;/schedule&gt;
 *  &lt;/delay&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 */
public class GenericDelayResolver extends AbstractDelayResolver {

    private static final int MIN_DOW_LENGTH = 3;

    private List<DelaySchedule> schedules = new ArrayList<>();

    public GenericDelayResolver() {
        super();
    }

    public List<DelaySchedule> getSchedules() {
        return schedules;
    }
    public void setSchedules(List<DelaySchedule> schedules) {
        this.schedules = schedules;
    }

    @Override
    protected long resolveExplicitDelay(String url) {
        long delay = -1;
        for (DelaySchedule schedule : schedules) {
            if (schedule.isCurrentTimeInSchedule()) {
                delay = schedule.getDelay();
                break;
            }
        }
        return delay;
    }

    @Override
    protected void loadDelaysFromXML(XML xml) {
        for (XML sxml : xml.getXMLList("schedule")) {
            schedules.add(new DelaySchedule(
                    sxml.getString("@dayOfWeek", null),
                    sxml.getString("@dayOfMonth", null),
                    sxml.getString("@time", null),
                    sxml.getDurationMillis(".", DEFAULT_DELAY)
            ));
        }
    }

    @Override
    protected void saveDelaysToXML(XML xml) {
        for (DelaySchedule schedule : schedules) {
            XML sxml = xml.addElement("schedule", schedule.getDelay());
            if (schedule.getDayOfWeekRange() != null) {
                sxml.setAttribute("dayOfWeek",
                        "from " + schedule.getDayOfWeekRange().getMinimum()
                      + " to " + schedule.getDayOfWeekRange().getMaximum());
            }
            if (schedule.getDayOfMonthRange() != null) {
                sxml.setAttribute("dayOfMonth",
                       "from " + schedule.getDayOfMonthRange().getMinimum()
                     + " to " + schedule.getDayOfMonthRange().getMaximum());
            }
            if (schedule.getTimeRange() != null) {
                int min = schedule.getTimeRange().getMinimum();
                int max = schedule.getTimeRange().getMaximum();
                sxml.setAttribute("time",
                      "from " + (min / 100) + ":" + (min % 100)
                     + " to " + (max / 100) + ":" + (max % 100));
            }
        }
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }

    public static class DelaySchedule {
        private final CircularRange<DOW> dayOfWeekRange;
        private final CircularRange<Integer> dayOfMonthRange;
        // time is 4 digits. E.g., 16:34 is 1634
        //TODO use LocalTime when moving to Java 8
        //(and make this class top-level).
        private final CircularRange<Integer> timeRange;
        private final long delay;
        public enum DOW {mon,tue,wed,thu,fri,sat,sun}

        public DelaySchedule(String dow, String dom, String time, long delay) {
            super();
            this.dayOfWeekRange = parseDayOfWeekRange(dow);
            this.dayOfMonthRange = parseDayOfMonthRange(dom);
            this.timeRange = parseTime(time);
            this.delay = delay;
        }
        public boolean isCurrentTimeInSchedule() {
            return isDateTimeInSchedule(LocalDateTime.now());
        }
        /*default*/ boolean isDateTimeInSchedule(LocalDateTime dt) {
            if (dayOfWeekRange != null && !dayOfWeekRange.contains(
                    DOW.values()[dt.getDayOfWeek() -1])) {
                return false;
            }
            if (dayOfMonthRange != null
                    && !dayOfMonthRange.contains(dt.getDayOfMonth())) {
                return false;
            }
            return timeRange == null || timeRange.contains(
                    (dt.getHourOfDay() * 100) + dt.getMinuteOfHour());
        }
        public CircularRange<DOW> getDayOfWeekRange() {
            return dayOfWeekRange;
        }
        public CircularRange<Integer> getDayOfMonthRange() {
            return dayOfMonthRange;
        }
        public CircularRange<Integer> getTimeRange() {
            return timeRange;
        }
        public long getDelay() {
            return delay;
        }
        private CircularRange<Integer> parseTime(String time) {
            if (StringUtils.isBlank(time)) {
                return null;
            }
            String localTime = normalize(time);
            String[] parts = StringUtils.split(localTime, '-');
            return CircularRange.between(
                    0, 2359, toTimeInt(parts[0]), toTimeInt(parts[1]));
        }
        private CircularRange<DOW> parseDayOfWeekRange(String dayOfWeek) {
            if (StringUtils.isBlank(dayOfWeek)) {
                return null;
            }
            String dow = normalize(dayOfWeek);
            String[] parts = StringUtils.split(dow, '-');
            return CircularRange.between(
                    DOW.mon, DOW.sun, toDow(parts[0]), toDow(parts[1]));
        }
        private CircularRange<Integer> parseDayOfMonthRange(String dayOfMonth) {
            if (StringUtils.isBlank(dayOfMonth)) {
                return null;
            }
            String dom = normalize(dayOfMonth);
            String[] parts = StringUtils.split(dom, '-');
            return CircularRange.between(1, 31,
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        private Integer toTimeInt(String str) {
            if (str.contains(":")) {
                String[] parts = StringUtils.split(str, ':');
                return (Integer.parseInt(parts[0]) * 100)
                        + Integer.parseInt(parts[1]);
            }
            return Integer.parseInt(str) * 100;
        }
        private static DOW toDow(String str) {
            if (str.length() < MIN_DOW_LENGTH) {
                throw new CollectorException(
                        "Invalid day of week: " + str);
            }
            String dow = str.substring(0, MIN_DOW_LENGTH);
            return DOW.valueOf(dow);
        }
        private String normalize(String str) {
            String out = str.toLowerCase();
            out = StringUtils.remove(out, "from");
            out = out.replace("to", "-");
            out = StringUtils.remove(out, " ");
            if (!out.contains("-")) {
                throw new CollectorException(
                        "Invalid range format: " + str);
            }
            return out;
        }

        @Override
        public boolean equals(final Object other) {
            return EqualsBuilder.reflectionEquals(this, other);
        }
        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
        @Override
        public String toString() {
            return new ReflectionToStringBuilder(
                    this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
        }
    }
}
