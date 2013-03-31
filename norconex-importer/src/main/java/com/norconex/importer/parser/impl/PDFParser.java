package com.norconex.importer.parser.impl;


/**
 * HTML parser based on Apache Tika
 * {@link org.apache.tika.parser.pdf.PDFParser}.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class PDFParser extends AbstractTikaParser {

    private static final long serialVersionUID = 1L;

    public PDFParser(String format) {
        super(new org.apache.tika.parser.pdf.PDFParser(), format);
    }
}
