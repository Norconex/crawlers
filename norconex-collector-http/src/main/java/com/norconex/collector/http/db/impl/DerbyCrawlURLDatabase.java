package com.norconex.collector.http.db.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ArrayListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.CrawlURL;
import com.norconex.collector.http.db.CrawlURLDatabaseException;
import com.norconex.collector.http.db.ICrawlURLDatabase;

public class DerbyCrawlURLDatabase  implements ICrawlURLDatabase {

    private static final Logger LOG = 
            LogManager.getLogger(DerbyCrawlURLDatabase.class);
    
    private final String dbDir;
    private final DataSource datasource;

    
    public DerbyCrawlURLDatabase(HttpCrawlerConfig config, boolean resume) {
        super();
        LOG.info("Initializing crawl database...");
        this.dbDir = config.getWorkDir().getPath() + "/db";
        this.datasource = createDataSource();
        try {
            ensureTablesExist();
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem creating crawl database.", e);
        }
        if (!resume) {
            LOG.debug("Caching processed URL from last run (if any)...");
            sqlUpdate("RENAME TABLE cache TO temp");
            sqlUpdate("RENAME TABLE processed TO cache");
            sqlUpdate("RENAME TABLE temp TO processed");
            LOG.debug("Cleaning queue database...");
            sqlUpdate("DELETE FROM queue");
            LOG.debug("Cleaning active database...");
            sqlUpdate("DELETE FROM active");
            LOG.debug("Cleaning processed database...");
            sqlUpdate("DELETE FROM processed");
        } else {
            LOG.debug("Resuming: putting active URLs back in the queue...");
            copyURLDepthToQueue("active");
        }
        LOG.info("Done initializing databases.");
    }

    @Override
    public void queue(String url, int depth) {
        sqlUpdate("INSERT INTO queue (url, depth) VALUES (?,?)", url, depth);
    }

    @Override
    public boolean isQueueEmpty() {
        return getQueueSize()  == 0;
    }

    @Override
    public int getQueueSize() {
        return sqlQueryInteger("SELECT count(*) FROM queue");
    }

    @Override
    public boolean isQueued(String url) {
        return sqlQueryInteger("SELECT 1 FROM queue where url = ?", url) > 0;
    }

    @Override
    public CrawlURL next() {
        CrawlURL crawlURL = sqlQueryCrawlURL(
                "SELECT url, depth FROM queue ORDER BY depth");
        if (crawlURL != null) {
            sqlUpdate("INSERT INTO active (url, depth) values (?, ?)",
                    crawlURL.getUrl(), crawlURL.getDepth());
            sqlUpdate("DELETE FROM queue WHERE url = ?", crawlURL.getUrl());
        }
        return crawlURL;
    }

    @Override
    public boolean isActive(String url) {
        return sqlQueryInteger("SELECT 1 FROM active where url = ?", url) > 0;
    }

    @Override
    public int getActiveCount() {
        return sqlQueryInteger("SELECT count(*) FROM active");
    }

    @Override
    public CrawlURL getCached(String url) {
        return sqlQueryCrawlURL(
                "SELECT url, depth, docchecksum, headchecksum, status "
              + "FROM cache WHERE url = ?", url);
    }

    @Override
    public boolean isCacheEmpty() {
        return sqlQueryInteger("SELECT count(*) FROM cache") == 0;
    }

    @Override
    public void processed(CrawlURL crawlURL) {
        sqlUpdate("INSERT INTO processed ("
                + "url, depth, docchecksum, headchecksum, status) "
                + "values (?, ?, ?, ?, ?)",
                crawlURL.getUrl(), crawlURL.getDepth(),
                crawlURL.getDocChecksum(), crawlURL.getHeadChecksum(),
                crawlURL.getStatus().toString());
        sqlUpdate("DELETE FROM active WHERE url = ?", crawlURL.getUrl());
        sqlUpdate("DELETE FROM cache WHERE url = ?", crawlURL.getUrl());
    }

    @Override
    public boolean isProcessed(String url) {
        return sqlQueryInteger(
                "SELECT 1 FROM processed where url = ?", url) > 0;
    }

    @Override
    public int getProcessedCount() {
        return sqlQueryInteger("SELECT count(*) FROM processed");
    }

    @Override
    public void queueCache() {
        copyURLDepthToQueue("cache");
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

    private void copyURLDepthToQueue(String sourceTable) {
        ResultSetHandler<Void> h = new ResultSetHandler<Void>() {
            @Override
            public Void handle(ResultSet rs) throws SQLException {
                while(rs.next()) {
                    queue(rs.getString("url"), rs.getInt("depth"));
                }
                return null;
            }
        };
        try {
            new QueryRunner(datasource).query(
                    "SELECT url, depth FROM " + sourceTable, h);
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem running database query.", e);            
        }
    }
    
    private CrawlURL sqlQueryCrawlURL(String sql, Object... params) {
      if (LOG.isDebugEnabled()) {
          LOG.debug("SQL: " + sql);
      }
      try {
          ResultSetHandler<CrawlURL> h = new ResultSetHandler<CrawlURL>() {
              @Override
              public CrawlURL handle(ResultSet rs) throws SQLException {
                  if (!rs.next()) {
                      return null;
                  }
                  int colCount = rs.getMetaData().getColumnCount();
                  CrawlURL crawlURL = new CrawlURL();
                  crawlURL.setUrl(rs.getString("url"));
                  crawlURL.setDepth(rs.getInt("depth"));
                  if (colCount > 2) {
                      crawlURL.setDocChecksum(rs.getString("docchecksum"));
                      crawlURL.setHeadChecksum(rs.getString("headchecksum"));
                      crawlURL.setStatus(
                              CrawlStatus.valueOf(rs.getString("status")));
                  }
                  return crawlURL;
              }
          };
          return new QueryRunner(datasource).query(sql, h, params);
      } catch (SQLException e) {
          throw new CrawlURLDatabaseException(
                  "Problem running database query.", e);            
      }
  }
    
    private int sqlQueryInteger(String sql, Object... params) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("SQL: " + sql);
        }
        try {
            Integer value = new QueryRunner(datasource).query(
                    sql, new ScalarHandler<Integer>(), params);
            if (value == null) {
                return 0;
            }
            return value;
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem running database query.", e);            
        }
    }
    private void sqlUpdate(String sql, Object... params) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("SQL: " + sql);
        }
        try {
            new QueryRunner(datasource).update(sql, params);
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem updating database.", e);            
        }
    }
    

    private DataSource createDataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("org.apache.derby.jdbc.EmbeddedDriver");
        ds.setUrl("jdbc:derby:" + dbDir + ";create=true");
        ds.setDefaultAutoCommit(true);
        return ds;
    }

    private void ensureTablesExist() throws SQLException {
        List<Object[]> tables = new ArrayListHandler().handle(
                datasource.getConnection().getMetaData().getTables(
                        null, null, null, new String[]{"TABLE"}));
        if (tables.size() == 4) {
            LOG.debug("    Re-using existing tables.");
            return;
        }
        LOG.debug("    Creating new crawl tables...");
        sqlUpdate("CREATE TABLE queue ("
                + "url VARCHAR(32672) NOT NULL, depth INTEGER NOT NULL, "
                + "PRIMARY KEY (url))");
        sqlUpdate("CREATE INDEX orderindex ON queue(depth)");
        sqlUpdate("CREATE TABLE active ("
                + "url VARCHAR(32672) NOT NULL, depth INTEGER NOT NULL, "
                + "PRIMARY KEY (url))");
        sqlUpdate("CREATE TABLE processed ("
                + "url VARCHAR(32672) NOT NULL, "
                + "depth INTEGER NOT NULL, "
                + "docChecksum VARCHAR(32672), "
                + "headChecksum VARCHAR(32672), "
                + "status VARCHAR(10), "
                + "PRIMARY KEY (url))");
        sqlUpdate("CREATE TABLE cache ("
                + "url VARCHAR(32672) NOT NULL, "
                + "depth INTEGER NOT NULL, "
                + "docChecksum VARCHAR(32672), "
                + "headChecksum VARCHAR(32672), "
                + "status VARCHAR(10), "
                + "PRIMARY KEY (url))");
    }
}
