package com.norconex.collector.http.db.impl.mapdb;

import java.io.File;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.CrawlURL;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.ICrawlURLDatabase;

public class MapDBCrawlURLDatabase implements ICrawlURLDatabase {

    private static final Logger LOG = 
            LogManager.getLogger(MapDBCrawlURLDatabase.class);

    private static final int COMMIT_SIZE = 10000;
    
    private final DB db;
    private Queue<CrawlURL> queue;
    private Map<String, CrawlURL> active;
    private Map<String, CrawlURL> cache;
    private Map<String, CrawlURL> processed;
    private Set<String> sitemap;
    
    private long commitCounter;
    
    public MapDBCrawlURLDatabase(
            HttpCrawlerConfig config,
            int cacheSize,
            boolean resume) {
        super();

        LOG.info("Initializing crawl database...");
        String dbDir = config.getWorkDir().getPath() + "/crawldb/";
        new File(dbDir).mkdirs();
        // Configure and open database
        this.db = DBMaker.newFileDB(new File(dbDir + "mapdb"))
                        .closeOnJvmShutdown()
                        .cacheSoftRefEnable()
                        //.cacheLRUEnable()
                        //.cacheSize(cacheSize / 5)
//TODO configurable:    .compressionEnable()
                        .randomAccessFileEnableIfNeeded()
//TODO configurable:    .freeSpaceReclaimQ(5)
//TODO configurable:    .syncOnCommitDisable()
//TODO configurable:    .writeAheadLogDisable()
                        .make();
    
        initDB();
        if (!resume) {
            LOG.debug("Caching processed URL from last run (if any)...");
            db.rename("cache", "temp");
            db.rename("processed", "cache");
            db.rename("temp", "processed");
            LOG.debug("Cleaning queue database...");
            queue.clear();
            LOG.debug("Cleaning active database...");
            active.clear();
            LOG.debug("Cleaning sitemap database...");
            sitemap.clear();
        } else {
            LOG.debug("Resuming: putting active URLs back in the queue...");
            for (CrawlURL crawlUrl : active.values()) {
                queue.add(crawlUrl);
            }
            LOG.debug("Cleaning active database...");
            active.clear();
        }
        db.commit();
        db.compact();
        LOG.info("Done initializing databases.");
    }
    
    private void initDB() {
        queue = new MappedQueue(db, "queue");
        active = db.getHashMap("active");
        cache = db.getHashMap("cache");
        processed = db.getHashMap("processed");
        sitemap = db.getHashSet("sitemap");
    }
    
    @Override
    public void queue(CrawlURL url) {
        queue.add(url);
    }

    @Override
    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    @Override
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public boolean isQueued(String url) {
        return queue.contains(url);
    }

    @Override
    public synchronized CrawlURL next() {
        CrawlURL crawlURL = queue.poll();
        if (crawlURL != null) {
            active.put(crawlURL.getUrl(), crawlURL);
        }
        return crawlURL;
    }

    @Override
    public boolean isActive(String url) {
        return active.containsKey(url);
    }

    @Override
    public int getActiveCount() {
        return active.size();
    }

    @Override
    public CrawlURL getCached(String cacheURL) {
        return cache.get(cacheURL);
    }

    @Override
    public boolean isCacheEmpty() {
        return cache.isEmpty();
    }

    @Override
    public synchronized void processed(CrawlURL crawlURL) {
        processed.put(crawlURL.getUrl(), crawlURL);
        if (!active.isEmpty()) {
            active.remove(crawlURL.getUrl());
        }
        if (!cache.isEmpty()) {
            cache.remove(crawlURL.getUrl());
        }
        commitCounter++;
        if (commitCounter % COMMIT_SIZE == 0) {
            LOG.info("Committing URL database to disk...");
            db.commit();
            LOG.info("DONE Committing URL database.");
        }
        if (commitCounter % (COMMIT_SIZE * 10) == 0) {
            LOG.info("Compacting URL database...");
            db.compact();
            LOG.info("DONE Compacting URL database.");
        }
    }

    @Override
    public boolean isProcessed(String url) {
        return processed.containsKey(url);
    }

    @Override
    public int getProcessedCount() {
        return processed.size();
    }

    @Override
    public void queueCache() {
        for (CrawlURL crawlUrl : cache.values()) {
            queue.add(crawlUrl);
        }
    }

    @Override
    public boolean isVanished(CrawlURL crawlURL) {
        CrawlURL cachedURL = getCached(crawlURL.getUrl());
        if (cachedURL == null) {
            return false;
        }
        CrawlStatus cur = crawlURL.getStatus();
        CrawlStatus last = cachedURL.getStatus();
        return cur != CrawlStatus.OK && cur != CrawlStatus.UNMODIFIED
              && (last == CrawlStatus.OK ||  last == CrawlStatus.UNMODIFIED);
    }

    @Override
    public void sitemapResolved(String urlRoot) {
        sitemap.add(urlRoot);
    }

    @Override
    public boolean isSitemapResolved(String urlRoot) {
        return sitemap.contains(urlRoot);
    }

//    class ValueSerializer implements Serializer<CrawlURL>, Serializable {
//        private static final long serialVersionUID = -2112698832835179517L;
//        @Override
//        public void serialize(
//                DataOutput out, CrawlURL value) throws IOException {
//            out.writeUTF(value.getUrl());
//            out.writeInt(value.getDepth());
//            writeUTF(out, value.getStatus().toString());
//            writeUTF(out, value.getDocChecksum());
//            writeUTF(out, value.getHeadChecksum());
//            writeUTF(out, value.getSitemapChangeFreq());
//            writeLong(out, value.getSitemapLastMod());
//            writeFloat(out, value.getSitemapPriority());
//        }
//
//        @Override
//        public CrawlURL deserialize(
//                DataInput in, int available) throws IOException {
//            CrawlURL url = new CrawlURL(in.readUTF(), in.readInt());
//            String status = readUTF(in);
//            if (status != null) {
//                url.setStatus(CrawlStatus.valueOf(status));
//            }
//            url.setDocChecksum(readUTF(in));
//            url.setHeadChecksum(readUTF(in));
//            url.setSitemapChangeFreq(readUTF(in));
//            long lastMod = in.readLong();
//            if (lastMod > -1) {
//                url.setSitemapLastMod(lastMod);
//            }
//            float priority = in.readFloat();
//            if (priority > -1) {
//                url.setSitemapPriority(priority);
//            }
//            return url;
//        }
//        private String readUTF(DataInput in) throws IOException {
//            String value = in.readUTF();
//            if (value.equals("")) {
//                value = null;
//            }
//            return value;
//        }
//        private void writeUTF(DataOutput out, String value) throws IOException {
//            out.writeUTF(StringUtils.defaultString(value, ""));
//        }
//        private void writeLong(DataOutput out, Long value) throws IOException {
//            if (value == null) {
//                out.writeLong(-1);
//            } else {
//                out.writeLong(value);
//            }
//        }
//        private void writeFloat(
//                DataOutput out, Float value) throws IOException {
//            if (value == null) {
//                out.writeFloat(-1);
//            } else {
//                out.writeFloat(value);
//            }
//        }
//    }
}
