package com.norconex.crawler.core.store;

import com.norconex.crawler.core.doc.CrawlDocRecord;

// Adds a few extra field types for testing
class TestObject extends CrawlDocRecord {

    private static final long serialVersionUID = 1L;
    private int count;
    private boolean valid;

    TestObject() {
        super();
    }
    TestObject(String reference, int count, String checksum,
            String parentReference) {
        super(reference);
        this.count = count;
        setContentChecksum(checksum);
        setParentRootReference(parentReference);
    }

    int getCount() {
        return count;
    }
    void setCount(int count) {
        this.count = count;
    }
    boolean isValid() {
        return valid;
    }
    void setValid(boolean valid) {
        this.valid = valid;
    }
}
