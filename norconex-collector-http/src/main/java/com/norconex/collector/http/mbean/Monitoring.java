package com.norconex.collector.http.mbean;

import com.norconex.collector.core.data.store.ICrawlDataStore;

public class Monitoring implements MonitoringMBean {

    private final ICrawlDataStore refStore;
    
    public Monitoring(ICrawlDataStore refStore) {
        this.refStore = refStore;
    }

    @Override
    public int getProcessedURLCount() {
        return refStore.getProcessedCount();
    }

    @Override
    public int getURLQueueSize() {
        return refStore.getQueueSize();
    }

}
