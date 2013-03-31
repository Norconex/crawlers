package com.norconex.importer.parser.impl;

import org.apache.tika.parser.html.HtmlParser;

/**
 * HTML parser based on Apache Tika {@link HtmlParser}.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class HTMLParser extends AbstractTikaParser {

    private static final long serialVersionUID = -231116566033729542L;

    public HTMLParser(String format) {
        super(new HtmlParser(), format);
    }

}
