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
package com.norconex.collector.http.data.store.impl.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.norconex.collector.core.data.ICrawlData;
import com.norconex.collector.core.data.store.impl.mongo.BaseMongoSerializer;
import com.norconex.collector.http.data.HttpCrawlData;

public class MongoCrawlDataSerializer extends BaseMongoSerializer {

    private static final String FIELD_DEPTH = "depth";
    private static final String FIELD_SITEMAP_LAST_MOD = "sitemapLastMod";
    private static final String FIELD_SITEMAP_CHANGE_FREQ = 
            "sitemapChangeFreq";
    private static final String FIELD_SITEMAP_PRIORITY = "sitemapPriority";
    private static final String FIELD_REFERRER_REFERENCE = "referrerReference";
    private static final String FIELD_REFERRER_LINK_TAG = "referrerLinkTag";
    private static final String FIELD_REFERRER_LINK_TEXT = "referrerLinkText";
    private static final String FIELD_REFERRER_LINK_TITLE = "referrerLinkTitle";
    
    @Override
    public BasicDBObject toDBObject(Stage stage, ICrawlData crawlData) {
        HttpCrawlData data = (HttpCrawlData) crawlData;
        BasicDBObject doc = super.toDBObject(stage, crawlData);
        doc.put(FIELD_DEPTH, data.getDepth());
        doc.put(FIELD_SITEMAP_LAST_MOD, data.getSitemapLastMod());
        doc.put(FIELD_SITEMAP_CHANGE_FREQ, data.getSitemapChangeFreq());
        doc.put(FIELD_SITEMAP_PRIORITY, data.getSitemapPriority());
        doc.put(FIELD_REFERRER_REFERENCE, data.getReferrerReference());
        doc.put(FIELD_REFERRER_LINK_TAG, data.getReferrerLinkTag());
        doc.put(FIELD_REFERRER_LINK_TEXT, data.getReferrerLinkTag());
        doc.put(FIELD_REFERRER_LINK_TITLE, data.getReferrerLinkTitle());
        return doc;
    }

    @Override
    public DBObject getNextQueued(DBCollection collRefs) {
        BasicDBObject whereQuery = 
                new BasicDBObject(FIELD_STAGE, Stage.QUEUED.name());
        BasicDBObject sort = new BasicDBObject(FIELD_DEPTH, 1);
        BasicDBObject newDocument = new BasicDBObject("$set",
              new BasicDBObject(FIELD_STAGE, Stage.ACTIVE.name()));
        return collRefs.findAndModify(whereQuery, sort, newDocument);
    }
    
    @Override
    public ICrawlData fromDBObject(DBObject dbObject) {
        ICrawlData superData = super.fromDBObject(dbObject);
        if (superData == null) {
            return null;
        }
        HttpCrawlData data = new HttpCrawlData(superData);
        data.setDepth((Integer) dbObject.get(FIELD_DEPTH)); 
        data.setSitemapLastMod((Long) dbObject.get(FIELD_SITEMAP_LAST_MOD));
        data.setSitemapChangeFreq(
                (String) dbObject.get(FIELD_SITEMAP_CHANGE_FREQ));
        Double val = (Double) dbObject.get(FIELD_SITEMAP_PRIORITY);
        if (val != null) {
            data.setSitemapPriority(val.floatValue());
        }
        data.setReferrerReference(
                (String) dbObject.get(FIELD_REFERRER_REFERENCE));
        data.setReferrerLinkTag(
                (String) dbObject.get(FIELD_REFERRER_LINK_TAG));
        data.setReferrerLinkText(
                (String) dbObject.get(FIELD_REFERRER_LINK_TEXT));
        data.setReferrerLinkTitle(
                (String) dbObject.get(FIELD_REFERRER_LINK_TITLE));
        return data;
    }
    
    @Override
    public void createIndices(DBCollection referenceCollection,
            DBCollection cachedCollection) {
        super.createIndices(referenceCollection, cachedCollection);
        ensureIndex(referenceCollection, 
                false, FIELD_DEPTH, FIELD_CRAWL_STATE);
    }
}