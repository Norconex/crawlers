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
package com.norconex.collector.http.db.impl.mongo;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.CrawlURL;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.CrawlURLDatabaseException;
import com.norconex.collector.http.db.ICrawlURLDatabase;

/**
 * <p>Mongo implementation of {@link ICrawlURLDatabase}.</p>
 * 
 * <p>All the urls are stored in a collection named 'url'. They go from the
 * "QUEUED", "ACTIVE" and "PROCESSED" states.</p>
 * 
 * <p>The cached urls are stored in a separated collection named "cached".</p>
 * 
 * <p>The sitemaps information are stored in a "sitemaps" collection.</p>
 * 
 * @author Pascal Dimassimo
 * @since 1.2
 */
public class MongoCrawlURLDatabase implements ICrawlURLDatabase {

    private static final String FIELD_VALID = "valid";

    private static final String FIELD_DOC_CHECKSUM = "doc_checksum";

    private static final String FIELD_HEAD_CHECKSUM = "head_checksum";

    private static final String FIELD_CRAWL_STATUS = "crawl_status";

    private static final String FIELD_DEPTH = "depth";

    private static final String FIELD_STATE = "state";

    private static final String FIELD_URL = "url";

    private static final String COLLECTION_CACHED = "cached";

    private static final String COLLECTION_SITEMAPS = "sitemaps";

    private static final String COLLECTION_URLS = "urls";

    private static final int BATCH_UPDATE_SIZE = 1000;

    private final DB database;

    private final DBCollection collUrls;
    /*
     * We need to have a separate collection for cached because an url can be
     * both queued and cached (it will be removed from cache when it is
     * processed)
     */
    private final DBCollection collCached;
    private final DBCollection collSitemaps;

    enum State {
        QUEUED, ACTIVE, PROCESSED;
    };

    /**
     * Constructor.
     * @param config crawler config
     * @param resume whether to resume an aborted job
     * @param port Mongo port
     * @param host Mongo host
     * @param dbName Mongo database name
     */
    public MongoCrawlURLDatabase(HttpCrawlerConfig config, boolean resume,
            int port, String host, String dbName) {
        this(resume, buildMongoDB(
                port, host, MongoUtil.getDbNameOrGenerate(dbName, config)));
    }

    /**
     * Constructor.
     * @param resume whether to resume an aborted job
     * @param database Mongo database
     */
    public MongoCrawlURLDatabase(boolean resume, DB database) {

        this.database = database;
        this.collUrls = database.getCollection(COLLECTION_URLS);
        this.collSitemaps = database.getCollection(COLLECTION_SITEMAPS);
        this.collCached = database.getCollection(COLLECTION_CACHED);

        if (resume) {
            changeUrlsState(State.ACTIVE, State.QUEUED);
        } else {
            // Delete everything except valid processed urls that are transfered
            // to cache
            deleteAllDocuments(collSitemaps);
            deleteAllDocuments(collCached);
            processedToCached();
            deleteAllDocuments(collUrls);
        }

        ensureIndex(collUrls, true, FIELD_URL);
        ensureIndex(collUrls, false, FIELD_STATE);
        ensureIndex(collUrls, false, FIELD_DEPTH, FIELD_STATE);
        ensureIndex(collSitemaps, true, FIELD_URL);
        ensureIndex(collCached, true, FIELD_URL);
    }

    private void ensureIndex(DBCollection coll, boolean unique,
            String... fields) {
        BasicDBObject fieldsObject = new BasicDBObject();
        for (String field : fields) {
            fieldsObject.append(field, 1);
        }
        coll.ensureIndex(fieldsObject, null, unique);
    }

    protected static DB buildMongoDB(int port, String host, String dbName) {
        try {
            MongoClient client = new MongoClient(host, port);
            return client.getDB(dbName);
        } catch (UnknownHostException e) {
            throw new CrawlURLDatabaseException(e);
        }
    }

    protected void clear() {
        database.dropDatabase();
    }

    public void queue(CrawlURL crawlUrl) {
        BasicDBObject document = new BasicDBObject();
        document.append(FIELD_URL, crawlUrl.getUrl());
        document.append(FIELD_DEPTH, crawlUrl.getDepth());
        document.append(FIELD_STATE, State.QUEUED.name());

        // If the document does not exist yet, it will be inserted. If exists,
        // it will be replaced.
        BasicDBObject whereQuery = new BasicDBObject(FIELD_URL,
                crawlUrl.getUrl());
        collUrls.update(whereQuery, document, true, false);
    }

    public boolean isQueueEmpty() {
        return getQueueSize() == 0;
    }

    public int getQueueSize() {
        return getUrlsCount(State.QUEUED);
    }

    public boolean isQueued(String url) {
        return isState(url, State.QUEUED);
    }

    public CrawlURL nextQueued() {

        BasicDBObject whereQuery = new BasicDBObject(FIELD_STATE,
                State.QUEUED.name());
        BasicDBObject sort = new BasicDBObject(FIELD_DEPTH, 1);

        BasicDBObject newDocument = new BasicDBObject("$set",
                new BasicDBObject(FIELD_STATE, State.ACTIVE.name()));

        DBObject next = collUrls.findAndModify(whereQuery, sort, newDocument);
        return convertToCrawlURL(next);
    }

    public boolean isActive(String url) {
        return isState(url, State.ACTIVE);
    }

    public int getActiveCount() {
        return getUrlsCount(State.ACTIVE);
    }

    public CrawlURL getCached(String cacheURL) {
        BasicDBObject whereQuery = new BasicDBObject(FIELD_URL, cacheURL);
        DBObject result = collCached.findOne(whereQuery);
        return convertToCrawlURL(result);
    }

    private CrawlURL convertToCrawlURL(DBObject result) {
        if (result == null) {
            return null;
        }

        String url = (String) result.get(FIELD_URL);
        Integer depth = (Integer) result.get(FIELD_DEPTH);
        CrawlURL crawlURL = new CrawlURL(url, depth);

        String crawlStatus = (String) result.get(FIELD_CRAWL_STATUS);
        if (crawlStatus != null) {
            crawlURL.setStatus(CrawlStatus.valueOf(crawlStatus));
        }

        crawlURL.setHeadChecksum((String) result.get(FIELD_HEAD_CHECKSUM));
        crawlURL.setDocChecksum((String) result.get(FIELD_DOC_CHECKSUM));

        return crawlURL;
    }

    public boolean isCacheEmpty() {
        return collCached.count() == 0;
    }

    public void processed(CrawlURL crawlURL) {

        BasicDBObject document = new BasicDBObject();
        document.put(FIELD_URL, crawlURL.getUrl());
        document.put(FIELD_DEPTH, crawlURL.getDepth());
        document.put(FIELD_STATE, State.PROCESSED.name());
        document.put(FIELD_CRAWL_STATUS, crawlURL.getStatus().toString());
        document.put(FIELD_HEAD_CHECKSUM, crawlURL.getHeadChecksum());
        document.put(FIELD_DOC_CHECKSUM, crawlURL.getDocChecksum());
        document.put(FIELD_VALID, isValidStatus(crawlURL));

        // If the document does not exist yet, it will be inserted. If exists,
        // it will be updated.
        BasicDBObject whereQuery = new BasicDBObject(FIELD_URL,
                crawlURL.getUrl());
        collUrls.update(whereQuery, new BasicDBObject("$set", document), true,
                false);

        // Remove from cache
        collCached.remove(whereQuery);
    }

    public boolean isProcessed(String url) {
        return isState(url, State.PROCESSED);
    }

    public int getProcessedCount() {
        return getUrlsCount(State.PROCESSED);
    }

    /**
     * TODO: same code as Derby impl. Put in a abstract base class?
     */
    public boolean isVanished(CrawlURL crawlURL) {
        CrawlURL cachedURL = getCached(crawlURL.getUrl());
        if (cachedURL == null) {
            return false;
        }
        CrawlStatus cur = crawlURL.getStatus();
        CrawlStatus last = cachedURL.getStatus();
        return cur != CrawlStatus.OK && cur != CrawlStatus.UNMODIFIED
                && (last == CrawlStatus.OK || last == CrawlStatus.UNMODIFIED);
    }

    /**
     * TODO: same code as Derby impl. Put in a abstract base class?
     */
    private boolean isValidStatus(CrawlURL crawlURL) {
        return crawlURL.getStatus() == CrawlStatus.OK
                || crawlURL.getStatus() == CrawlStatus.UNMODIFIED;
    }

    private void changeUrlsState(State state, State newState) {
        BasicDBObject whereQuery = new BasicDBObject(FIELD_STATE, state.name());
        BasicDBObject newDocument = new BasicDBObject("$set",
                new BasicDBObject(FIELD_STATE, newState.name()));
        // Batch update
        collUrls.update(whereQuery, newDocument, false, true);
    }

    protected void deleteUrls(String... states) {
        BasicDBObject document = new BasicDBObject();
        List<String> list = Arrays.asList(states);
        document.put(FIELD_STATE, new BasicDBObject("$in", list));
        collUrls.remove(document);
    }

    protected int getUrlsCount(State state) {
        BasicDBObject whereQuery = new BasicDBObject(FIELD_STATE, state.name());
        return (int) collUrls.count(whereQuery);
    }

    protected boolean isState(String url, State state) {
        BasicDBObject whereQuery = new BasicDBObject(FIELD_URL, url);
        DBObject result = collUrls.findOne(whereQuery);
        if (result == null || result.get(FIELD_STATE) == null) {
            return false;
        }
        State currentState = State.valueOf((String) result.get(FIELD_STATE));
        return state.equals(currentState);
    }

    @Override
    public void sitemapResolved(String urlRoot) {
        BasicDBObject document = new BasicDBObject(FIELD_URL, urlRoot);
        collSitemaps.insert(document);
    }

    @Override
    public boolean isSitemapResolved(String urlRoot) {
        BasicDBObject whereQuery = new BasicDBObject(FIELD_URL, urlRoot);
        return collSitemaps.findOne(whereQuery) != null;
    }

    @Override
    public void close() {
        database.getMongo().close();
    }

    private void processedToCached() {
        BasicDBObject whereQuery = new BasicDBObject(FIELD_STATE,
                State.PROCESSED.name());
        whereQuery.put(FIELD_VALID, true);
        DBCursor cursor = collUrls.find(whereQuery);

        // Add them to cache in batch
        ArrayList<DBObject> list = new ArrayList<DBObject>(BATCH_UPDATE_SIZE);
        while (cursor.hasNext()) {
            list.add(cursor.next());
            if (list.size() == BATCH_UPDATE_SIZE) {
                collCached.insert(list);
                list.clear();
            }
        }
        if (!list.isEmpty()) {
            collCached.insert(list);
        }
    }

    private void deleteAllDocuments(DBCollection coll) {
        coll.remove(new BasicDBObject());
    }

    @Override
    public Iterator<CrawlURL> getCacheIterator() {
        final DBCursor cursor = collCached.find();
        return new Iterator<CrawlURL>() {
            @Override
            public boolean hasNext() {
                return cursor.hasNext();
            }

            @Override
            public CrawlURL next() {
                return convertToCrawlURL(cursor.next());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
