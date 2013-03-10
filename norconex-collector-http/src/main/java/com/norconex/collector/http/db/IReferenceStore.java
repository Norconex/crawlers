package com.norconex.collector.http.db;

import com.norconex.collector.http.crawler.URLStatus;

interface IReferenceStore {

  //TODO.... create interface to abstract reference storage
    boolean hasActiveURLs();
    boolean hasQueuedURLs();
    boolean isLastProcessedURLsEmpty();
    void copyLastProcessedURLBatchToQueue(int batchSize);
    void addQueuedURL(String url, int depth);
    QueuedURL getNextQueuedURL();
    URLMemento getURLMemento(String processedURL);
    boolean shouldDeleteURL(String url, URLMemento memento);
    void addProcessedURL(String url, URLMemento memento);
    boolean isProcessedURL(String url);
    boolean isQueuedURL(String url);
    boolean isActiveURL(String url);
    int getQueuedURLCount();
    int getProcessedURLCount();

    
    class QueuedURL {
        private int depth;
        private String url;
        int getDepth() {
            return depth;
        }
        String getUrl() {
            return url;
        }
    }
    
    
    class URLMemento{
    	private URLStatus status;
    	final private int depth;
    	private String headChecksum;
    	private String docChecksum;
		URLMemento(URLStatus status, int depth) {
			super();
			this.status = status;
			this.depth = depth;
		}
		int getDepth() {
			return depth;
		}
		String getHeadChecksum() {
			return headChecksum;
		}
		void setHeadChecksum(String headChecksum) {
			this.headChecksum = headChecksum;
		}
		String getDocChecksum() {
			return docChecksum;
		}
		void setDocChecksum(String docChecksum) {
			this.docChecksum = docChecksum;
		}
		URLStatus getStatus() {
			return status;
		}
		void setStatus(URLStatus status) {
			this.status = status;
		}
//		@Override
//		String toString() {
//			Properties props = new Properties();
//			props.setProperty("s", status.toString());
//			if (headChecksum != null) {
//				props.setProperty("hc", headChecksum);
//			}
//			if (docChecksum != null) {
//                props.setProperty("dc", docChecksum);
//			}
//			props.setProperty("d", Integer.toString(depth));
//			StringWriter w = new StringWriter();
//			try {
//				props.store(w, null);
//			} catch (IOException e) {
//				throw new HttpCollectorException(
//						"Could not persist ProcessedURL: " 
//								+ props.toString(), e);
//			}
//			return w.toString();
//		}
//		static URLMemento fromString(String str) {
//			Properties props = new Properties();
//			StringReader r = new StringReader(str);
//			try {
//				props.load(r);
//				r.close();
//			} catch (IOException e) {
//				throw new HttpCollectorException(
//						"Could not parse processed URL from String: " + str, e);
//			}
//			URLMemento url = new URLMemento(
//					URLStatus.valueOf(props.getProperty("s")),
//					Integer.parseInt(props.getProperty("d")));
//			url.setDocChecksum(props.getProperty("dc"));
//			url.setHeadChecksum(props.getProperty("hc"));
//			return url;
//		}
    }
}
