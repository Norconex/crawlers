/* Copyright 2013 Norconex Inc.
 * 
 * This file is part of Norconex ElasticSearch Committer.
 * 
 * Norconex ElasticSearch Committer is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation, either version 3 of the License, 
 * or (at your option) any later version.
 * 
 * Norconex ElasticSearch Committer is distributed in the hope that it will be 
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex ElasticSearch Committer. 
 * If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.committer.elasticsearch;

import org.elasticsearch.client.Client;

/**
 * 
 * Factory for Elasticsearch {@link Client}.
 * 
 * @author <a href="mailto:pascal.dimassimo@norconex.com">Pascal Dimassimo</a>
 * 
 */
public interface IClientFactory {

    /**
     * Creates a client
     * 
     * @param committer
     *            committer object (used to get properties needed for building
     *            the client)
     * @return {@link Client}
     */
    public Client createClient(ElasticsearchCommitter committer);
}
