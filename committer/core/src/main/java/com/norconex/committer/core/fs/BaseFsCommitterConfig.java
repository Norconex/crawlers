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
package com.norconex.committer.core.fs;

import java.nio.file.Path;

import com.norconex.committer.core.BaseCommitterConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Base class for Committers writing to the local file system.
 * </p>
 */
@Data
@Accessors(chain = true)
public class BaseFsCommitterConfig extends BaseCommitterConfig {

    /**
     * The directory where files are committed.
     */
    private Path directory;
    private int docsPerFile;
    private boolean compress;
    private boolean splitUpsertDelete;
    /**
     * The file name prefix (default is {@code null}).
     */
    private String fileNamePrefix;
    /**
     * The file name suffix (default is {@code null}).
     */
    private String fileNameSuffix;

}
