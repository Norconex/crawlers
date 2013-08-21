/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer.util;

import java.io.IOException;
import java.io.Writer;

/**
 * Buffer related utility methods.
 * @author Pascal Essiembre
 */
public final class BufferUtil {

    public static final int MAX_CONTENT_FROM_END_TO_CUT = 1000;
    
    private BufferUtil() {
        super();
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
            int index = -1;
            int fromIndex = 0;
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
            int writeChunkSize = 
                    Math.min(buffer.length(), MAX_CONTENT_FROM_END_TO_CUT);
            if (out != null) {
                char[] chars = new char[writeChunkSize];
                buffer.getChars(0, writeChunkSize, chars, 0);
                out.write(chars);
                chars = null;
            }
            buffer.delete(0, writeChunkSize);
        }
        if (remainingText != null) {
            buffer.append(remainingText);
        }
    }
}
