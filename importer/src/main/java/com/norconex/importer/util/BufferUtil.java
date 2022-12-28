/* Copyright 2010-2022 Norconex Inc.
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
import java.io.Writer;

/**
 * Buffer related utility methods.
 */
public final class BufferUtil {

    public static final int MAX_CONTENT_FROM_END_TO_CUT = 1000;

    private BufferUtil() {
    }

    /**
     * Flushes the buffer to output stream.  If the buffer is considered
     * partial (e.g. containing a partial set of a huge document),
     * you can tell the method to be wise about only flushing the content
     * up to the last line break it finds, dot, or space,
     * when found before {@link #MAX_CONTENT_FROM_END_TO_CUT}. The remaining
     * content after the cut location will remain in the buffer for further use.
     * If the output writer is null, it will simply truncate the buffer content
     * without writing it anywhere.
     * @param buffer the buffer to flush
     * @param out where to write the buffer content
     * @param cutWisely whether to "cut" wisely the buffer content
     * @throws IOException when there is a problem flushing the buffer
     */
    public static void flushBuffer(
            StringBuilder buffer, Writer out, boolean cutWisely)
            throws IOException {
        String remainingText = null;
        if (cutWisely) {
            var index = -1;
            var fromIndex = 0;
            if (buffer.length() > MAX_CONTENT_FROM_END_TO_CUT) {
                fromIndex = buffer.length() - MAX_CONTENT_FROM_END_TO_CUT;
            }
            index = buffer.lastIndexOf("\n", fromIndex);
            if (index == -1) {
                index = buffer.lastIndexOf("\r", fromIndex);
            }
            if (index == -1) {
                index = buffer.lastIndexOf(". ", fromIndex);
            }
            if (index == -1) {
                index = buffer.lastIndexOf(" ", fromIndex);
            }
            if (index > -1) {
                remainingText = buffer.substring(index);
                buffer.delete(index, buffer.length());
            }
        }
        while (buffer.length() != 0) {
            var writeChunkSize =
                    Math.min(buffer.length(), MAX_CONTENT_FROM_END_TO_CUT);
            if (out != null) {
                var chars = new char[writeChunkSize];
                buffer.getChars(0, writeChunkSize, chars, 0);
                out.write(chars);
            }
            buffer.delete(0, writeChunkSize);
        }
        if (remainingText != null) {
            buffer.append(remainingText);
        }
    }
}
