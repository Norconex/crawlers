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
package com.norconex.committer.core.fs;

import java.nio.file.Path;

import com.norconex.committer.core.BaseCommitterConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Base class for committers writing to the local file system.
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * <p>
 * The following are configuration options inherited by subclasses:
 * </p>
 * {@nx.xml #options
 *   <directory>(path where to save the files)</directory>
 *   <docsPerFile>(max number of docs per file)</docsPerFile>
 *   <compress>[false|true]</compress>
 *   <splitUpsertDelete>[false|true]</splitUpsertDelete>
 *   <fileNamePrefix>(optional prefix to created file names)</fileNamePrefix>
 *   <fileNameSuffix>(optional suffix to created file names)</fileNameSuffix>
 *   {@nx.include com.norconex.committer.core.AbstractCommitter@nx.xml.usage}
 * }
 *
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class BaseFSCommitterConfig extends BaseCommitterConfig {

    /**
     * The directory where files are committed.
     * @param directory a directory
     * @return a directory
     */
    private Path directory;
    private int docsPerFile;
    private boolean compress;
    private boolean splitUpsertDelete;
    /**
     * The file name prefix (default is <code>null</code>).
     * @param fileNamePrefix file name prefix
     * @return file name prefix
     */
    private String fileNamePrefix;
    /**
     * The file name suffix (default is <code>null</code>).
     * @param fileNameSuffix file name suffix
     * @return file name suffix
     */
    private String fileNameSuffix;

}
