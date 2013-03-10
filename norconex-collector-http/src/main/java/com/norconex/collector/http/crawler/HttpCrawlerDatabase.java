package com.norconex.collector.http.crawler;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.Properties;

import org.entityfs.support.log.LogAdapterHolder;
import org.entityfs.support.log.StdOutLogAdapter;
import org.entityfs.util.io.ReadWritableFileAdapter;
import org.helidb.Cursor;
import org.helidb.Database;
import org.helidb.backend.cache.lru.LruCacheBackend;
import org.helidb.backend.heap.HeapBackendBuilder;
import org.helidb.backend.index.bplus.BPlusTreeIndexBackendBuilder;
import org.helidb.impl.simple.SimpleDatabase;
import org.helidb.lang.hasher.StringToBigIntegerHasher;
import org.helidb.lang.hasher.StringToLongHasher;
import org.helidb.lang.serializer.FixedSizeBigIntegerNullSerializer;
import org.helidb.lang.serializer.LongNullSerializer;
import org.helidb.lang.serializer.LongSerializer;
import org.helidb.lang.serializer.StringSerializer;
import org.helidb.util.bplus.BPlusTree;
import org.helidb.util.bplus.FileBackedNodeRepositoryBuilder;
import org.helidb.util.bplus.FixedSizeNodeSizeStrategy;
import org.helidb.util.bplus.LruCacheNodeRepository;

import com.norconex.commons.lang.io.FileUtil;
import com.norconex.collector.http.HttpCollectorException;

public class HttpCrawlerDatabase {

    private static final long COMPACT_THRESHOLD = 10000;
    
    private static final String DBNAME_PROCESSED = "processed";
    private static final String DBNAME_QUEUED = "queued";
    private static final String DBNAME_ACTIVE = "active";
    private static final String DBNAME_LASTPROCESSED = "lastProcessed";
    
    private final HttpCrawlerConfig config;
    private final Database<String, String> queuedURLs;
    private final Database<String, String> activeURLs;
    private final Database<String, String> processedURLs;
    private final Database<String, String> lastProcessedURLs;
    private final String dbWorkDir;
    private long processedCount;
    
    
    //TODO have database for rejected URLs?
    
    public HttpCrawlerDatabase(HttpCrawlerConfig config, boolean resume) {
        super();
        this.config = config;
        this.dbWorkDir = config.getWorkDir().getPath() + "/db/";
        new File(dbWorkDir).mkdirs();

        if (resume) {
            if (exists(DBNAME_LASTPROCESSED)) {
                lastProcessedURLs = createLRUDatabase(DBNAME_LASTPROCESSED);
            } else {
                lastProcessedURLs = null;
            }
        } else {
            lastProcessedURLs = replaceLastProcessedDBWithProcessedDB();
        }
        this.processedURLs = createLRUDatabase(DBNAME_PROCESSED);
        this.queuedURLs = createSimpleDatabase(DBNAME_QUEUED);
        this.activeURLs = createSimpleDatabase(DBNAME_ACTIVE);
        
        if (resume) {
            moveActiveDBURLsToQueuedDB(activeURLs, queuedURLs);
        } else {
            this.activeURLs.clear();
            this.activeURLs.compact();
            this.queuedURLs.clear();
            this.queuedURLs.compact();
            this.processedURLs.clear();
            this.processedURLs.compact();
        }
    }

    private Database<String, String> replaceLastProcessedDBWithProcessedDB() {
        File processedDBFile = getDBFile(DBNAME_PROCESSED);
        File processedDBIdxFile = getDBIndexFile(DBNAME_PROCESSED);
    	if (processedDBFile.exists()) {
    		try {
				FileUtil.moveFile(processedDBFile, 
						getDBFile(DBNAME_LASTPROCESSED));
				FileUtil.moveFile(processedDBIdxFile, 
	    				getDBIndexFile(DBNAME_LASTPROCESSED));
			} catch (IOException e) {
				throw new HttpCollectorException(
						"Could not move last processed URLs.", e);
			}
    		return createLRUDatabase(DBNAME_LASTPROCESSED);
    	}
    	return null;
    }
    private void moveActiveDBURLsToQueuedDB(
            Database<String, String> activeURLs, 
            Database<String, String> queuedURLs) {
        while (!activeURLs.isEmpty()) {
            Cursor<String, String> cursor = activeURLs.firstRecord();
            if (cursor != null) {
                String url = cursor.getKey();
                String depth = cursor.getValue();
                queuedURLs.insertOrUpdate(url, depth);
                activeURLs.delete(url);
            }
        }
    }
    
    synchronized public boolean hasActiveURLs() {
        return !activeURLs.isEmpty();
    }
    synchronized public boolean hasQueuedURLs() {
        return !queuedURLs.isEmpty();
    }
    
    private boolean exists(String dbName) {
        return getDBFile(dbName).exists();
    }
    
    synchronized public boolean isLastProcessedURLsEmpty() {
    	if (lastProcessedURLs == null) {
    		return true;
    	}
    	return lastProcessedURLs.isEmpty();
    }
    
    synchronized public void copyLastProcessedURLBatchToQueue(int batchSize) {
    	if (lastProcessedURLs == null) {
    		return;
    	}
    	for (int i = 0; i < batchSize; i++) {
            Cursor<String, String> cursor = lastProcessedURLs.firstRecord();
            if (cursor == null) {
            	break;
            }
            String url = cursor.getKey();
            String value = cursor.getValue();
            URLMemento memento = URLMemento.fromString(value);
            addQueuedURL(url, memento.depth);
		}
    }
    
//    public boolean hasQueuedURL() {
//        
//        if (!queuedURLs.isEmpty() && queuedURLs.firstRecord() == null) {
//            System.err.println("PROBLEM!!");
//        }
//        
//        
//        return !queuedURLs.isEmpty();
//    }
    synchronized public void addQueuedURL(String url, int depth) {
        //TODO have value being depth.  First check if the same URL 
        //already exists.  If so, keep the smallest depth.
        
        queuedURLs.insertOrUpdate(url, Integer.toString(depth));
    }
    synchronized public QueuedURL getNextQueuedURL() {
        Cursor<String, String> cursor = queuedURLs.firstRecord();
        if (cursor != null) {
            QueuedURL url = new QueuedURL();
            url.url = cursor.getKey();
            String depth = cursor.getValue();
            url.depth = Integer.parseInt(depth); //TODO have it return int instead
            
            activeURLs.insertOrUpdate(url.url, depth);
            queuedURLs.delete(url.url);
            return url;
        }
        return null;
    }
    
    synchronized public URLMemento getURLMemento(String processedURL) {
    	if (lastProcessedURLs != null) {
        	String str = lastProcessedURLs.get(processedURL);
        	if (str != null) {
        		return URLMemento.fromString(str);
        	}
    	}
    	return null;
    }

    synchronized public boolean shouldDeleteURL(String url, URLMemento memento) {
        if (lastProcessedURLs == null) {
        	return false;
        }
        URLMemento lastMemento = getURLMemento(url);
        if (lastMemento == null) {
        	return false;
        }
        CrawlStatus cur = memento.getStatus();
        CrawlStatus last = lastMemento.getStatus();
    	return cur != CrawlStatus.OK && cur != CrawlStatus.UNMODIFIED
    			&& (last == CrawlStatus.OK ||  last == CrawlStatus.UNMODIFIED);
    			//TODO ERROR too?
    }
    synchronized public void addProcessedURL(String url, URLMemento memento) {
        processedURLs.insertOrUpdate(url, memento.toString());
        activeURLs.delete(url);
        //queuedURLs.delete(url);
        if (lastProcessedURLs != null) {
        	lastProcessedURLs.delete(url);
        }

        //Compact the queue which probably has many deletion as URLs
        //are marked as "processed".
        processedCount++;
        if (processedCount % COMPACT_THRESHOLD == 0) {
            queuedURLs.compact();
            if (lastProcessedURLs != null) {
            	lastProcessedURLs.compact();
            }
        }
    }
    synchronized public boolean isProcessedURL(String url) {
        Cursor<String, String> cursor = processedURLs.find(url);
        return cursor != null;
    }
    synchronized public boolean isQueuedURL(String url) {
        Cursor<String, String> cursor = queuedURLs.find(url);
        return cursor != null;
    }
    synchronized public boolean isActiveURL(String url) {
        Cursor<String, String> cursor = activeURLs.find(url);
        return cursor != null;
    }

    
    synchronized public int getQueuedURLCount() {
        return queuedURLs.size();
    }
    synchronized public int getProcessedURLCount() {
        return processedURLs.size();
    }
    
    private Database<String, String> createSimpleDatabase(String dbName) {
     // Create a new SimpleDatabase that uses a HeapBackend
     // with a BPlusTreeIndexBackend to speed up searches in the database.
     // The database will store String keys and values.
     //  - f is a File where the database data will be stored.
     //  - indf is a File where the B+ Tree index data will be stored.

     // A LogAdapter that logs to stdout and stderr.
     LogAdapterHolder lah = 
       new LogAdapterHolder(
         new StdOutLogAdapter());

     Database<String, String> db = 
       new SimpleDatabase<String, String, Long>(
         // Use a BPlusTreeIndexBackendBuilder object to build the 
         // BPlusTreeIndexBackend.
         new BPlusTreeIndexBackendBuilder<String, Long>().
           // Hash the keys to a long value (eight bytes).
           // Beware of the birthday paradox! Use sufficiently large hash values.
           setKeyHasher(StringToLongHasher.INSTANCE).
           create(
             // Use a HeapBackendBuilder.
             new HeapBackendBuilder<String, String>().
               setKeySerializer(StringSerializer.INSTANCE).
               setValueSerializer(StringSerializer.INSTANCE).
               // Adapt f to a ReadWritableFile.
               create(new ReadWritableFileAdapter(getDBFile(dbName))),
             // The BPlusTree is <Long, Long> since it has Long keys (the key
             // hashes) and Long values (the positions in the HeapBackend).
             new BPlusTree<Long,Long>(
               // Use a LRU caching NodeRepository.
               new LruCacheNodeRepository<Long, Long>(
                 // Use a FileBackedNodeRepositoryBuilder to build the
                 // node repository.
                 new FileBackedNodeRepositoryBuilder<Long, Long>().
                   // Use a fixed node size of 4096 bytes. This is a common file
                   // system allocation size.
                   setNodeSizeStrategy(new FixedSizeNodeSizeStrategy(4096)).
                   // Have to use a null-aware serializer for the keys.
                   // This particular serializer uses Long.MIN_VALUE to represent
                   // null, which means that that key value cannot be used at all in
                   // the database.
                   setKeySerializer(LongNullSerializer.INSTANCE).
                   setValueSerializer(LongSerializer.INSTANCE).
                   // An internal pointer size of 3 bytes sets the maximum size of
                   // the B+ Tree to 2^(3*8) = 16777216 bytes.
                   setInternalPointerSize(3).
                   setLogAdapterHolder(lah).
                   create(new ReadWritableFileAdapter(getDBIndexFile(dbName)
                   ), false),
                 // Use a cache size of 16 tree nodes.
                 16),
               // We don't have to use a key Comparator (null) since the tree's keys
               // are Comparable themselves (Long:s). 
               null, lah)),
         lah);

         return db;
    }
    
    
    private Database <String, String> createLRUDatabase(String dbName) {
        
     // Create a new SimpleDatabase that uses a HeapBackend
     // with a BPlusTreeIndexBackend and a LruCacheBackend
     // to speed up searches in the database.
     // The database will store String keys and values.
     //  - f is a File where the database data will be stored.
     //  - indf is a File where the B+ Tree index data will be stored.

     // A LogAdapter that logs to stdout and stderr.
     LogAdapterHolder lah = 
       new LogAdapterHolder(
         new StdOutLogAdapter());

     Database<String, String> db = 
       new SimpleDatabase<String, String, Long>(
         new LruCacheBackend<String, String, Long>(
           // Use a BPlusTreeIndexBackendBuilder object to build the 
           // BPlusTreeIndexBackend.
           new BPlusTreeIndexBackendBuilder<String, BigInteger>().
             // Hash the strings to a twelve-byte value, with the most significant 
             // bit in the most significant byte removed. The most significant bit is
             // used to represent a null value by the FixedSizeBigIntegerNullSerializer
             // used below instead.
             setKeyHasher(new StringToBigIntegerHasher(12, 7)).
             create(
               // Use a HeapBackendBuilder.
               new HeapBackendBuilder<String, String>().
                 setKeySerializer(StringSerializer.INSTANCE).
                 setValueSerializer(StringSerializer.INSTANCE).
                 // Adapt f to a ReadWritableFile.
                 create(new ReadWritableFileAdapter(getDBFile(dbName))),
               // The BPlusTree is <Long, BigInteger> since it has BigInteger keys
               // (the key hashes) and Long values (the records' positions in the
               // HeapBackend).
               new BPlusTree<BigInteger, Long>(
                 // Use a LRU caching NodeRepository.
                 new LruCacheNodeRepository<BigInteger, Long>(
                   // Use a FileBackedNodeRepositoryBuilder to build the
                   // node repository.
                   new FileBackedNodeRepositoryBuilder<BigInteger, Long>().
                     // Use a fixed node size of 4096 bytes.
                     setNodeSizeStrategy(new FixedSizeNodeSizeStrategy(4096)).
                     // Have to use a null-aware serializer for the keys.
                     //
                     // This serializer uses a null value that has the most 
                     // significant bit in the most significant byte set. We chose to
                     // not use that particular bit for our hashes when we created
                     // the StringToBigIntegerHasher above. By doing that we
                     // have made sure that null won't collide with any real value.
                     //
                     // Set the total data size of the serializer to 13 bytes. The
                     // first byte of the serialized data is the length of the
                     // BigInteger data.
                     setKeySerializer(new FixedSizeBigIntegerNullSerializer(13)).
                     setValueSerializer(LongSerializer.INSTANCE).
                     // An internal pointer size of 3 bytes sets the maximum size of
                     // the B+ Tree to 2^(3*8) = 16777216 bytes.
                     setInternalPointerSize(3).
                     setLogAdapterHolder(lah).
                     create(new ReadWritableFileAdapter(
                    		 getDBIndexFile(dbName)), false),
                   // Use a cache size of 16 tree nodes.
                   16),
                 // We don't have to use a key Comparator (null) since the tree's keys
                 // are Comparable themselves (BigInteger:s). 
                 null, lah)),
           // The cache size and the negative cache size
           false, 256, 32),
         lah);
         return db;
    }
    
    private File getDBFile(String dbName) {
    	return new File(dbWorkDir + config.getId() + "-" + dbName + ".db");
    }
    private File getDBIndexFile(String dbName) {
    	return new File(dbWorkDir + config.getId() + "-" + dbName + "-idx.db");
    }
    
    
    @Override
    protected void finalize() throws Throwable {
        processedURLs.compact();
        processedURLs.close();
        queuedURLs.compact();
        queuedURLs.close();
        super.finalize();
    }
    
    public class QueuedURL {
        private int depth;
        private String url;
        public int getDepth() {
            return depth;
        }
        public String getUrl() {
            return url;
        }
    }
    
    
    public static class URLMemento{
    	private CrawlStatus status;
    	final private int depth;
    	private String headChecksum;
    	private String docChecksum;
		public URLMemento(CrawlStatus status, int depth) {
			super();
			this.status = status;
			this.depth = depth;
		}
		public int getDepth() {
			return depth;
		}
		public String getHeadChecksum() {
			return headChecksum;
		}
		public void setHeadChecksum(String headChecksum) {
			this.headChecksum = headChecksum;
		}
		public String getDocChecksum() {
			return docChecksum;
		}
		public void setDocChecksum(String docChecksum) {
			this.docChecksum = docChecksum;
		}
		public CrawlStatus getStatus() {
			return status;
		}
		public void setStatus(CrawlStatus status) {
			this.status = status;
		}
		@Override
		public String toString() {
			Properties props = new Properties();
			props.setProperty("s", status.toString());
			if (headChecksum != null) {
				props.setProperty("hc", headChecksum);
			}
			if (docChecksum != null) {
                props.setProperty("dc", docChecksum);
			}
			props.setProperty("d", Integer.toString(depth));
			StringWriter w = new StringWriter();
			try {
				props.store(w, null);
			} catch (IOException e) {
				throw new HttpCollectorException(
						"Could not persist ProcessedURL: " 
								+ props.toString(), e);
			}
			return w.toString();
		}
		public static URLMemento fromString(String str) {
			Properties props = new Properties();
			StringReader r = new StringReader(str);
			try {
				props.load(r);
				r.close();
			} catch (IOException e) {
				throw new HttpCollectorException(
						"Could not parse processed URL from String: " + str, e);
			}
			URLMemento url = new URLMemento(
					CrawlStatus.valueOf(props.getProperty("s")),
					Integer.parseInt(props.getProperty("d")));
			url.setDocChecksum(props.getProperty("dc"));
			url.setHeadChecksum(props.getProperty("hc"));
			return url;
		}
    }
}
