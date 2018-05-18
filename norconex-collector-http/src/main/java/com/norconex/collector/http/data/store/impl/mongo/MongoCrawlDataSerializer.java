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
package com.norconex.collector.http.data.store.impl.mongo;

import static com.mongodb.client.model.Filters.eq;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
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

    /** @since 2.6.0 */
    private static final String FIELD_REFERENCED_URLS = "referencedUrls";
    /** @since 2.8.0 */
    private static final String FIELD_REDIRECT_TRAIL = "redirectTrail";

    @Override
    public Document toDocument(Stage stage, ICrawlData crawlData) {
        HttpCrawlData data = (HttpCrawlData) crawlData;
        Document doc = super.toDocument(stage, crawlData);
        doc.put(FIELD_DEPTH, data.getDepth());
        doc.put(FIELD_SITEMAP_LAST_MOD, data.getSitemapLastMod());
        doc.put(FIELD_SITEMAP_CHANGE_FREQ, data.getSitemapChangeFreq());
        doc.put(FIELD_SITEMAP_PRIORITY, data.getSitemapPriority());
        doc.put(FIELD_ORIGINAL_REFERENCE, data.getOriginalReference());
        doc.put(FIELD_REFERRER_REFERENCE, data.getReferrerReference());
        doc.put(FIELD_REFERRER_LINK_TAG, data.getReferrerLinkTag());
        doc.put(FIELD_REFERRER_LINK_TEXT, data.getReferrerLinkText());
        doc.put(FIELD_REFERRER_LINK_TITLE, data.getReferrerLinkTitle());
        if (ArrayUtils.isNotEmpty(data.getReferencedUrls())) {
            doc.put(FIELD_REFERENCED_URLS, 
                    Arrays.asList(data.getReferencedUrls()));
        }
        if (ArrayUtils.isNotEmpty(data.getRedirectTrail())) {
            doc.put(FIELD_REDIRECT_TRAIL, 
                    Arrays.asList(data.getRedirectTrail()));
        }
        return doc;
    }

    @Override
    public Document getNextQueued(MongoCollection<Document> collRefs) {
        Document sort = new Document(FIELD_DEPTH, 1);
        Document newDocument = new Document(
                "$set", new Document(FIELD_STAGE, Stage.ACTIVE.name()));
        return collRefs.findOneAndUpdate(
                eq(FIELD_STAGE, Stage.QUEUED.name()), newDocument,
                new FindOneAndUpdateOptions().sort(sort));
    }
    
    @Override
    public ICrawlData fromDocument(Document doc) {
        ICrawlData superData = super.fromDocument(doc);
        if (superData == null) {
            return null;
        }
        HttpCrawlData data = new HttpCrawlData(superData);
        data.setDepth(doc.getInteger(FIELD_DEPTH)); 
        data.setSitemapLastMod(doc.getLong(FIELD_SITEMAP_LAST_MOD));
        data.setSitemapChangeFreq(doc.getString(FIELD_SITEMAP_CHANGE_FREQ));
        Double val = doc.getDouble(FIELD_SITEMAP_PRIORITY);
        if (val != null) {
            data.setSitemapPriority(val.floatValue());
        }
        data.setOriginalReference(doc.getString(FIELD_ORIGINAL_REFERENCE));
        data.setReferrerReference(doc.getString(FIELD_REFERRER_REFERENCE));
        data.setReferrerLinkTag(doc.getString(FIELD_REFERRER_LINK_TAG));
        data.setReferrerLinkText(doc.getString(FIELD_REFERRER_LINK_TEXT));
        data.setReferrerLinkTitle(doc.getString(FIELD_REFERRER_LINK_TITLE));
        
        @SuppressWarnings("unchecked")
        List<String> dbRefUrls = (List<String>) doc.get(FIELD_REFERENCED_URLS);
        if (dbRefUrls != null) {
            data.setReferencedUrls(
                    dbRefUrls.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
        }

        @SuppressWarnings("unchecked")
        List<String> dbRdrTrail = (List<String>) doc.get(FIELD_REDIRECT_TRAIL);
        if (dbRdrTrail != null) {
            data.setRedirectTrail(
                    dbRdrTrail.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
        }
        
        return data;
    }
}