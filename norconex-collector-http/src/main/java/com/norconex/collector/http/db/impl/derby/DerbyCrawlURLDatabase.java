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
package com.norconex.collector.http.db.impl.derby;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ArrayListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.crawler.CrawlURL;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.CrawlURLDatabaseException;
import com.norconex.collector.http.db.ICrawlURLDatabase;

public class DerbyCrawlURLDatabase  implements ICrawlURLDatabase {

    private static final Logger LOG = 
            LogManager.getLogger(DerbyCrawlURLDatabase.class);
    
    private static final int NUMBER_OF_TABLES = 5;
    
    private final String dbDir;
    private final DataSource datasource;

    
    public DerbyCrawlURLDatabase(HttpCrawlerConfig config, boolean resume) {
        super();
        LOG.info("Initializing crawl database...");
        this.dbDir = config.getWorkDir().getPath() + "/crawldb/db";
        
        System.setProperty("derby.system.home", 
                config.getWorkDir().getPath() + "/crawldb/log");
        
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
            LOG.debug("Cleaning sitemap database...");
            sqlUpdate("DELETE FROM sitemap");
        } else {
            LOG.debug("Resuming: putting active URLs back in the queue...");
            copyURLDepthToQueue("active");
            LOG.debug("Cleaning active database...");
            sqlUpdate("DELETE FROM active");
        }
        LOG.info("Done initializing databases.");
    }

    @Override
    public final synchronized void queue(CrawlURL unUrl) {
        sqlUpdate("INSERT INTO queue "
                + "  (url, depth, smLastMod, smChangeFreq, smPriority) "
                + "VALUES (?,?,?,?,?) ", 
                unUrl.getUrl(), unUrl.getDepth(), 
                unUrl.getSitemapLastMod(), 
                unUrl.getSitemapChangeFreq(), unUrl.getSitemapPriority());
    }

    @Override
    public final synchronized boolean isQueueEmpty() {
        return getQueueSize()  == 0;
    }

    @Override
    public final synchronized int getQueueSize() {
        return sqlQueryInteger("SELECT count(*) FROM queue");
    }

    @Override
    public final synchronized boolean isQueued(String url) {
        return sqlQueryInteger("SELECT 1 FROM queue where url = ?", url) > 0;
    }

    @Override
    public final synchronized CrawlURL next() {
        CrawlURL crawlURL = sqlQueryCrawlURL(false, "queue", null, "depth");
        if (crawlURL != null) {
            sqlUpdate("INSERT INTO active "
                    + "(url, depth, smLastMod, smChangeFreq, smPriority) "
                    + " values (?,?,?,?,?)",
                    crawlURL.getUrl(), crawlURL.getDepth(),  
                    crawlURL.getSitemapLastMod(),
                    crawlURL.getSitemapChangeFreq(),
                    crawlURL.getSitemapPriority());
            sqlUpdate("DELETE FROM queue WHERE url = ?", crawlURL.getUrl());
        }
        return crawlURL;
    }

    @Override
    public final synchronized boolean isActive(String url) {
        return sqlQueryInteger("SELECT 1 FROM active where url = ?", url) > 0;
    }

    @Override
    public final synchronized int getActiveCount() {
        return sqlQueryInteger("SELECT count(*) FROM active");
    }

    @Override
    public synchronized CrawlURL getCached(String url) {
        return sqlQueryCrawlURL(true, "cache", "url = ?", null, url);
    }

    @Override
    public final synchronized boolean isCacheEmpty() {
        return sqlQueryInteger("SELECT count(*) FROM cache") == 0;
    }

    @Override
    public final synchronized void processed(CrawlURL crawlURL) {
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
    public final synchronized boolean isProcessed(String url) {
        return sqlQueryInteger(
                "SELECT 1 FROM processed where url = ?", url) > 0;
    }

    @Override
    public final synchronized int getProcessedCount() {
        return sqlQueryInteger("SELECT count(*) FROM processed");
    }

    @Override
    public final synchronized void queueCache() {
        copyURLDepthToQueue("cache");
    }

    @Override
    public final synchronized boolean isVanished(CrawlURL crawlURL) {
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
                    CrawlURL crawlURL = toCrawlURL(rs);
                    if (crawlURL != null) {
                        queue(crawlURL);
                    }
                }
                return null;
            }
        };
        try {
            new QueryRunner(datasource).query(
                    "SELECT url, depth, smLastMod, smChangeFreq, smPriority "
                  + "FROM " + sourceTable, h);
        } catch (SQLException e) {
            throw new CrawlURLDatabaseException(
                    "Problem running database query.", e);            
        }
    }
    
    private CrawlURL sqlQueryCrawlURL(boolean selectAll, String table, 
            String where, String order, Object... params) {
      try {
          ResultSetHandler<CrawlURL> h = new ResultSetHandler<CrawlURL>() {
              @Override
              public CrawlURL handle(ResultSet rs) throws SQLException {
                  if (!rs.next()) {
                      return null;
                  }
                  int colCount = rs.getMetaData().getColumnCount();
                  CrawlURL crawlURL = new CrawlURL(
                          rs.getString("url"), rs.getInt("depth"));
                  crawlURL.setSitemapChangeFreq(
                          rs.getString("smChangeFreq"));
                  BigDecimal bigP = rs.getBigDecimal("smPriority");
                  if (bigP != null) {
                      crawlURL.setSitemapPriority(bigP.floatValue());
                  }
                  BigDecimal bigLM = rs.getBigDecimal("smLastMod");
                  if (bigLM != null) {
                      crawlURL.setSitemapLastMod(bigLM.longValue());
                  }
                  if (colCount > 5) {
                      crawlURL.setDocChecksum(rs.getString("docchecksum"));
                      crawlURL.setHeadChecksum(rs.getString("headchecksum"));
                      crawlURL.setStatus(
                              CrawlStatus.valueOf(rs.getString("status")));
                  }
                  return crawlURL;
              }
          };
          String sql = "SELECT url, depth, smLastMod, smChangeFreq, "
                     + "smPriority ";
          if (selectAll) {
              sql += ", docchecksum, headchecksum, status ";
          }
          sql += "FROM " + table;
          if (StringUtils.isNotBlank(where)) {
              sql += " WHERE " + where;
          }
          if (StringUtils.isNotBlank(order)) {
              sql += " ORDER BY " + order;
          }
          if (LOG.isDebugEnabled()) {
              LOG.debug("SQL: " + sql);
          }
          return new QueryRunner(datasource).query(sql, h, params);
      } catch (SQLException e) {
          throw new CrawlURLDatabaseException(
                  "Problem running database query.", e);            
      }
    }
    
    private CrawlURL toCrawlURL(ResultSet rs) throws SQLException {
        if (rs == null) {
            return null;
        }
        int colCount = rs.getMetaData().getColumnCount();
        CrawlURL crawlURL = new CrawlURL(
                rs.getString("url"), rs.getInt("depth"));
        if (colCount > 2) {
            crawlURL.setSitemapChangeFreq(
                    rs.getString("smChangeFreq"));
            BigDecimal bigP = rs.getBigDecimal("smPriority");
            if (bigP != null) {
                crawlURL.setSitemapPriority(bigP.floatValue());
            }
            BigDecimal bigLM = rs.getBigDecimal("smLastMod");
            if (bigLM != null) {
                crawlURL.setSitemapLastMod(bigLM.longValue());
            }
            if (colCount > 5) {
                crawlURL.setDocChecksum(rs.getString("docchecksum"));
                crawlURL.setHeadChecksum(rs.getString("headchecksum"));
                crawlURL.setStatus(
                        CrawlStatus.valueOf(rs.getString("status")));
            }
        }
        return crawlURL;
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
            if (e.getErrorCode() == 30000) {
                LOG.debug("Already exists in table. SQL Error:" 
                        + e.getMessage());
            } else {
                throw new CrawlURLDatabaseException(
                        "Problem updating database.", e);            
            }
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
        if (tables.size() == NUMBER_OF_TABLES) {
            LOG.debug("    Re-using existing tables.");
            return;
        }
        LOG.debug("    Creating new crawl tables...");
        sqlUpdate("CREATE TABLE queue ("
                + "url VARCHAR(32672) NOT NULL, "
                + "depth INTEGER NOT NULL, "
                + "smLastMod BIGINT, "
                + "smChangeFreq VARCHAR(7), "
                + "smPriority FLOAT, "
                + "PRIMARY KEY (url))");
        sqlUpdate("CREATE INDEX orderindex ON queue(depth)");
        sqlUpdate("CREATE TABLE active ("
                + "url VARCHAR(32672) NOT NULL, "
                + "depth INTEGER NOT NULL, "
                + "smLastMod BIGINT, "
                + "smChangeFreq VARCHAR(7), "
                + "smPriority FLOAT, "
                + "PRIMARY KEY (url))");
        sqlUpdate("CREATE TABLE processed ("
                + "url VARCHAR(32672) NOT NULL, "
                + "depth INTEGER NOT NULL, "
                + "smLastMod BIGINT, "
                + "smChangeFreq VARCHAR(7), "
                + "smPriority FLOAT, "
                + "docChecksum VARCHAR(32672), "
                + "headChecksum VARCHAR(32672), "
                + "status VARCHAR(10), "
                + "PRIMARY KEY (url))");
        sqlUpdate("CREATE TABLE cache ("
                + "url VARCHAR(32672) NOT NULL, "
                + "depth INTEGER NOT NULL, "
                + "smLastMod BIGINT, "
                + "smChangeFreq VARCHAR(7), "
                + "smPriority FLOAT, "
                + "docChecksum VARCHAR(32672), "
                + "headChecksum VARCHAR(32672), "
                + "status VARCHAR(10), "
                + "PRIMARY KEY (url))");
        sqlUpdate("CREATE TABLE sitemap ("
                + "urlroot VARCHAR(32672) NOT NULL, "
                + "PRIMARY KEY (urlroot))");
    }

    @Override
    public synchronized void sitemapResolved(String urlRoot) {
        sqlUpdate("INSERT INTO sitemap (urlroot) VALUES (?) ", urlRoot);
    }

    @Override
    public synchronized boolean isSitemapResolved(String urlRoot) {
        return sqlQueryInteger(
                "SELECT 1 FROM sitemap where urlroot = ?", urlRoot) > 0;
    }
}
