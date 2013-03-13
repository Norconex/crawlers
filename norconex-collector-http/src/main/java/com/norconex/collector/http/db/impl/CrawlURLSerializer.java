package com.norconex.collector.http.db.impl;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapdb.Serializer;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.db.CrawlURL;

/*default*/ class CrawlURLSerializer 
            implements Serializer<CrawlURL>, Serializable {
        private static final long serialVersionUID = 3225699038123718796L;
        @Override
        public void serialize(DataOutput out, CrawlURL crawlURL)
                throws IOException {
            out.writeUTF(StringUtils.defaultString(crawlURL.getHeadChecksum()));
            out.writeUTF(StringUtils.defaultString(crawlURL.getDocChecksum()));
            out.writeInt(crawlURL.getDepth());
            out.writeUTF(ObjectUtils.toString(crawlURL.getStatus()));
        }
        @Override
        public CrawlURL deserialize(DataInput in, int available)
                throws IOException {
            CrawlURL crawlURL = new CrawlURL();
            crawlURL.setHeadChecksum(in.readUTF());
            crawlURL.setDocChecksum(in.readUTF());
            crawlURL.setDepth(in.readInt());
            crawlURL.setStatus(CrawlStatus.valueOf(in.readUTF()));
            return crawlURL;
        }
    }
//    Serializer<CrawlURL> serializer = new Serializer<CrawlURL>() {
//        @Override
//        public void serialize(DataOutput out, CrawlURL crawlURL)
//                throws IOException {
//            out.writeUTF(crawlURL.getHeadChecksum());
//            out.writeUTF(crawlURL.getDocChecksum());
//            out.writeInt(crawlURL.getDepth());
//            out.writeUTF(crawlURL.getStatus().toString());
//        }
//        @Override
//        public CrawlURL deserialize(DataInput in, int available)
//                throws IOException {
//            CrawlURL crawlURL = new CrawlURL();
//            crawlURL.setHeadChecksum(in.readUTF());
//            crawlURL.setDocChecksum(in.readUTF());
//            crawlURL.setDepth(in.readInt());
//            crawlURL.setStatus(CrawlStatus.valueOf(in.readUTF()));
//            return crawlURL;
//        }
//    };