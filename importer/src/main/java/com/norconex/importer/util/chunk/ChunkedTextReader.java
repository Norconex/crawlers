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
package com.norconex.importer.util.chunk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.function.FailableFunction;

import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.HandlerContext;
import com.norconex.importer.util.ReadAdapter;
import com.norconex.importer.util.ReadAdapter.ChunkedReadOptions;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;

/**
 * Supplies chunks of text to the provided consumer. If a field matcher
 * is supplied, the text will be coming from matching document fields.
 * Otherwise, the document body is used. In either case, if the text
 * is greater than the <code>maxChunkSize</code>, it is split and
 * read in chunks.
 * This class is a convenient way to apply similar logic whether the text
 * source is a document content, field, huge, or small.
 */
@Builder
public class ChunkedTextReader {

    private final TextMatcher fieldMatcher;
    private final Charset charset;
    @Default
    private final int maxChunkSize = TextReader.DEFAULT_MAX_READ_SIZE;
    private final boolean skipEmpty;

    public static ChunkedTextReader from(
            ChunkedTextSupport chunkedTextSupport) {
        return new ChunkedTextReaderBuilder()
                .fieldMatcher(chunkedTextSupport.getFieldMatcher())
                .maxChunkSize(chunkedTextSupport.getMaxReadSize())
                .charset(chunkedTextSupport.getSourceCharset())
                .build();
    }

    /**
     * Handles the processing of document text, invoking the consumer
     * as many times as necessary. If the field matcher is set, the
     * streams to handle are coming from fields, otherwise it is the document
     * content.
     * @param docCtx document context
     * @param textConsumer text consumer
     * @return <code>true</code> if all chunks were read. <code>false</code>
     *     if the chunk consumer ever returned <code>false</code>.
     * @throws IOException problem reading
     */
    public boolean read(
            @NonNull
            HandlerContext docCtx,
            @NonNull
            FailableFunction<TextChunk, Boolean, IOException> textConsumer)
                    throws IOException {

        var aborted = false;
        if (fieldMatcher != null && fieldMatcher.isSet()) {
            // handle matching fields
            var fields = docCtx.metadata().matchKeys(fieldMatcher);
            for (Entry<String, List<String>> en : fields.entrySet()) {
                aborted = readField(textConsumer, aborted, en);
            }
        } else {
            // handle body
            aborted |= handleChunk(null, 0, docCtx.input(), textConsumer);
        }
        return !aborted;
    }

    private boolean readField(
            FailableFunction<TextChunk, Boolean, IOException> textConsumer,
            boolean aborted, Entry<String, List<String>> en)
            throws IOException {
        for (var i = 0; i < en.getValue().size(); i++) {
            var val = en.getValue().get(i);
            if (maxChunkSize > -1 && val.length() > maxChunkSize) {
                aborted |= !handleChunk(
                        en.getKey(),
                        i,
                        new ReadAdapter(
                                new ByteArrayInputStream(val.getBytes())),
                        textConsumer);
            } else {
                textConsumer.apply(
                        new TextChunk(en.getKey(), i, 0, val));
            }
        }
        return aborted;
    }

    private boolean handleChunk(
            String fieldName,
            int fieldValueIndex,
            ReadAdapter readAdapter,
            FailableFunction<TextChunk, Boolean, IOException> textConsumer)
                    throws IOException {
        return readAdapter.asChunkedText((idx, text) ->
                textConsumer.apply(new TextChunk(
                        fieldName, fieldValueIndex, idx, text)),
                new ChunkedReadOptions()
                    .charset(charset)
                    .maxChunkSize(maxChunkSize)
                    .skipEmpty(skipEmpty));

    }
}
