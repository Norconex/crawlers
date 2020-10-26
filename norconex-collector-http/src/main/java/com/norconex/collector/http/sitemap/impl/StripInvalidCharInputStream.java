/* Copyright 2020 Norconex Inc.
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
package com.norconex.collector.http.sitemap.impl;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

class StripInvalidCharInputStream extends FilterInputStream {
    private static final Logger LOG = LogManager.getLogger(
            StripInvalidCharInputStream.class);

    private static final byte AMP_BYTE = "&".getBytes()[0];

    private boolean started;

    protected StripInvalidCharInputStream(InputStream in) {
        super(in);
        started = false;
    }

    @Override
    public int read(byte[] cbuf, int off, int len) throws IOException {
        int read = super.read(cbuf, off, len);
        if (read == -1) {
            return -1;
        }
        int pos = off - 1;
        for (int readPos = off; readPos < off + read; readPos++) {
            // ignore invalid XML 1.0 chars
            if (isInvalid(cbuf[readPos]) || isInvalid(cbuf, readPos)
                    || isBlankStart(cbuf, readPos)) {
                LOG.info("found control character: " + cbuf[readPos]);
                continue;
            } else {
                pos++;
            }
            if (pos < readPos) {
                cbuf[pos] = cbuf[readPos];
            }
        }
        return pos - off + 1;
    }

    @Override
    public synchronized void reset() throws IOException {
        super.reset();
        started = false;
    }

    public boolean isBlankStart(byte[] cbuf, int readPos) {
        if(!started && cbuf[readPos] != '<') {
            return true;
        } else {
            started = true;
            return false;
        }
    }

    public static boolean isInvalid(byte[] cbuf, int pos) {
        if (AMP_BYTE != cbuf[pos]) {
            return false;
        }
        // Grabs up to 20 bytes to check if a valid entity.
        // Not the most efficient, but handles more cases.
        String txt = new String(cbuf, pos, Math.min(20, cbuf.length - pos));
        return !txt.matches(
                "(?i)^&(#[0-9]+|#x[0-9a-f]+|amp|quot|apos|lt|gt);.*");
    }

    public static boolean isInvalid(byte c) {
        return ((c >= 0x00 && c <= 0x08) ||
                (c >= 0x0b && c <= 0x0c) ||
                (c >= 0x0e && c <= 0x1F));
    }
}