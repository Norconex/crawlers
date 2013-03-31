package com.norconex.importer.parser.impl;

import org.apache.tika.parser.AutoDetectParser;

/**
 * Parser using auto-detection of document content-type to figure out
 * which specific parser to invoke to best parse a document.  
 * Use this class only when you
 * do not know the content-type of a document to be imported.  
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class FallbackParser extends AbstractTikaParser {

    private static final long serialVersionUID = 673866160238948126L;

    /**
     * Creates a new parser.
     * @param format one of parser's supported format
     */
    public FallbackParser(String format) {
        super(new AutoDetectParser(), format);
    }

}
