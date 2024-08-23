/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.delay.impl;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.CircularRange;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

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
 * {@nx.xml.usage
 * <delay class="com.norconex.crawler.web.delay.impl.GenericDelayResolver"
 *       default="(milliseconds)"
 *       ignoreRobotsCrawlDelay="[false|true]"
 *       scope="[crawler|site|thread]">
 *   <schedule
 *       dayOfWeek="from (week day) to (week day)"
 *       dayOfMonth="from [1-31] to [1-31]"
 *       time="from (HH:mm) to (HH:mm)">
 *     (delay in milliseconds)
 *   </schedule>
 *
 *   (... repeat schedule tag as needed ...)
 * </delay>
 * }
 *
 * {@nx.xml.example
 * <delay class="GenericDelayResolver"
 *     default="5 seconds" ignoreRobotsCrawlDelay="true" scope="site" >
 *   <schedule dayOfWeek="from Saturday to Sunday">1 second</schedule>
 * </delay>
 * }
 *
 * <p>
 * The above example set the minimum delay between each document download
 * on a given site to 5 seconds, no matter what the crawler robots.txt may
 * say, except on weekend, where it is more agressive (1 second).
 * </p>
 */
@EqualsAndHashCode
@ToString
public class GenericDelayResolver
        extends AbstractDelayResolver<GenericDelayResolverConfig>
        implements EventListener<CrawlerEvent> {

    @Getter
    private final GenericDelayResolverConfig configuration =
            new GenericDelayResolverConfig();

    @JsonIgnore
    private List<CircularSchedule> schedules = new ArrayList<>();

    @Override
    public void accept(CrawlerEvent ev) {
        if (ev.is(CrawlerEvent.CRAWLER_RUN_BEGIN)) {
            schedules.clear();
            configuration.getSchedules().forEach(sch -> {
                schedules.add(toCircularSchedule(sch));
            });
        }
    }

    @Override
    protected Duration resolveExplicitDelay(String url) {
        Duration delay = null;
        var now = LocalDateTime.now();
        for (CircularSchedule schedule : schedules) {
            if (schedule.isDateTimeInSchedule(now)) {
                delay = schedule.delay;
                break;
            }
        }
        return delay;
    }

    static CircularSchedule toCircularSchedule(DelaySchedule sch) {
        var schedule = new CircularSchedule();
        ofNullable(sch.getDayOfMonthRange()).ifPresent(range ->
                schedule.domRange = CircularRange.between(
                        1,  31, range.getStart(), range.getEnd()));
        ofNullable(sch.getDayOfWeekRange()).ifPresent(range ->
                schedule.dowRange = CircularRange.between(
                        DelaySchedule.DOW.MON,
                        DelaySchedule.DOW.SUN,
                        range.getStart(),
                        range.getEnd()));
        ofNullable(sch.getTimeRange()).ifPresent(range ->
                schedule.timeRange = CircularRange.between(
                        LocalTime.of(0, 0),
                        LocalTime.of(23, 59, 59),
                        range.getStart(),
                        range.getEnd()));
        ofNullable(sch.getDelay()).ifPresent(
                delay -> schedule.delay = delay);
        return schedule;
    }

    static class CircularSchedule {
        private CircularRange<DelaySchedule.DOW> dowRange;
        private CircularRange<Integer> domRange;
        private CircularRange<LocalTime> timeRange;
        private Duration delay = Duration.ZERO;

        boolean isDateTimeInSchedule(LocalDateTime dt) {
            if ((dowRange != null && !dowRange.contains(
                    DelaySchedule.DOW.values()[dt.getDayOfWeek().ordinal()]))
                    || (domRange != null
                            && !domRange.contains(dt.getDayOfMonth()))) {
                return false;
            }
            return timeRange == null || timeRange.contains(dt.toLocalTime());
        }
    }
}
