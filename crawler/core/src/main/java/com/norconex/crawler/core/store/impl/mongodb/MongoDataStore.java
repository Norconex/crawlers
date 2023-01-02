/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.mongodb;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.google.gson.Gson;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.RenameCollectionOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.norconex.crawler.core.store.IDataStore;

public class MongoDataStore<T> implements IDataStore<T> {

    private static final String SORT_TIME_FIELD = "timestamp";

    private String name;
    private final MongoCollection<Document> collection;
    private final ReplaceOptions replaceOptions =
            new ReplaceOptions().upsert(true);
    private final FindOneAndDeleteOptions findOneAndDeleteOptions =
            new FindOneAndDeleteOptions().sort(fifoSort());
    private final Class<? extends T> type;
    private static final Gson GSON = new Gson();

    MongoDataStore(
            MongoDatabase db, String name, Class<? extends T> type) {
        super();
        this.type = type;
        requireNonNull(db, "'db' must not be null.");
        this.name = requireNonNull(name, "'name' must not be null.");
        this.collection = db.getCollection(name);
        collection.createIndex(Indexes.ascending("id"),
                new IndexOptions().sparse(true).unique(true));
        // When we need it as a FIFO queue, just grab first oldest time stamp.
        collection.createIndex(Indexes.ascending(SORT_TIME_FIELD),
                new IndexOptions().sparse(true));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void save(String id, T object) {
        collection.replaceOne(
                idFilter(id),
                toDocument(id, object),
                replaceOptions);
    }

    @Override
    public Optional<T> find(String id) {
        return unwrap(collection.find(idFilter(id)).first());
    }

    @Override
    public Optional<T> findFirst() {
        return unwrap(collection.find().sort(fifoSort()).first());
    }

    @Override
    public boolean exists(String id) {
        return collection.find(idFilter(id)).iterator().hasNext();
    }

    @Override
    public long count() {
        return collection.countDocuments();
    }

    @Override
    public boolean delete(String id) {
        return collection.deleteOne(idFilter(id)).getDeletedCount() > 0;
    }

    @Override
    public Optional<T> deleteFirst() {
        return unwrap(collection.findOneAndDelete(
                new Document(), findOneAndDeleteOptions));
    }

    @Override
    public void clear() {
        collection.deleteMany(new Document());
    }

    @Override
    public void close() {
        //NOOP: Closed implicitly when engine is closed.
    }

    // returns true if was all read
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        for (Document doc : collection.find()) {
            if (!predicate.test(doc.getString("id"), unwrap(doc).get())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        return !collection.find().limit(1).iterator().hasNext();
    }

    Class<?> getType() {
        return type;
    }
    String rename(String dbName, String newColName) {
        String oldName = name;
        collection.renameCollection(new MongoNamespace(dbName, newColName),
                new RenameCollectionOptions().dropTarget(true));
        name = newColName;
        return oldName;
    }
    static Bson idFilter(String idValue) {
        return Filters.eq("id", idValue);
    }

    private Bson fifoSort() {
        return Sorts.ascending(SORT_TIME_FIELD);
    }
    private Optional<T> unwrap(Document doc) {
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(fromDocument(doc, type));
    }

    private static Document toDocument(String id, Object object) {
        return new Document()
                .append("id", id)
                .append(SORT_TIME_FIELD, Instant.now().toEpochMilli())
                .append("object", GSON.toJson(object));
    }
    private static <T> T fromDocument(Document doc, Class<T> type) {
        return GSON.fromJson(doc.getString("object"), type);
    }
}
