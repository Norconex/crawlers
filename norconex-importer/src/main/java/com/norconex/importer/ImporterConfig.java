package com.norconex.importer;

import java.io.Serializable;

import com.norconex.importer.parser.DefaultDocumentParserFactory;
import com.norconex.importer.parser.IDocumentParserFactory;

/**
 * Importer configuration.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class ImporterConfig implements Serializable {

    private static final long serialVersionUID = -7110188100703942075L;

    private IDocumentParserFactory documentParserFactory = 
            new DefaultDocumentParserFactory();
//    private IDocumentTagger[] taggers;
//    private IDocumentTransformer[] transformers;
//    private IDocumentFilter[] filters;

    private IImportHandler[] preParseHandlers;
    private IImportHandler[] postParseHandlers;
    
    public IDocumentParserFactory getParserFactory() {
        return documentParserFactory;
    }
    public void setParserFactory(IDocumentParserFactory parserFactory) {
        this.documentParserFactory = parserFactory;
    }
    

    public void setPreParseHandlers(IImportHandler... handlers) {
        preParseHandlers = handlers;
    }
    public IImportHandler[] getPreParseHandlers() {
        return preParseHandlers;
    }
    public void setPostParseHandlers(IImportHandler... handlers) {
        postParseHandlers = handlers;
    }
    public IImportHandler[] getPostParseHandlers() {
        return postParseHandlers;
    }
    
    
    
//    public IDocumentFilter[] getFilters() {
//        return filters;
//    }
//    public void setFilters(IDocumentFilter[] filters) {
//        this.filters = filters;
//    }
//    public IDocumentTagger[] getTaggers() {
//        return taggers;
//    }
//    public void setTaggers(IDocumentTagger[] taggers) {
//        this.taggers = taggers;
//    }
//    public IDocumentTransformer[] getTransformers() {
//        return transformers;
//    }
//    public void setTransformers(IDocumentTransformer[] transformers) {
//        this.transformers = transformers;
//    }
}
