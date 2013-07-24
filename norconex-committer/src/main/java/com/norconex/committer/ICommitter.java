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
package com.norconex.committer;

import java.io.File;
import java.io.Serializable;

import com.norconex.commons.lang.map.Properties;

/**
 * Commits documents to their final destination (e.g. search engine).
 * @author Pascal Essiembre
 */
@SuppressWarnings("nls")
public interface ICommitter extends Serializable {

    /**
     * The default document unique identifier (reference) is 
     * "<code>document.reference</code>".   This value is set by default 
     * when using the Norconex Importer module.  Concrete 
     * implementations should offer to overwrite this default value when
     * appropriate.
     */
    static final String DEFAULT_DOCUMENT_REFERENCE = "document.reference";
    
    /**
     * Queues a new or modified document.   These queued documents should
     * be sent to their target destination when commit is called.
     * @param reference document reference (e.g. URL)
     * @param document text document 
     * @param metadata document metadata
     */
    void queueAdd(String reference, File document, Properties metadata);    

    /**
     * Queues a document for removal.   These queued documents should
     * be sent to their target destination for deletion when commit is called.
     * @param reference document reference (e.g. URL)
     * @param document text document 
     * @param metadata document metadata
     */
    void queueRemove(String reference, File document, Properties metadata);    

    /**
     * Commits queued documents.  Effectively apply the additions and removals.
     */
    void commit();
}
