package com.norconex.importer;

import java.io.Serializable;

import com.norconex.importer.filter.IDocumentFilter;
import com.norconex.importer.parser.DefaultDocumentParserFactory;
import com.norconex.importer.parser.IDocumentParserFactory;
import com.norconex.importer.tagger.IDocumentTagger;
import com.norconex.importer.transformer.IDocumentTransformer;

public class ImporterConfig implements Serializable {

    private static final long serialVersionUID = -7110188100703942075L;

    private IDocumentParserFactory documentParserFactory = 
            new DefaultDocumentParserFactory();
    private IDocumentTagger[] taggers;
    private IDocumentTransformer[] transformers;
    private IDocumentFilter[] filters;

    
    public IDocumentParserFactory getParserFactory() {
        return documentParserFactory;
    }
    public void setParserFactory(IDocumentParserFactory parserFactory) {
        this.documentParserFactory = parserFactory;
    }
    public IDocumentFilter[] getFilters() {
        return filters;
    }
    public void setFilters(IDocumentFilter[] filters) {
        this.filters = filters;
    }
    public IDocumentTagger[] getTaggers() {
        return taggers;
    }
    public void setTaggers(IDocumentTagger[] taggers) {
        this.taggers = taggers;
    }
    public IDocumentTransformer[] getTransformers() {
        return transformers;
    }
    public void setTransformers(IDocumentTransformer[] transformers) {
        this.transformers = transformers;
    }
}
