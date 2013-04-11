package com.norconex.importer;

import java.io.Serializable;

import com.norconex.importer.filter.IDocumentFilter;
import com.norconex.importer.tagger.IDocumentTagger;
import com.norconex.importer.transformer.IDocumentTransformer;

/**
 * <p>Identifies a class as being an import handler.  Handlers performs specific
 * tasks on the importing content (other than parsing to extract raw content).
 * They can be invoked before or after a document is parsed.  There are 
 * three types of handlers currently supported:</p> 
 * <ul>
 *   <li>{@link IDocumentFilter}: accepts or reject an incoming document.</li>
 *   <li>{@link IDocumentTagger}: modifies a document metadata.</li>
 *   <li>{@link IDocumentTransformer}: modifies a document content.</li>
 * </ul>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IImportHandler extends Serializable {

}
