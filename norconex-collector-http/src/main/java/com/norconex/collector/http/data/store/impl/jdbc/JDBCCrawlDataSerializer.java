/* Copyright 2010-2017 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl.jdbc;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.data.ICrawlData;
import com.norconex.collector.core.data.store.impl.jdbc.JDBCCrawlDataStore;
import com.norconex.collector.core.data.store.impl.jdbc.IJDBCSerializer;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.commons.lang.file.ContentType;

public class JDBCCrawlDataSerializer implements IJDBCSerializer {

    private static final int TAG_MAX_LENGTH = 1024;
    private static final int TEXT_MAX_LENGTH = 2048;
    private static final int TITLE_MAX_LENGTH = 2048;
    
    protected static final String ALL_FIELDS = 
            // common attributes:
              "reference, "
            + "parentRootReference, "
            + "isRootParentReference, "
            + "state, "
            + "metaChecksum, "
            + "contentChecksum, "
            + "contentType, "
            + "crawlDate, "
            
            // http-specific:
            + "depth, "
            + "sitemapLastMod, "
            + "sitemapChangeFreq, "
            + "sitemapPriority, "
            + "originalReference, "
            + "referrerReference, "
            + "referrerLinkTag, "
            + "referrerLinkText, "
            + "referrerLinkTitle, "
            + "referencedUrls, "
            + "redirectTrail ";
    
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
                + "contentType VARCHAR(256), "
                + "crawlDate BIGINT, "
                
                // http-specific:
                + "depth INTEGER NOT NULL, "
                + "sitemapLastMod BIGINT, "
                + "sitemapChangeFreq VARCHAR(7), "
                + "sitemapPriority FLOAT, "
                + "originalReference VARCHAR(32672), "
                + "referrerReference VARCHAR(32672), "
                + "referrerLinkTag VARCHAR(1024), "
                + "referrerLinkText VARCHAR(2048), "
                + "referrerLinkTitle VARCHAR(2048), "
                + "referencedUrls CLOB, "
                + "redirectTrail CLOB, "
                
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
                + ") values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }
    @Override
    public Object[] getInsertCrawlDataValues(
            String table, ICrawlData crawlData) {
        String contentType = null;
        if (crawlData.getContentType() != null) {
            contentType = crawlData.getContentType().toString();
        }
        long crawlDate = 0;
        if (crawlData.getCrawlDate() != null) {
            crawlDate = crawlData.getCrawlDate().getTime();
        }        
        HttpCrawlData data = (HttpCrawlData) crawlData;
        return new Object[] { 
                data.getReference(),
                data.getParentRootReference(),
                data.isRootParentReference(),
                data.getState().toString(),
                data.getMetaChecksum(),
                data.getContentChecksum(),
                contentType,
                crawlDate,
                
                data.getDepth(),
                data.getSitemapLastMod(),
                data.getSitemapChangeFreq(),
                data.getSitemapPriority(),
                data.getOriginalReference(),
                data.getReferrerReference(),
                StringUtils.substring(
                        data.getReferrerLinkTag(), 0, TAG_MAX_LENGTH),
                StringUtils.substring(
                        data.getReferrerLinkText(), 0, TEXT_MAX_LENGTH),
                StringUtils.substring(
                        data.getReferrerLinkTitle(), 0, TITLE_MAX_LENGTH),
                StringUtils.join(data.getReferencedUrls(), (char) 007),
                StringUtils.join(data.getRedirectTrail(), (char) 007)
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
        data.setContentChecksum(rs.getString("contentChecksum"));
        String contentType = rs.getString("contentType");
        if (StringUtils.isNoneBlank(contentType)) {
            data.setContentType(ContentType.valueOf(contentType));
        }
        long crawlDate = rs.getLong("crawlDate");
        if (crawlDate > 0) {
            data.setCrawlDate(new Date(crawlDate));
        }        
        
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
        data.setOriginalReference(rs.getString("originalReference"));
        data.setReferrerReference(rs.getString("referrerReference"));
        data.setReferrerLinkTag(rs.getString("referrerLinkTag"));
        data.setReferrerLinkText(rs.getString("referrerLinkText"));
        data.setReferrerLinkTitle(rs.getString("referrerLinkTitle"));
        
        try {
            Reader refUrlsReader = rs.getCharacterStream("referencedUrls");
            if (refUrlsReader != null) {
                data.setReferencedUrls(StringUtils.split(
                        IOUtils.toString(refUrlsReader), (char) 007));
            }
        } catch (IOException e) {
            throw new SQLException(
                    "Could not read referencedUrls character stream.", e);
        }
        try {
            Reader refRdrReader = rs.getCharacterStream("redirectTrail");
            if (refRdrReader != null) {
                data.setRedirectTrail(StringUtils.split(
                        IOUtils.toString(refRdrReader), (char) 007));
            }
        } catch (IOException e) {
            throw new SQLException(
                    "Could not read redirectTrail character stream.", e);
        }
        return data;
    }
}