/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.committer.core.fs.impl;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.fs.AbstractFsCommitter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Commits documents to JSON files.  There are two kinds of document
 * representations: upserts and deletions.
 * </p>
 * <p>
 * If you request to split upserts and deletions into separate files,
 * the generated files will start with "upsert-" (for additions/modifications)
 * and "delete-" (for deletions).
 * </p>
 * <p>
 * The generated files are never updated.  Sending a modified document with the
 * same reference will create a new entry and won't modify any existing ones.
 * You can think of the generated files as a set of commit instructions.
 * </p>
 * <p>
 * The generated JSON file names are made of a timestamp and a sequence number.
 * </p>
 * <p>
 * You have the option to give a prefix or suffix to
 * files that will be created (default does not add any).
 * </p>
 *
 * <h3>Generated JSON format:</h3>
 * {@nx.json
 * [
 *   {"upsert": {
 *     "reference": "document reference, e.g., URL",
 *     "metadata": {
 *       "name": ["value"],
 *       "anothername": [
 *         "multivalue1",
 *         "multivalue2"
 *       ],
 *       "anyname": ["name-value is repeated as necessary"]
 *     },
 *     "content": "Document Content Goes here"
 *   }},
 *   {"upsert": {
 *     // upsert is repeated as necessary
 *   }},
 *   {"delete": {
 *     "reference": "document reference, e.g., URL",
 *     "metadata": {
 *       "name": ["value"],
 *       "anothername": [
 *         "multivalue1",
 *         "multivalue2"
 *       ],
 *       "anyname": ["name-value is repeated as necessary"]
 *     }
 *   }},
 *   {"delete": {
 *     // delete is repeated as necessary
 *   }}
 * ]
 *
 * }
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.core.fs.impl.JSONFileCommitter">
 *   {@nx.include com.norconex.committer.core.fs.AbstractFSCommitter#options}
 *   <indent>(number of indentation spaces, default does not indent)</indent>
 * </committer>
 * }
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class JsonFileCommitter
        extends AbstractFsCommitter<Writer, JsonFileCommitterConfig> {

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private boolean first = true;

    @Getter
    private final JsonFileCommitterConfig configuration =
            new JsonFileCommitterConfig();

    @Override
    protected String getFileExtension() {
        return "json";
    }

    @Override
    protected Writer createDocWriter(Writer writer) throws IOException {
        writer.write("[");
        newLine(writer);
        return writer;
    }

    @Override
    protected synchronized void writeUpsert(
            Writer writer, UpsertRequest upsertRequest
    ) throws IOException {

        if (!first) {
            writer.write(',');
            newLine(writer);
        }

        var doc = new JSONObject();
        doc.put("reference", upsertRequest.getReference());
        doc.put("metadata", new JSONObject(upsertRequest.getMetadata()));
        doc.put(
                "content", IOUtils.toString(
                        upsertRequest.getContent(), StandardCharsets.UTF_8
                ).trim()
        );

        var upsertObj = new JSONObject();
        upsertObj.put("upsert", doc);
        if (configuration.getIndent() > -1) {
            writer.write(upsertObj.toString(configuration.getIndent()));
        } else {
            writer.write(upsertObj.toString());
        }

        writer.flush();
        first = false;
    }

    @Override
    protected synchronized void writeDelete(
            Writer writer, DeleteRequest deleteRequest
    ) throws IOException {

        if (!first) {
            writer.write(',');
            newLine(writer);
        }

        var doc = new JSONObject();
        doc.put("reference", deleteRequest.getReference());
        doc.put("metadata", new JSONObject(deleteRequest.getMetadata()));

        var deleteObj = new JSONObject();
        deleteObj.put("delete", doc);
        if (configuration.getIndent() > -1) {
            writer.write(deleteObj.toString(configuration.getIndent()));
        } else {
            writer.write(deleteObj.toString());
        }

        writer.flush();
        first = false;
    }

    @Override
    protected void closeDocWriter(Writer writer)
            throws IOException {
        if (writer != null) {
            if (configuration.getIndent() > -1) {
                writer.write("\n");
            }
            writer.write("]");
            writer.close();
        }
    }

    private void newLine(Writer writer) throws IOException {
        if (configuration.getIndent() > -1) {
            writer.write('\n');
        }
    }
}
