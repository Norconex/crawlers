package com.norconex.committer;

/**
 * Triggered when something went wrong with committing.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class CommitterException extends RuntimeException {

    private static final long serialVersionUID = -805913995358009121L;

    public CommitterException() {
        super();
    }

    public CommitterException(String message) {
        super(message);
    }

    public CommitterException(Throwable cause) {
        super(cause);
    }

    public CommitterException(String message, Throwable cause) {
        super(message, cause);
    }

}
