/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Committer.
 * 
 * Norconex Committer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Committer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Committer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.committer.elasticsearch;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

/**
 * 
 * Implementation that creates a client that does not hold any data.
 * 
 * @see http://www.elasticsearch.org/guide/reference/java-api/client/
 * 
 * @author <a href="mailto:pascal.dimassimo@norconex.com">Pascal Dimassimo</a>
 * 
 */
public class DefaultClientFactory implements IClientFactory {

    @Override
    public Client createClient(ElasticsearchCommitter committer) {
        NodeBuilder builder;
        if (StringUtils.isNotBlank(committer.getClusterName())) {
            builder = nodeBuilder().clusterName(committer.getClusterName());
        } else {
            builder = nodeBuilder();
        }
        Node node = builder.client(true).node();
        return node.client();
    }
}
