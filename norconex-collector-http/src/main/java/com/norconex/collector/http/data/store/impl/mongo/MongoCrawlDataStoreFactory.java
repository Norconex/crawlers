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

import com.norconex.collector.core.data.store.ICrawlDataStoreFactory;
import com.norconex.collector.core.data.store.impl.mongo.AbstractMongoCrawlDataStoreFactory;
import com.norconex.collector.core.data.store.impl.mongo.IMongoSerializer;
import com.norconex.commons.lang.encrypt.EncryptionUtil;

/**
 * <p>
 * Mongo implementation of {@link ICrawlDataStoreFactory}.
 * </p>
 * <p>
 * As of 2.7.0, <code>password</code> can take a password that has been 
 * encrypted using {@link EncryptionUtil} (or command-line encrypt.[bat|sh]). 
 * See for {@link AbstractMongoCrawlDataStoreFactory} details.
 * </p>
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;crawlDataStoreFactory  
 *      class="com.norconex.collector.http.data.store.impl.mongo.MongoCrawlDataStoreFactory"&gt;
 *      &lt;host&gt;(Optional Mongo server hostname. Default to localhost)&lt;/host&gt;
 *      &lt;port&gt;(Optional Mongo port. Default to 27017)&lt;/port&gt;
 *      &lt;dbname&gt;(Optional Mongo database name. Default to crawl id)&lt;/dbname&gt;
 *      &lt;username&gt;(Optional user name)&lt;/username&gt;
 *      &lt;password&gt;(Optional user password)&lt;/password&gt;
 *      &lt;cachedCollectionName&gt;(Custom "cached" collection name)&lt;/cachedCollectionName&gt;
 *      &lt;referencesCollectionName&gt;(Custom "references" collection name)&lt;/referencesCollectionName&gt;
 *      &lt;mechanism&gt;(Optional authentication mechanism)&lt;/mechanism&gt;
 *      &lt;!-- Use the following if password is encrypted. --&gt;
 *      &lt;passwordKey&gt;(the encryption key or a reference to it)&lt;/passwordKey&gt;
 *      &lt;passwordKeySource&gt;[key|file|environment|property]&lt;/passwordKeySource&gt;
 *  &lt;/crawlDataStoreFactory&gt;
 * </pre>
 * <p>
 * If "username" is not provided, no authentication will be attempted. 
 * The "username" must be a valid user that has the "readWrite" role over 
 * the database (set with "dbname").
 * </p>
 * <p>
 * As of 2.7.1, it is now possible to specify which MongoDB mechanism to use 
 * for authentication. Refer to {@link AbstractMongoCrawlDataStoreFactory}
 * for available options.
 * </p>
 * <p>
 * As of 2.8.2, you can define your own collection names with 
 * {@link #setReferencesCollectionName(String)} and 
 * {@link #setCachedCollectionName(String)}.
 * </p>
 * 
 * <h4>Usage example:</h4>
 * <p>
 * The following points to a Mongo installation with host name "localhost",
 * port 1234, and a Mongo database called "MyCrawl".
 * </p>
 * <pre>
 *  &lt;crawlDataStoreFactory  
 *      class="com.norconex.collector.http.data.store.impl.mongo.MongoCrawlDataStoreFactory"&gt;
 *      &lt;host&gt;localhost&lt;/host&gt;
 *      &lt;port&gt;1234&lt;/port&gt;
 *      &lt;dbname&gt;MyCrawl&lt;/dbname&gt;
 *  &lt;/crawlDataStoreFactory&gt;
 * </pre> 
 * 
 * @author Pascal Essiembre
 */
public class MongoCrawlDataStoreFactory 
        extends AbstractMongoCrawlDataStoreFactory {

    @Override
    protected IMongoSerializer createMongoSerializer() {
        return new MongoCrawlDataSerializer();
    }
    
}
