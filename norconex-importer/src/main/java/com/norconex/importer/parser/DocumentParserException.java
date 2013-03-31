package com.norconex.importer.parser;

/**
 * Exception thrown upon encountering a non-recoverable issue parsing a
 * document.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
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
