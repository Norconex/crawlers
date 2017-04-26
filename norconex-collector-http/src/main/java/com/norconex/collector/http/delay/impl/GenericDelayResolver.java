/* Copyright 2010-2016 Norconex Inc.
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

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalTime;

import com.norconex.collector.core.CollectorException;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

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
    protected void loadDelaysFromXML(XMLConfiguration xml) 
            throws IOException {
        List<HierarchicalConfiguration> nodes =
                xml.configurationsAt("schedule");
        for (HierarchicalConfiguration node : nodes) {
            schedules.add(new DelaySchedule(
                    node.getString("[@dayOfWeek]", null),
                    node.getString("[@dayOfMonth]", null),
                    node.getString("[@time]", null),
                    XMLConfigurationUtil.getDuration(node, "", DEFAULT_DELAY)
            ));
        }
    }

    @Override
    protected void saveDelaysToXML(EnhancedXMLStreamWriter writer)
            throws IOException {
        try {
            for (DelaySchedule schedule : schedules) {
                writer.writeStartElement("schedule");
                if (schedule.getDayOfWeekRange() != null) {
                    writer.writeAttribute("dayOfWeek", 
                            "from " + schedule.getDayOfWeekRange().getMinimum()
                          + " to " + schedule.getDayOfWeekRange().getMaximum());
                }
                if (schedule.getDayOfMonthRange() != null) {
                    writer.writeAttribute("dayOfMonth", 
                           "from " + schedule.getDayOfMonthRange().getMinimum()
                         + " to " + schedule.getDayOfMonthRange().getMaximum());
                }
                if (schedule.getTimeRange() != null) {
                    writer.writeAttribute("time", "from " 
                        + schedule.getTimeRange().getLeft().toString("HH:mm")
                        + " to "
                        + schedule.getTimeRange().getRight().toString("HH:mm"));
                }
                writer.writeCharacters(Long.toString(schedule.getDelay()));
                writer.writeEndElement();
            }
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }        
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof GenericDelayResolver)) {
            return false;
        }
        GenericDelayResolver castOther = (GenericDelayResolver) other;
        return new EqualsBuilder()
                .appendSuper(super.equals(castOther))
                .append(schedules, castOther.schedules)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(schedules)
                .toHashCode();
    }
    
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("schedules", schedules)
                .toString();
    }
    
    public static class DelaySchedule {
        private final Range<Integer> dayOfWeekRange;
        private final Range<Integer> dayOfMonthRange;
        private final ImmutablePair<LocalTime, LocalTime> timeRange;
        private final long delay;
        private enum DOW {mon,tue,wed,thu,fri,sat,sun}

        public DelaySchedule(String dow, String dom, String time, long delay) {
            super();
            this.dayOfWeekRange = parseDayOfWeekRange(dow);
            this.dayOfMonthRange = parseDayOfMonthRange(dom);
            this.timeRange = parseTime(time);
            this.delay = delay;
        }
        public boolean isCurrentTimeInSchedule() {
            DateTime now = DateTime.now();
            if (dayOfWeekRange != null 
                    && !dayOfWeekRange.contains(now.getDayOfWeek())) {
                return false;
            }
            if (dayOfMonthRange != null 
                    && !dayOfMonthRange.contains(now.getDayOfMonth())) {
                return false;
            }
            if (timeRange != null) {
                Interval interval = new Interval(
                        timeRange.getLeft().toDateTimeToday(),
                        timeRange.getRight().toDateTimeToday());
                if (!interval.contains(now)) {
                    return false;
                }
            }
            return true;
        }
        public Range<Integer> getDayOfWeekRange() {
            return dayOfWeekRange;
        }
        public Range<Integer> getDayOfMonthRange() {
            return dayOfMonthRange;
        }
        public ImmutablePair<LocalTime, LocalTime> getTimeRange() {
            return timeRange;
        }
        public long getDelay() {
            return delay;
        }
        private ImmutablePair<LocalTime, LocalTime> parseTime(String time) {
            if (StringUtils.isBlank(time)) {
                return null;
            }
            String localTime = normalize(time);
            String[] parts = StringUtils.split(localTime, '-');
            return new ImmutablePair<>(
                    getLocalTime(parts[0]), getLocalTime(parts[1]));
        }
        private Range<Integer> parseDayOfWeekRange(String dayOfWeek) {
            if (StringUtils.isBlank(dayOfWeek)) {
                return null;
            }
            String dow = normalize(dayOfWeek);
            String[] parts = StringUtils.split(dow, '-');
            return Range.between(getDOW(parts[0]), getDOW(parts[1]));
        }
        private Range<Integer> parseDayOfMonthRange(String dayOfMonth) {
            if (StringUtils.isBlank(dayOfMonth)) {
                return null;
            }
            String dom = normalize(dayOfMonth);
            String[] parts = StringUtils.split(dom, '-');
            return Range.between(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        private LocalTime getLocalTime(String str) {
            if (str.contains(":")) {
                String[] parts = StringUtils.split(str, ':');
                return new LocalTime(
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            return new LocalTime(Integer.parseInt(str), 0);
        }
        private int getDOW(String str) {
            if (str.length() < MIN_DOW_LENGTH) {
                throw new CollectorException(
                        "Invalid day of week: " + str);
            }
            String dow = str.substring(0, MIN_DOW_LENGTH);
            return DOW.valueOf(dow).ordinal() + 1;
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
            if (!(other instanceof DelaySchedule)) {
                return false;
            }
            DelaySchedule castOther = (DelaySchedule) other;
            return new EqualsBuilder()
                    .append(dayOfMonthRange, castOther.dayOfMonthRange)
                    .append(dayOfWeekRange, castOther.dayOfWeekRange)
                    .append(timeRange, castOther.timeRange)
                    .append(delay, castOther.delay)
                    .isEquals();
        }
        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(dayOfMonthRange)
                .append(dayOfWeekRange)
                .append(timeRange)
                .append(delay)
                .toHashCode();
        }
        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("dayOfMonthRange", dayOfMonthRange)
                    .append("dayOfWeekRange", dayOfWeekRange)
                    .append("timeRange", timeRange)
                    .append("delay", delay)
                    .toString();
        }
    }
}
