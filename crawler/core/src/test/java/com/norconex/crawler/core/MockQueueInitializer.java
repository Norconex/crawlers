/* Copyright 2023 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.crawler.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.norconex.crawler.core.crawler.CrawlerImpl.QueueInitContext;
import com.norconex.crawler.core.doc.CrawlDocRecord;

public class MockQueueInitializer
        implements Function<QueueInitContext, MutableBoolean> {

//    private final MockQueue mockQueue;
    private final List<String> startReferences = new ArrayList<>();

    public MockQueueInitializer(String... startReferences) {
        this.startReferences.addAll(List.of(startReferences));
    }

    @Override
    public MutableBoolean apply(QueueInitContext ctx) {

//        if (mockQueue != null) {
//            var cnt = 0;
//            for (Mock mock : mockQueue.getMocks()) {
//                ctx.queue(record(mock, ++cnt));
//            }
//        }

        startReferences.forEach(ref -> ctx.queue(new CrawlDocRecord(ref)));

        return new MutableBoolean(true);

//        mockQueue.

//
//        var rec1 = new CrawlDocRecord("mock://sampledoc/1");
//        rec1.setContentChecksum("blah1-checksum");
//        rec1.setContentType(ContentType.HTML);
//        rec1.setDepth(0);
//        rec1.setState(CrawlState.MODIFIED);
//        // add more stuff?
//        ctx.queue(rec1);
//
//        var rec2 = new CrawlDocRecord("mock://sampledoc/2");
//        // add more stuff?
//        rec2.setDepth(1);
//        ctx.queue(rec2);
//
//        return new MutableBoolean(true);
    }


//    private CrawlDocRecord record(Mock mock, int index) {
//        var rec = new CrawlDocRecord("mock://sampledoc/" + index);
//        switch (mock) {
//        case UNMODIFIED -> {
//            rec.setContentChecksum("unmodified-checksum-" + index);
//            rec.setContentType(ContentType.HTML);
//        }
////            return new CrawlDocRecord();
////              var rec1 = new CrawlDocRecord("mock://sampledoc/1");
////              rec1.setContentChecksum("blah1-checksum");
////              rec1.setContentType(ContentType.HTML);
////              rec1.setDepth(0);
////              rec1.setState(CrawlState.MODIFIED);
////              // add more stuff?
////              ctx.queue(rec1);
//
//
//
//        default ->
//            throw new IllegalArgumentException("Unexpected value: " + mock);
//        }
//        return rec;
////        var rec1 = new CrawlDocRecord("mock://sampledoc/1");
////        rec1.setContentChecksum("blah1-checksum");
////        rec1.setContentType(ContentType.HTML);
////        rec1.setDepth(0);
////        rec1.setState(CrawlState.MODIFIED);
////        // add more stuff?
////        ctx.queue(rec1);
//
//    }
}
