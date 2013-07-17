/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.delay.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalTime;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;

// Take robot delay first (if not null and enabled)
// Then schedule delay (will pick the first one matching - if any)
// Then default delay
public class DefaultDelayResolver implements IDelayResolver, IXMLConfigurable {

    public static final long DEFAULT_DELAY = 3000;
    
    private static final double THOUSAND_MILIS = 1000.0;
    private static final int MIN_DOW_LENGTH = 3;
    
    private long lastHitTimestampNanos = -1;
    private long defaultDelay = DEFAULT_DELAY;
    private List<DelaySchedule> schedules = new ArrayList<DelaySchedule>();
    private boolean ignoreRobotsCrawlDelay = false;
    
    private static final long serialVersionUID = -7742290966880042419L;

    @Override
    public void delay(RobotsTxt robotsTxt, String url) {
        if (lastHitTimestampNanos == -1) {
            lastHitTimestampNanos = System.nanoTime();
            return;
        }
        long delayNanos = millisToNanos(defaultDelay);
        if (robotsTxt != null && !ignoreRobotsCrawlDelay 
                && robotsTxt.getCrawlDelay() >=0) {
            delayNanos = TimeUnit.MILLISECONDS.toNanos(
                    (long)(robotsTxt.getCrawlDelay() * THOUSAND_MILIS));
        } else {
            for (DelaySchedule schedule : schedules) {
                if (schedule.isCurrentTimeInSchedule()) {
                    delayNanos = millisToNanos(schedule.getDelay());
                    break;
                }
            }
        }
        
        long elapsedNanoTime = System.nanoTime() - lastHitTimestampNanos;
        if (elapsedNanoTime < delayNanos) {
            Sleeper.sleepNanos(delayNanos - elapsedNanoTime);
        }
        lastHitTimestampNanos = System.nanoTime();
        Sleeper.sleepNanos(1);
    }

    /**
     * Gets the default delay in milliseconds.
     * @return default delay
     */
    public long getDefaultDelay() {
        return defaultDelay;
    }
    /**
     * Sets the default delay in milliseconds.
     * @param defaultDelay default deleay
     */
    public void setDefaultDelay(long defaultDelay) {
        this.defaultDelay = defaultDelay;
    }
    public List<DelaySchedule> getSchedules() {
        return schedules;
    }
    public void setSchedules(List<DelaySchedule> schedules) {
        this.schedules = schedules;
    }
    public boolean isIgnoreRobotsCrawlDelay() {
        return ignoreRobotsCrawlDelay;
    }
    public void setIgnoreRobotsCrawlDelay(boolean ignoreRobotsCrawlDelay) {
        this.ignoreRobotsCrawlDelay = ignoreRobotsCrawlDelay;
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        try {
            XMLConfiguration xml = ConfigurationLoader.loadXML(in);
            defaultDelay = xml.getLong("[@default]", defaultDelay);
            ignoreRobotsCrawlDelay = xml.getBoolean(
                    "[@ignoreRobotsCrawlDelay]", ignoreRobotsCrawlDelay);
            List<HierarchicalConfiguration> nodes =
                    xml.configurationsAt("schedule");
            for (HierarchicalConfiguration node : nodes) {
                schedules.add(new DelaySchedule(
                        node.getString("[@dayOfWeek]", null),
                        node.getString("[@dayOfMonth]", null),
                        node.getString("[@time]", null),
                        node.getLong("")
                ));
            }
        } catch (ConfigurationException e) {
            throw new IOException("Cannot load XML.", e);
        }
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("delay");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttribute("default", Long.toString(defaultDelay));
            writer.writeAttribute("ignoreRobotsCrawlDelay", 
                    Boolean.toString(ignoreRobotsCrawlDelay));
            
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
                        + schedule.getTimeRange().getLeft().toString("HH:MM")
                        + " to "
                        + schedule.getTimeRange().getRight().toString("HH:MM"));
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }        
    }
    
    private long millisToNanos(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    public static class DelaySchedule implements Serializable {
        private static final long serialVersionUID = 5602602696446583844L;
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
            return new ImmutablePair<LocalTime, LocalTime>(
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
                throw new HttpCollectorException(
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
                throw new HttpCollectorException(
                        "Invalid range format: " + str);
            }
            return out;
        }
        @Override
        public String toString() {
            return "DelaySchedule [dayOfWeekRange=" + dayOfWeekRange
                    + ", dayOfMonthRange=" + dayOfMonthRange + ", timeRange="
                    + timeRange + "]";
        }
        
        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(dayOfMonthRange)
                .append(dayOfWeekRange)
                .append(timeRange)
                .toHashCode();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof DefaultDelayResolver.DelaySchedule)) {
                return false;
            }
            DefaultDelayResolver.DelaySchedule other = (DefaultDelayResolver.DelaySchedule) obj;
            return new EqualsBuilder()
                .append(dayOfMonthRange, other.dayOfMonthRange)
                .append(dayOfWeekRange, other.dayOfWeekRange)
                .append(timeRange, other.timeRange)
                .isEquals();
        }
    }
    
}
