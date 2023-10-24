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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
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

    public OutputStream outputStream() {
        return outputSupplier.get();
    }

    public Writer writer() {
        return writer(null);
    }
    public Writer writer(Charset charset) {
        return new OutputStreamWriter(IOUtil.toNonNullOutputStream(
                outputSupplier.get()),
                ObjectUtils.firstNonNull(
                        charset, defaultCharset, StandardCharsets.UTF_8));
    }

    public Consumer<String> chunkedText() {
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
