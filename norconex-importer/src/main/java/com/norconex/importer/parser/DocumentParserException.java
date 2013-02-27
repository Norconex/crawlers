package com.norconex.importer.parser;

public class DocumentParserException extends Exception {

    private static final long serialVersionUID = -8668185121797858885L;

    public DocumentParserException() {
    }

    public DocumentParserException(String message) {
        super(message);
    }

    public DocumentParserException(Throwable cause) {
        super(cause);
    }

    public DocumentParserException(String message, Throwable cause) {
        super(message, cause);
    }

}
