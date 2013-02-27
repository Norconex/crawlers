package com.norconex.importer;

public class ImporterException extends RuntimeException {

    private static final long serialVersionUID = -805913995358009121L;

    public ImporterException() {
        super();
    }

    public ImporterException(String message) {
        super(message);
    }

    public ImporterException(Throwable cause) {
        super(cause);
    }

    public ImporterException(String message, Throwable cause) {
        super(message, cause);
    }

}
