/* Copyright 2023-2024 Norconex Inc.
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

import com.norconex.committer.core.fs.BaseFsCommitterConfig;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

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
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
@FieldNameConstants
public class JsonFileCommitterConfig extends BaseFsCommitterConfig {
    private int indent = -1;
}
