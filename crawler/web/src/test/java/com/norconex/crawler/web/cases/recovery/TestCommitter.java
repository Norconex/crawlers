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
package com.norconex.crawler.web.cases.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.queue.impl.FSQueueUtil;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.web.cases.recovery.ExternalCrawlSessionLauncher.CrawlOutcome;

import lombok.Data;
import lombok.SneakyThrows;

@Data
public class TestCommitter implements Committer, XMLConfigurable {
    private Path dir;
    public TestCommitter() {}
    public TestCommitter(Path dir) {
        this.dir = dir;
    }

    @Override
    @SneakyThrows
    public void init(CommitterContext committerContext)
            throws CommitterException {
        Files.createDirectories(dir);
    }
    @Override
    @SneakyThrows
    public void clean() throws CommitterException {
        FileUtil.delete(dir.toFile());
    }
    @Override
    public boolean accept(CommitterRequest request)
            throws CommitterException {
        return true;
    }
    @Override
    @SneakyThrows
    public void upsert(UpsertRequest upsertRequest)
            throws CommitterException {
        FSQueueUtil.toZipFile(upsertRequest, dir.resolve(
                "upsert-" + UUID.randomUUID() + ".zip"));
    }
    @Override
    @SneakyThrows
    public void delete(DeleteRequest deleteRequest)
            throws CommitterException {
        FSQueueUtil.toZipFile(deleteRequest, dir.resolve(
                "delete-" + UUID.randomUUID() + ".zip"));
    }
    @Override
    public void close() throws CommitterException {
        //NOOP
    }
    @Override
    public void loadFromXML(XML xml) {
        setDir(xml.getPath("dir", dir));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElement("dir", dir);
    }
    @SneakyThrows
    public void fillMemoryCommitters(
            CrawlOutcome outcome, ZonedDateTime launchTime) {
        FSQueueUtil.findZipFiles(dir).forEach(zip -> {
            try {
                CommitterRequest req = FSQueueUtil.fromZipFile(zip);
                if (Files.getLastModifiedTime(zip).compareTo(
                        FileTime.from(launchTime.toInstant())) > 0) {
                    if (req instanceof UpsertRequest upsert) {
                        outcome.committerAfterLaunch.upsert(upsert);
                    } else {
                        outcome.committerAfterLaunch.delete(
                                (DeleteRequest) req);
                    }
                }
                if (req instanceof UpsertRequest upsert) {
                    outcome.committerCombininedLaunches.upsert(upsert);
                } else {
                    outcome.committerCombininedLaunches.delete(
                            (DeleteRequest) req);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}