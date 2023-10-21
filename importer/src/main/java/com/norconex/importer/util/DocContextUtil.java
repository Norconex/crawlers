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
package com.norconex.importer.util;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.importer.handler.DocContext;
import com.norconex.importer.util.DocChunkedTextReader.TextChunk;

/**
 * Utilities when dealing with {@link DocContext}.
 */
public final class DocContextUtil {

    private DocContextUtil() {}

    public static void setTextChunk(
            DocContext docCtx, TextChunk chunk, String content)
                    throws IOException {
        if (isNotBlank(chunk.getField())) {
            // set on field
            var values = docCtx.metadata().get(chunk.getField());
            if (CollectionUtils.isNotEmpty(values)
                    && values.size() > chunk.getFieldValueIndex()) {
                if (chunk.getChunkIndex() == 0) {
                    values.set(chunk.getFieldValueIndex(), content);
                } else {
                    var value = values.get(chunk.getFieldValueIndex());
                    values.set(chunk.getFieldValueIndex(), value + content);
                }
            }
            docCtx.metadata().setList(chunk.getField(), values);
        } else {
            // set on body
            try (var writer = docCtx.writeContent().toWriter()) {
                writer.write(content);
            }
        }
    }
}
