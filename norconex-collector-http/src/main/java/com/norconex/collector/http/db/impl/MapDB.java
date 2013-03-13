package com.norconex.collector.http.db.impl;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.norconex.collector.http.db.CrawlURL;
import com.norconex.collector.http.db.CrawlURLDatabaseException;

/**
 * Wrapper around MapDB implementation.
 * @author Pascal Essiembre
 */
/*default*/ class MapDB {

    private final File dbDir;
    private DB db;
    private Map<String, CrawlURL> dbMap;
    private final CrawlURLSerializer serializer = new CrawlURLSerializer();
    
    /*default*/ MapDB(String dbDir) {
        super();
        this.dbDir = new File(dbDir);
    }
    
    public File getDirectory() {
        return dbDir;
    }

    public boolean exists() {
        return isOpen() || dbDir.exists() && dbDir.isDirectory();
    }
    

    public void insert(CrawlURL crawlURL) {
        getMap().put(crawlURL.getUrl(), crawlURL);
    }
    public void delete(CrawlURL crawlURL) {
        if (crawlURL != null) {
            getMap().remove(crawlURL.getUrl());
        }
    }
    public CrawlURL get(String url) {
        CrawlURL crawlURL = getMap().get(url);
        if (crawlURL != null) {
            crawlURL.setUrl(url);
        }
        return crawlURL;
    }
    public boolean isEmpty() {
        return getMap().isEmpty();
    }
    public boolean contains(String url) {
        return getMap().containsKey(url);
    }
    public int size() {
        return getMap().size();
    }
    public CrawlURL getFirst() {
        Iterator<String> it = getMap().keySet().iterator();
        if (it.hasNext()) {
            String url = it.next();
            return get(url);
        }
        return null;
    }
    
    public void compact() {
        if (!isOpen()) {
            open();
        }
        db.compact();
    }

    public boolean isOpen() {
        return db != null;
    }
    public void wipeOut() {
        close();
        try {
            if (dbDir.exists() && dbDir.isDirectory()) {
                FileUtils.forceDelete(dbDir);
            }
        } catch (IOException e) {
            throw new CrawlURLDatabaseException(
                    "Could not delete:" + dbDir, e);
        }
    }
    
    private Map<String, CrawlURL> getMap() {
        if (!isOpen()) {
            open();
        }
        return dbMap;
    }

    public void close() {
        if (isOpen()) {
            db.commit();
            db.close();
        }
    }
    
    public MapDB open() {
        if (isOpen()) {
            return this;
        }
        File dbFile = new File(dbDir.getAbsolutePath() + "/crawldb");
        boolean open = dbDir.exists();
        dbDir.mkdirs();
        db = DBMaker.newFileDB(dbFile)
                .closeOnJvmShutdown()
                .randomAccessFileEnableIfNeeded()
                .journalDisable()
                .make();
        if (open) {
            dbMap = db.getHashMap("map");
        } else {
            dbMap = db.createHashMap("map", null, serializer);
        }
        return this;
    }
    
    public void moveTo(File newDbDir) {
        if (exists()) {
            try {
                FileUtils.deleteDirectory(newDbDir);
                FileUtils.moveDirectory(dbDir, newDbDir);
            } catch (IOException e) {
                throw new CrawlURLDatabaseException(
                     "Could not move '" + dbDir + "' to '" + newDbDir + "'", e);
            }
        }
    }
    
    public void copyTo(MapDB targetDB) {
        for (String url : getMap().keySet()) {
            targetDB.insert(getMap().get(url));
        }
        wipeOut();
    }
}
