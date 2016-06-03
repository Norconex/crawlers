/* Copyright 2010-2016 Norconex Inc.
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
    
    private static final String FIELD_ORIGINAL_REFERENCE = "originalReference";
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
        doc.put(FIELD_ORIGINAL_REFERENCE, data.getOriginalReference());
        doc.put(FIELD_REFERRER_REFERENCE, data.getReferrerReference());
        doc.put(FIELD_REFERRER_LINK_TAG, data.getReferrerLinkTag());
        doc.put(FIELD_REFERRER_LINK_TEXT, data.getReferrerLinkText());
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
        data.setOriginalReference(
                (String) dbObject.get(FIELD_ORIGINAL_REFERENCE));
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
}