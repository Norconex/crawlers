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

import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.i18n.UncheckedException;

import com.norconex.commons.lang.io.IOUtil;

import lombok.RequiredArgsConstructor;

// Move to nx commons lang?

@RequiredArgsConstructor
public class WriteAdapter {

    private final Supplier<OutputStream> outputSupplier;
    private final Charset defaultCharset;

    public WriteAdapter(Supplier<OutputStream> outputSupplier) {
        this.outputSupplier = outputSupplier;
        defaultCharset = null;
    }

    public WriteAdapter(OutputStream outputStream) {
        outputSupplier = () -> outputStream;
        defaultCharset = null;
    }

    public OutputStream asOutputStream() {
        return outputSupplier.get();
    }

    public Writer asWriter() {
        return asWriter(null);
    }

    public Writer asWriter(Charset charset) {
        var os = outputSupplier.get();
        var writer = new OutputStreamWriter(
                IOUtil.toNonNullOutputStream(os),
                ObjectUtils.firstNonNull(
                        charset, defaultCharset, StandardCharsets.UTF_8
                )
        );
        //NOTE: we wrap this writer to overwrite the write() methods.
        // That is necessary to "write" empty strings to the underlying
        // output stream. Without this, the CachedOutputStream will not be
        // notified of a write attempt, and won't be considered "dirty",
        // which is important to detect if the blanking of it is intentional.
        // This should, in theory, have no impact on buffered streams/writers
        // as we are adding "nothing".
        return new FilterWriter(writer) {
            @Override
            public void write(String str, int off, int len) throws IOException {
                if (len == 0) {
                    os.write(StringUtils.EMPTY.getBytes());
                } else {
                    super.write(str, off, len);
                }
                flush();
            }

            @Override
            public void write(char[] cbuf, int off, int len)
                    throws IOException {
                if (len == 0) {
                    os.write(StringUtils.EMPTY.getBytes());
                } else {
                    super.write(cbuf, off, len);
                }
                flush();
            }
        };
    }

    public Consumer<String> asChunkedText() {
        var out = outputSupplier.get();
        return text -> {
            try {
                out.write(text.getBytes());
                out.flush();
            } catch (IOException e) {
                throw new UncheckedException(e);
            }
        };
    }
}
