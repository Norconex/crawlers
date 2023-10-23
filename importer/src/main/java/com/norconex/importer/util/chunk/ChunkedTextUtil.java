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
package com.norconex.importer.util.chunk;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.util.function.Predicate;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.function.FailableFunction;

import com.norconex.importer.handler.DocContext;
import com.norconex.importer.handler.ImporterHandlerException;

import lombok.NonNull;

/**
 * Utilities when dealing with {@link DocContext}.
 */
public final class ChunkedTextUtil {

    private ChunkedTextUtil() {}

    public static void transform(
            @NonNull
            ChunkedTextSupport cfg,
            @NonNull
            DocContext docCtx,
            @NonNull
            FailableFunction<TextChunk, String, IOException> textConsumer)
                    throws ImporterHandlerException {
        transform(cfg, docCtx, textConsumer, null);
    }
    public static void transform(
            @NonNull
            ChunkedTextSupport cfg,
            @NonNull
            DocContext docCtx,
            @NonNull
            FailableFunction<TextChunk, String, IOException> textConsumer,
            Predicate<TextChunk> keepReading)
                    throws ImporterHandlerException {
        ChunkedTextReader.from(cfg).read(docCtx, chunk -> {
            var newValue = textConsumer.apply(chunk);
            writeBack(docCtx, chunk, newValue);
            if (keepReading != null) {
                return keepReading.test(chunk);
            }
            return true;
        });
    }

    public static void writeBack(
            @NonNull DocContext docCtx,
            @NonNull TextChunk originalChunk,
            String newText)
                    throws IOException {
        if (isNotBlank(originalChunk.getField())) {
            // set on field
            var values = docCtx.metadata().get(originalChunk.getField());
            if (CollectionUtils.isNotEmpty(values)
                    && values.size() > originalChunk.getFieldValueIndex()) {
                if (originalChunk.getChunkIndex() == 0) {
                    values.set(originalChunk.getFieldValueIndex(), newText);
                } else {
                    var value = values.get(originalChunk.getFieldValueIndex());
                    values.set(originalChunk.getFieldValueIndex(),
                            value + newText);
                }
            }
            docCtx.metadata().setList(originalChunk.getField(), values);
        } else {
            // set on body
            try (var writer = docCtx.writeContent().toWriter()) {
                writer.write(newText);
            }
        }
    }
}
