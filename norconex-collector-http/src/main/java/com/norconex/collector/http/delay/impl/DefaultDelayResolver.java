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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
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

/**
 * <p>
 * Default implementation for creating voluntary delays between URL downloads.
 * There are a few ways the actual delay value can be defined (in order):
 * </p>
 * <ol>
 *   <li>Takes the delay specify by a robots.txt file.  
 *       Only applicable if robots.txt files and its robots crawl delays
 *       are not ignored.</li>
 *   <li>Takes an explicitly scheduled delays, if any (picks the first
 *       one matching).</li>
 *   <li>Used the specified default delay or 3 seconds, if none is 
 *       specified.</li>
 * </ol>
 * <p>
 * In a delay schedule, the days of weeks are spelled out: Monday, Tuesday, etc
 * (English only for now).  Time ranges are using the 24h format.
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
 *       any given thread.  That the more threads you have the less of an 
 *       impact the delay will have.</li>
 * </ul>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;delay class="com.norconex.collector.http.delay.impl.DefaultDelayResolver"
 *          default="(milliseconds)" 
 *          ignoreRobotsCrawlDelay="[false|true]"
 *          scope="[crawler|site|thread] &gt;
 *      &lt;schedule dayOfWeek="from (week day) to (week day)"
 *          dayOfMonth="from [1-31] to [1-31]"
 *          time="from (hh:mm) to (hh:mm)" /&gt;
 *      (... repeat schedule tag as needed ...)
 *  &lt;/delay&gt;
 * </pre>
 * 
 * @author Pascal Essiembre
 */
public class DefaultDelayResolver implements IDelayResolver, IXMLConfigurable {

    private static final long serialVersionUID = -7742290966880042419L;

    private static final Logger LOG = 
            LogManager.getLogger(DefaultDelayResolver.class);
    
    public static final String SCOPE_CRAWLER = "crawler";
    public static final String SCOPE_SITE = "site";
    public static final String SCOPE_THREAD = "thread";
    
    /** Default delay is 3 seconds. */
    public static final long DEFAULT_DELAY = 3000;
    
    private static final double THOUSAND_MILIS = 1000.0;
    private static final int MIN_DOW_LENGTH = 3;
    
    // Per Crawler
    private MutableLong crawlerLastHitNanos;
    // Per Site
    private Map<String, Long> siteLastHitNanos;
    // Per Thread
    private ThreadLocal<Long> threadLastHitNanos;
    
    
    private long defaultDelay = DEFAULT_DELAY;
    private List<DelaySchedule> schedules = new ArrayList<DelaySchedule>();
    private boolean ignoreRobotsCrawlDelay = false;
    private String scope = SCOPE_CRAWLER;
    
    @Override
    public void delay(RobotsTxt robotsTxt, String url) {
        if (SCOPE_CRAWLER.equalsIgnoreCase(scope)) {
            resolveCrawlerDelay(robotsTxt);
        } else if (SCOPE_SITE.equalsIgnoreCase(scope)) {
            resolveSiteDelay(robotsTxt, url);
        } else if (SCOPE_THREAD.equalsIgnoreCase(scope)) {
            resolveThreadDelay(robotsTxt);
        } else {
            LOG.warn("Unspecified or unsupported delay scope: "
                    + scope + ".  Using crawler scope.");
            resolveCrawlerDelay(robotsTxt);
        }
    }

    //TODO consider making those methods re-usable classes instead?
    private void resolveCrawlerDelay(RobotsTxt robotsTxt) {
        if (crawlerLastHitNanos == null) {
            crawlerLastHitNanos = new MutableLong(System.nanoTime());
            return;
        }
        boolean waitForNextCycle = false;
        long lastHitNanos;
        synchronized (crawlerLastHitNanos) {
            if (crawlerLastHitNanos.longValue() == -1) {
                waitForNextCycle = true;
            }
            lastHitNanos = crawlerLastHitNanos.longValue();
            crawlerLastHitNanos.setValue(-1);
        }
        if (waitForNextCycle) {
            long targetDelayNanos = getTargetedDelayNanos(robotsTxt);
            if (targetDelayNanos <= 0) {
                return;
            }
            Sleeper.sleepNanos(targetDelayNanos);
            resolveCrawlerDelay(robotsTxt);
            return;
        }
        delay(robotsTxt, lastHitNanos);
        crawlerLastHitNanos.setValue(System.nanoTime());
    }

    private void resolveSiteDelay(RobotsTxt robotsTxt, String url) {
        String site = url.replaceFirst("(.*?//.*?)(/.*)|$]", "$1");
        if (siteLastHitNanos == null) {
            siteLastHitNanos = new ConcurrentHashMap<String, Long>();
            siteLastHitNanos.put(site, System.nanoTime());
            return;
        }
        Long curSiteLastHitNano = siteLastHitNanos.get(site);
        if (curSiteLastHitNano == null) {
            siteLastHitNanos.put(site, System.nanoTime());
            return;
        }
        
        boolean waitForNextCycle = false;
        long lastHitNanos;
        synchronized (siteLastHitNanos) {
            curSiteLastHitNano = siteLastHitNanos.get(site);
            if (curSiteLastHitNano == -1) {
                waitForNextCycle = true;
            }
            lastHitNanos = curSiteLastHitNano;
            siteLastHitNanos.put(site, -1l);
        }
        if (waitForNextCycle) {
            long targetDelayNanos = getTargetedDelayNanos(robotsTxt);
            if (targetDelayNanos <= 0) {
                return;
            }
            Sleeper.sleepNanos(targetDelayNanos);
            resolveSiteDelay(robotsTxt, url);
            return;
        }
        delay(robotsTxt, lastHitNanos);
        siteLastHitNanos.put(site, System.nanoTime());
    }

    private void resolveThreadDelay(RobotsTxt robotsTxt) {
        if (threadLastHitNanos == null) {
            threadLastHitNanos = new ThreadLocal<Long>();
            threadLastHitNanos.set(System.nanoTime());
        }
        long lastHitNanos = threadLastHitNanos.get();
        delay(robotsTxt, lastHitNanos);
        threadLastHitNanos.set(System.nanoTime());
    }

    private void delay(RobotsTxt robotsTxt, long lastHitNanos) {
        // Targeted delay in nanoseconds
        long targetDelayNanos = getTargetedDelayNanos(robotsTxt);
        if (targetDelayNanos <= 0) {
            return;
        }
        
        // How much time since last hit?
        long elapsedNanoTime = System.nanoTime() - lastHitNanos;

        // Sleep until targeted delay if not already passed.
        if (elapsedNanoTime < targetDelayNanos) {
            long timeToSleep = targetDelayNanos - elapsedNanoTime;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Thread " + Thread.currentThread().getName()
                        +  " sleeping for " 
                        + ((double) timeToSleep / 1000d 
                                / 1000d / 1000) + " seconds.");
            }
            Sleeper.sleepNanos(timeToSleep);
        }
        Sleeper.sleepNanos(1); // Ensure time has changed
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
    public String getScope() {
        return scope;
    }
    /**
     * Sets the delay scope. 
     * @param scope one of "crawler", "site", or "thread". 
     */
    public void setScope(String scope) {
        this.scope = scope;
    }    

    private long getTargetedDelayNanos(RobotsTxt robotsTxt) {
        long delayNanos = millisToNanos(defaultDelay);
        if (isUsingRobotsTxtCrawlDelay(robotsTxt)) {
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
        return delayNanos;
    }
    
    private boolean isUsingRobotsTxtCrawlDelay(RobotsTxt robotsTxt) {
        return robotsTxt != null && !ignoreRobotsCrawlDelay 
                && robotsTxt.getCrawlDelay() >= 0;
    }
    
    private long millisToNanos(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    
    @Override
    public void loadFromXML(Reader in) throws IOException {
        try {
            XMLConfiguration xml = ConfigurationLoader.loadXML(in);
            defaultDelay = xml.getLong("[@default]", defaultDelay);
            ignoreRobotsCrawlDelay = xml.getBoolean(
                    "[@ignoreRobotsCrawlDelay]", ignoreRobotsCrawlDelay);
            scope = xml.getString("[@scope]", SCOPE_CRAWLER);
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
            writer.writeAttribute("scope", scope);
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

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof DefaultDelayResolver))
            return false;
        DefaultDelayResolver castOther = (DefaultDelayResolver) other;
        return new EqualsBuilder()
                .append(crawlerLastHitNanos, castOther.crawlerLastHitNanos)
                .append(siteLastHitNanos, castOther.siteLastHitNanos)
                .append(threadLastHitNanos, castOther.threadLastHitNanos)
                .append(defaultDelay, castOther.defaultDelay)
                .append(schedules, castOther.schedules)
                .append(ignoreRobotsCrawlDelay,
                        castOther.ignoreRobotsCrawlDelay)
                .append(scope, castOther.scope).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(crawlerLastHitNanos)
                .append(siteLastHitNanos).append(threadLastHitNanos)
                .append(defaultDelay).append(schedules)
                .append(ignoreRobotsCrawlDelay).append(scope).toHashCode();
    }
    
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SIMPLE_STYLE)
                .append("defaultDelay", defaultDelay)
                .append("schedules", schedules)
                .append("ignoreRobotsCrawlDelay", ignoreRobotsCrawlDelay)
                .append("scope", scope).toString();
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
            DefaultDelayResolver.DelaySchedule other = 
                    (DefaultDelayResolver.DelaySchedule) obj;
            return new EqualsBuilder()
                .append(dayOfMonthRange, other.dayOfMonthRange)
                .append(dayOfWeekRange, other.dayOfWeekRange)
                .append(timeRange, other.timeRange)
                .isEquals();
        }
    }



}
