/* Copyright 2026 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.support.InMemoryCacheManager;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.session.CrawlSession;

@Timeout(30)
class DedupServiceTest {

    @Test
    void init_enablesDocumentAndMetadataDedup_trackingDuplicates() {
        var service = new DedupService();
        service.init(session(true, true));

        var first = new CrawlEntry("ref-1");
        first.setContentChecksum("doc-cs");
        first.setMetaChecksum("meta-cs");
        var second = new CrawlEntry("ref-2");
        second.setContentChecksum("doc-cs");
        second.setMetaChecksum("meta-cs");

        assertThat(service.findOrTrackDocument(first)).isEmpty();
        assertThat(service.findOrTrackDocument(second)).contains("ref-1");
        assertThat(service.findOrTrackMetadata(first)).isEmpty();
        assertThat(service.findOrTrackMetadata(second)).contains("ref-1");
    }

    @Test
    void init_disabledDedupOrNullChecksum_returnsEmpty() {
        var service = new DedupService();
        service.init(session(false, false));

        var entry = new CrawlEntry("ref-1");

        assertThat(service.findOrTrackDocument(entry)).isEmpty();
        assertThat(service.findOrTrackMetadata(entry)).isEmpty();
    }

    @Test
    void init_metadataOnly_tracksMetadataButLeavesDocumentDisabled() {
        var service = new DedupService();
        service.init(session(true, false));

        var first = new CrawlEntry("ref-1");
        first.setMetaChecksum("meta-cs");
        var second = new CrawlEntry("ref-2");
        second.setMetaChecksum("meta-cs");
        var docEntry = new CrawlEntry("ref-3");
        docEntry.setContentChecksum("doc-cs");

        assertThat(service.findOrTrackMetadata(first)).isEmpty();
        assertThat(service.findOrTrackMetadata(second)).contains("ref-1");
        assertThat(service.findOrTrackDocument(docEntry)).isEmpty();
    }

    @Test
    void init_documentOnly_tracksDocumentButLeavesMetadataDisabled() {
        var service = new DedupService();
        service.init(session(false, true));

        var first = new CrawlEntry("ref-1");
        first.setContentChecksum("doc-cs");
        var second = new CrawlEntry("ref-2");
        second.setContentChecksum("doc-cs");
        var metaEntry = new CrawlEntry("ref-3");
        metaEntry.setMetaChecksum("meta-cs");

        assertThat(service.findOrTrackDocument(first)).isEmpty();
        assertThat(service.findOrTrackDocument(second)).contains("ref-1");
        assertThat(service.findOrTrackMetadata(metaEntry)).isEmpty();
    }

    private CrawlSession session(boolean metadataDedup, boolean documentDedup) {
        var config = new CrawlConfig();
        config.setMetadataDeduplicate(metadataDedup);
        config.setDocumentDeduplicate(documentDedup);
        if (metadataDedup) {
            config.setMetadataChecksummer(metadata -> "meta");
        }
        if (documentDedup) {
            config.setDocumentChecksummer(doc -> "doc");
        }

        var cacheManager = new InMemoryCacheManager();
        var cluster = mock(Cluster.class);
        var crawlContext = mock(CrawlContext.class);
        var session = mock(CrawlSession.class);

        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(session.getCluster()).thenReturn(cluster);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        when(cluster.getCacheManager()).thenReturn(cacheManager);

        return session;
    }
}
