package com.norconex.importer.parser.impl;

import org.apache.tika.parser.AutoDetectParser;

public class FallbackParser extends AbstractTikaParser {

    private static final long serialVersionUID = 673866160238948126L;

    public FallbackParser(String format) {
        super(new AutoDetectParser(), format);
    }

}
