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
package com.norconex.collector.http.db.impl.mapdb;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

import org.mapdb.DB;

import com.norconex.collector.http.crawler.CrawlURL;

public class MappedQueue implements Queue<CrawlURL> {

    private final Queue<String> queue;
    private final Map<String, CrawlURL> map;

    public MappedQueue(DB db, String name, boolean create) {
        super();
        if (create) {
            queue = db.createQueue(name + "-q", null, true);
            map = db.createHashMap(name + "-m").counterEnable().make();
        } else {
            queue = db.getQueue(name + "-q");
            map = db.getHashMap(name + "-m");
        }
    }
    @Override
    public int size() {
        return map.size();
    }
    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }
    @Override
    public void clear() {
        queue.clear();
        map.clear();
    }
    @Override
    public boolean offer(CrawlURL crawlURL) {
        if (queue.offer(crawlURL.getUrl())) {
            map.put(crawlURL.getUrl(), crawlURL);
            return true;
        }
        return false;
    }
    @Override
    public CrawlURL remove() {
        String url = queue.remove();
        return map.remove(url);
    }
    @Override
    public CrawlURL poll() {
        String url = queue.poll();
        if (url != null) {
            return map.remove(url);
        }
        return null;
    }
    @Override
    public CrawlURL element() {
        String url = queue.element();
        return map.get(url);
    }
    @Override
    public CrawlURL peek() {
        String url = queue.peek();
        if (url != null) {
            return map.get(url);
        }
        return null;
    }
    @Override
    public boolean contains(Object o) {
        if (o instanceof String) {
            return map.containsKey((String) o);
        }
        if (o instanceof CrawlURL) {
            return map.containsKey(((CrawlURL) o).getUrl());
        }
        return false;
    }
    @Override
    public Iterator<CrawlURL> iterator() {
        throw new UnsupportedOperationException("iterator() not supported.");
    }
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("toArray() not supported.");
    }
    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException(
                "toArray(T[] a) not supported.");
    }
    @Override
    public boolean remove(Object o) {
        if (o instanceof String) {
            boolean present = queue.remove(o);
            if (present) {
                map.remove(o);
            }
            return present;
        }
        if (o instanceof CrawlURL) {
            String url = ((CrawlURL) o).getUrl();
            boolean present = queue.remove(url);
            if (present) {
                map.remove(url);
            }
            return present;
        }
        return false;
    }
    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }
    @Override
    public boolean addAll(Collection<? extends CrawlURL> c) {
        throw new UnsupportedOperationException("addAll(...) not supported.");
    }
    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException(
                "removeAll(...) not supported.");
    }
    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException(
                "retainAll(...) not supported.");
    }
    @Override
    public boolean add(CrawlURL e) {
        if (e == null) {
            return false;
        }
        boolean changed = queue.add(e.getUrl());
        map.put(e.getUrl(), e);
        return changed;
    }
}
