/* Copyright 2014 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang3.ArrayUtils;

import com.norconex.collector.core.data.ICrawlData;
import com.norconex.collector.core.data.store.impl.jdbc.JDBCCrawlDataStore;
import com.norconex.collector.core.data.store.impl.jdbc.IJDBCSerializer;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;

class JDBCCrawlDataSerializer implements IJDBCSerializer {

    protected static final String ALL_FIELDS = 
            // common attributes:
              "reference, "
            + "parentRootReference, "
            + "isRootParentReference, "
            + "state, "
            + "metaChecksum, "
            + "contentChecksum, "
            // http-specific:
            + "depth, "
            + "sitemapLastMod, "
            + "sitemapChangeFreq, "
            + "sitemapPriority ";
    
    @Override
    public String[] getCreateTableSQLs(String table) {
        String sql = "CREATE TABLE " + table + " ("
                // common attributes:
                + "reference VARCHAR(32672) NOT NULL, "
                + "parentRootReference VARCHAR(32672), "
                + "isRootParentReference BOOLEAN, "
                + "state VARCHAR(256), "
                + "metaChecksum VARCHAR(32672), "
                + "contentChecksum VARCHAR(32672), "
                // http-specific:
                + "depth INTEGER NOT NULL, "
                + "sitemapLastMod BIGINT, "
                + "sitemapChangeFreq VARCHAR(7), "
                + "sitemapPriority FLOAT, "
                + "PRIMARY KEY (reference))";

        String[] sqls = new String[] { sql };
        if (JDBCCrawlDataStore.TABLE_QUEUE.equals(table)) {
            sqls = ArrayUtils.add(sqls, 
                    "CREATE INDEX orderindex ON queue(depth)");
        }
        return sqls;
    }

    @Override
    public String getSelectCrawlDataSQL(String table) {
        return "SELECT " + ALL_FIELDS + "FROM " + table;
    }

    @Override
    public String getDeleteCrawlDataSQL(String table) {
        return "DELETE FROM " + table + " WHERE reference = ?";
    }
    public Object[] getDeleteCrawlDataValues(
            String table, ICrawlData crawlURL) {
        return new Object[] { crawlURL.getReference() };
    }
    
    @Override
    public String getInsertCrawlDataSQL(String table) {
        return "INSERT INTO " + table + "(" + ALL_FIELDS 
                + ") values (?,?,?,?,?,?,?,?,?,?)";
    }
    @Override
    public Object[] getInsertCrawlDataValues(
            String table, ICrawlData crawlData) {
        HttpCrawlData data = (HttpCrawlData) crawlData;
        return new Object[] { 
                data.getReference(),
                data.getParentRootReference(),
                data.isRootParentReference(),
                data.getState().toString(),
                data.getMetaChecksum(),
                data.getContentChecksum(),
                data.getDepth(),
                data.getSitemapLastMod(),
                data.getSitemapChangeFreq(),
                data.getSitemapPriority()
        };
    }
    
    @Override
    public String getNextQueuedCrawlDataSQL() {
        return "SELECT " + ALL_FIELDS 
                + "FROM " + JDBCCrawlDataStore.TABLE_QUEUE
                + " ORDER BY depth";
    }
    @Override
    public Object[] getNextQueuedCrawlDataValues() {
        return null;
    }

    @Override
    public String getCachedCrawlDataSQL() {
        return "SELECT " + ALL_FIELDS 
                + "FROM " + JDBCCrawlDataStore.TABLE_CACHE
                + " WHERE reference = ? ";
    }
    @Override
    public Object[] getCachedCrawlDataValues(String reference) {
        return new Object[] { reference };
    }

    @Override
    public String getReferenceExistsSQL(String table) {
        return "SELECT 1 FROM " + table + " WHERE reference = ?";
    }
    @Override
    public Object[] getReferenceExistsValues(
            String table, String reference) {
        return new Object[] { reference };
    }
    
    @Override
    public ICrawlData toCrawlData(String table, ResultSet rs)
            throws SQLException {
        if (rs == null) {
            return null;
        }
        HttpCrawlData data = new HttpCrawlData();
        data.setReference(rs.getString("reference"));
        data.setParentRootReference(rs.getString("parentRootReference"));
        data.setRootParentReference(rs.getBoolean("isRootParentReference"));
        data.setState(HttpCrawlState.valueOf(rs.getString("state")));
        data.setMetaChecksum(rs.getString("metaChecksum"));
        data.setDocumentChecksum(rs.getString("contentChecksum"));
        data.setDepth(rs.getInt("depth"));
        BigDecimal bigLM = rs.getBigDecimal("sitemapLastMod");
        if (bigLM != null) {
            data.setSitemapLastMod(bigLM.longValue());
        }
        BigDecimal bigP = rs.getBigDecimal("sitemapPriority");
        if (bigP != null) {
            data.setSitemapPriority(bigP.floatValue());
        }
        data.setSitemapChangeFreq(rs.getString("sitemapChangeFreq"));
        return data;
    }
}