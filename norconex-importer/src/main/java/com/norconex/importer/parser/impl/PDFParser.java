package com.norconex.importer.parser.impl;


public class PDFParser extends AbstractTikaParser {

    private static final long serialVersionUID = 1L;

    public PDFParser(String format) {
        super(new org.apache.tika.parser.pdf.PDFParser(), format);
    }
}
