/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.committer.core;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.jupiter.params.provider.Arguments;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.commons.lang.map.Properties;


/**
 */
public final class TestUtil {

    private static final BeanMapper beanMapper = BeanMapper.DEFAULT;

    private TestUtil() {}

    public static BeanMapper beanMapper() {
        return beanMapper;
    }

    // Create Arguments instance with the object as the first argument
    // and the simple class name of the object as the second.  For nicer
    // display in test reports.
    public static Arguments args(Object obj) {
        return Arguments.of(obj, obj.getClass().getSimpleName());
    }
    public static Arguments args(Supplier<Object> supplier) {
        return args(supplier.get());
    }

    public static Collection<File> listFSFiles(Path path) {
        return FileUtils.listFiles(path.toFile(),
                TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    }
    public static Collection<File> listFSUpsertFiles(Path path) {
        return FileUtils.listFiles(path.toFile(),
                FileFilterUtils.prefixFileFilter("upsert-"),
                TrueFileFilter.INSTANCE);
    }
    public static Collection<File> listFSDeleteFiles(Path path) {
        return FileUtils.listFiles(path.toFile(),
                FileFilterUtils.prefixFileFilter("delete-"),
                TrueFileFilter.INSTANCE);
    }

    public static void commitRequests(Committer c, List<CommitterRequest> crs)
            throws CommitterException {
        for (CommitterRequest cr : crs) {
            commitRequest(c, cr);
        }
    }
    public static void commitRequest(Committer c, CommitterRequest cr)
            throws CommitterException {
        if (cr instanceof UpsertRequest) {
            c.upsert((UpsertRequest) cr);
        } else {
            c.delete((DeleteRequest) cr);
        }
    }


    public static CommitterContext committerContext(Path folder) {
        return CommitterContext.builder().setWorkDir(folder).build();
    }

    // 1+ = upserts; 0- = delete
    public static List<CommitterRequest> mixedRequests(int... reqTypes) {
        List<CommitterRequest> reqs = new ArrayList<>();
        for (var i = 0; i < reqTypes.length; i++) {
            if (reqTypes[i] > 0) {
                reqs.add(upsertRequest(i + 1));
            } else {
                reqs.add(deleteRequest(i + 1));
            }
        }
        return reqs;
    }

    public static List<UpsertRequest> upsertRequests(int qty) {
        List<UpsertRequest> reqs = new ArrayList<>();
        for (var i = 0; i < qty; i++) {
            reqs.add(upsertRequest(i + 1));
        }
        return reqs;
    }
    public static UpsertRequest upsertRequest(int index) {
        var meta = new Properties();
        meta.add("title", "Sample document " + index);
        return new UpsertRequest(
                "http://example.com/page" + index + ".html", meta,
                IOUtils.toInputStream(
                        "This is fake content for sample document " + index,
                        StandardCharsets.UTF_8));
    }
    public static UpsertRequest upsertRequest(
            String reference, String content, Object... metadataPairs) {
        var meta = new Properties();
        meta.loadFromMap(MapUtil.toMap(metadataPairs));
        return new UpsertRequest(reference, meta,
                IOUtils.toInputStream(content, StandardCharsets.UTF_8));
    }

    public static List<DeleteRequest> deleteRequests(int qty) {
        List<DeleteRequest> reqs = new ArrayList<>();
        for (var i = 0; i < qty; i++) {
            reqs.add(deleteRequest(i + 1));
        }
        return reqs;
    }
    public static DeleteRequest deleteRequest(int index) {
        var meta = new Properties();
        meta.add("title", "Sample document " + index);
        return new DeleteRequest(
                "http://example.com/page" + index + ".html", meta);
    }
    public static DeleteRequest deleteRequest(
            String reference, Object... metadataPairs) {
        var meta = new Properties();
        meta.loadFromMap(MapUtil.toMap(metadataPairs));
        return new DeleteRequest(reference, meta);
    }
}
