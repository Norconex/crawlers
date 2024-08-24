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
package com.norconex.importer.handler;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.io.CachedOutputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.charset.CharsetUtil;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocContext;
import com.norconex.importer.handler.condition.Condition;
import com.norconex.importer.handler.parser.ParseState;
import com.norconex.importer.util.ReadAdapter;
import com.norconex.importer.util.WriteAdapter;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Builder
@Data
@Accessors(fluent = true)
@Getter
public class HandlerContext {

    private final List<Doc> childDocs = new ArrayList<>();

    @Getter(value = AccessLevel.NONE)
    @Setter(value = AccessLevel.NONE)
    private CachedOutputStream out;

    @Getter(value = AccessLevel.PACKAGE)
    @NonNull
    private final Doc doc;

    @NonNull
    @Default
    private ParseState parseState = ParseState.PRE;

    /**
     * Closest wrapping {@link Condition}, if applicable.
     * @param condition a condition
     * @return condition
     */
    @SuppressWarnings("javadoc")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Condition condition;

    @NonNull
    private final EventManager eventManager;

    private Object rejectedBy;

    public DocContext docRecord() {
        return doc.getDocContext();
    }
    public Properties metadata() {
        return doc.getMetadata();
    }
    public String reference() {
        return doc.getReference();
    }
    public CachedStreamFactory streamFactory() {
        return doc.getStreamFactory();
    }
    public boolean isRejected() {
        return rejectedBy != null;
    }

    /**
     * Return the most appropriate charset for the current context. Logic
     * (in order):
     * <ul>
     *  <li>If the document has been parsed already, return UTF-8.</li>
     *  <li>If the provided Charset if not <code>null</code>, return it.</li>
     *  <li>If the document has a Charset on it, return it.</li>
     *  <li>Fallback to UTF-8.</li>
     * </ul>
     * @param charset the charset to use if non-null and the document has not
     *     been parsed yet.
     * @return a character set (never <code>null</code>)
     */
    public Charset resolveCharset(Charset charset) {
        return CharsetUtil.firstNonNullOrUTF8(
                parseState,
                charset,
                docRecord().getCharset(),
                StandardCharsets.UTF_8);
    }

    /**
     * Flush and dispose any output that has been written to with
     * {@link #output()} and apply it as the input source of the underlying
     * document.
     * @throws IOException
     */
    public synchronized void flush() throws IOException {




        // PROBLEM: when Cached output stream is wrapped in a writer,
        // the writer won't call the write method on it if we are
        // writing an empty string.  That way we can't rely on
        // "isCacheEmpty" to find out if the content was intentionally
        // blanked out.



        if (out != null && !out.isCacheEmpty()) {
            doc.setInputStream(out.getInputStream());
            out.dispose();
            out = null;
        }
    }

    /**
     * Flushes any output and returns an adapter for  the document input..
     * @return document input adapter
     * @throws IOException
     */
    public synchronized ReadAdapter input() throws IOException {
        flush();
        return new ReadAdapter(
                doc::getInputStream,
                CharsetUtil.firstNonNullOrUTF8(
                        parseState, docRecord().getCharset()));
    }
    /**
     * Make sure to close the stream when done or explictely flush the stream.
     * @return output adapter
     */
    public synchronized WriteAdapter output() {
        out = streamFactory().newOuputStream();
        return new WriteAdapter(out);
    }
}
