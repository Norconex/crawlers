package com.norconex.collectors.urldatabase.mongo;

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
import com.norconex.collector.http.db.ICrawlURLDatabase;

public class MongoCrawlURLDatabase implements ICrawlURLDatabase {

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

    public MongoCrawlURLDatabase(HttpCrawlerConfig config, boolean resume,
            int port, String host, String dbName) {
        this(config, resume, buildMongoDB(port, host,
                Utils.getDbNameOrGenerate(dbName, config)));
    }

    public MongoCrawlURLDatabase(HttpCrawlerConfig config, boolean resume,
            DB database) {

        this.database = database;
        this.collUrls = database.getCollection("urls");
        this.collSitemaps = database.getCollection("sitemaps");
        this.collCached = database.getCollection("cached");

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

        ensureIndex(collUrls, true, "url");
        ensureIndex(collUrls, false, "state");
        ensureIndex(collUrls, false, "depth", "state");
        ensureIndex(collSitemaps, true, "url");
        ensureIndex(collCached, true, "url");
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
            // TODO use specific exception
            throw new RuntimeException(e);
        }
    }

    protected void clear() {
        database.dropDatabase();
    }

    public void queue(CrawlURL crawlUrl) {
        BasicDBObject document = new BasicDBObject();
        document.append("url", crawlUrl.getUrl());
        document.append("depth", crawlUrl.getDepth());
        document.append("state", State.QUEUED.name());

        // If the document does not exist yet, it will be inserted. If exists,
        // it will be replaced.
        BasicDBObject whereQuery = new BasicDBObject("url", crawlUrl.getUrl());
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

        BasicDBObject whereQuery = new BasicDBObject("state",
                State.QUEUED.name());
        BasicDBObject sort = new BasicDBObject("depth", 1);

        BasicDBObject newDocument = new BasicDBObject("$set",
                new BasicDBObject("state", State.ACTIVE.name()));

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
        BasicDBObject whereQuery = new BasicDBObject("url", cacheURL);
        DBObject result = collCached.findOne(whereQuery);
        return convertToCrawlURL(result);
    }

    private CrawlURL convertToCrawlURL(DBObject result) {
        if (result == null) {
            return null;
        }

        String url = (String) result.get("url");
        Integer depth = (Integer) result.get("depth");
        CrawlURL crawlURL = new CrawlURL(url, depth);

        String crawlStatus = (String) result.get("crawl_status");
        if (crawlStatus != null) {
            crawlURL.setStatus(CrawlStatus.valueOf(crawlStatus));
        }

        crawlURL.setHeadChecksum((String) result.get("head_checksum"));
        crawlURL.setDocChecksum((String) result.get("doc_checksum"));

        return crawlURL;
    }

    public boolean isCacheEmpty() {
        return collCached.count() == 0;
    }

    public void processed(CrawlURL crawlURL) {

        BasicDBObject document = new BasicDBObject();
        document.put("url", crawlURL.getUrl());
        document.put("depth", crawlURL.getDepth());
        document.put("state", State.PROCESSED.name());
        document.put("crawl_status", crawlURL.getStatus().toString());
        document.put("head_checksum", crawlURL.getHeadChecksum());
        document.put("doc_checksum", crawlURL.getDocChecksum());
        document.put("valid", isValidStatus(crawlURL));

        // If the document does not exist yet, it will be inserted. If exists,
        // it will be updated.
        BasicDBObject whereQuery = new BasicDBObject("url", crawlURL.getUrl());
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

    public void queueCache() {
        // Get all cached urls
        DBCursor cursor = collCached.find();

        // Add them to the queue
        while (cursor.hasNext()) {
            DBObject next = cursor.next();
            BasicDBObject whereQuery = new BasicDBObject();
            whereQuery.put("url", next.get("url"));

            next.put("state", State.QUEUED.name());
            collUrls.update(whereQuery, next, true, false);
        }
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

    protected void changeUrlsState(State state, State newState) {
        BasicDBObject whereQuery = new BasicDBObject("state", state.name());
        BasicDBObject newDocument = new BasicDBObject("$set",
                new BasicDBObject("state", newState.name()));
        // Batch update
        collUrls.update(whereQuery, newDocument, false, true);
    }

    protected void deleteUrls(String... states) {
        BasicDBObject document = new BasicDBObject();
        List<String> list = Arrays.asList(states);
        document.put("state", new BasicDBObject("$in", list));
        collUrls.remove(document);
    }

    protected int getUrlsCount(State state) {
        BasicDBObject whereQuery = new BasicDBObject("state", state.name());
        return (int) collUrls.count(whereQuery);
    }

    protected boolean isState(String url, State state) {
        BasicDBObject whereQuery = new BasicDBObject("url", url);
        DBObject result = collUrls.findOne(whereQuery);
        if (result == null || result.get("state") == null) {
            return false;
        }
        State currentState = State.valueOf((String) result.get("state"));
        return state == currentState;
    }

    @Override
    public void sitemapResolved(String urlRoot) {
        BasicDBObject document = new BasicDBObject("url", urlRoot);
        collSitemaps.insert(document);
    }

    @Override
    public boolean isSitemapResolved(String urlRoot) {
        BasicDBObject whereQuery = new BasicDBObject("url", urlRoot);
        return collSitemaps.findOne(whereQuery) != null;
    }

    @Override
    public void close() {
        database.getMongo().close();
    }

    private void processedToCached() {
        BasicDBObject whereQuery = new BasicDBObject("state",
                State.PROCESSED.name());
        whereQuery.put("valid", true);
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
