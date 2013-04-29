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
package com.norconex.importer.transformer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * <p>Base class to facilitate creating transformers on text content, load text 
 * into {@link StringBuilder} for memory processing, also giving more options 
 * (like fancy regex).  This class check for free memory every 10KB of text
 * read.  If enough memory, it keeps going for another 10KB or until
 * all the content is read, or the buffer size reaches half the available 
 * memory.  In either case, it pass the buffer content so far for 
 * transformation (all of it for small enough content, and in several
 * chunks for large content).
 * </p>
 * <p>
 * Implementors should be conscious about memory when dealing with the string
 * builder.
 * </p>
 * <p>Subclasses implementing {@link IXMLConfigurable} should allow this inner 
 * configuration:</p>
 * <pre>
 *  &lt;contentTypeRegex&gt;
 *      (regex to identify text content-types, overridding default)
 *  &lt;/contentTypeRegex&gt;
 *  &lt;restrictTo
 *          caseSensitive="[false|true]" &gt;
 *          property="(name of header/metadata name to match)"
 *      (regular expression of value to match)
 *  &lt;/restrictTo&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public abstract class AbstractStringTransformer 
            extends AbstractCharStreamTransformer {

    //TODO maybe: Add to importer config something about max buffer memory.
    // That way, we can ensure to apply the memory check technique on content of 
    // the same size (threads should be taken into account), 
    // as opposed to have one big file take all the memory so other big files
    // are forced to do smaller chunks at a time.
    
    private static final long serialVersionUID = -2401917724782923656L;
    private static final Logger LOG = 
            LogManager.getLogger(AbstractStringTransformer.class);

    private static final int READ_CHUNK_SIZE = 10 * (int) FileUtils.ONE_KB;
    
    protected final void transformTextDocument(
            String reference, Reader input,
            Writer output, Properties metadata, boolean parsed)
            throws IOException {
        
        // Initial size is half free memory, considering chars take 2 bytes
        StringBuilder b = new StringBuilder((int)(getFreeMemory() / 4));
        int i;
        while ((i = input.read()) != -1) {
            char ch = (char) i;
            b.append(ch);
            if (b.length() * 2 % READ_CHUNK_SIZE == 0) {
                if (isTakingTooMuchMemory(b)) {
                    transformStringDocument(
                            reference, b, metadata, parsed, true);
                    flushBuffer(b, output, true);
                }
            }
        }
        if (b.length() > 0) {
            transformStringDocument(reference, b, metadata, parsed, false);
            flushBuffer(b, output, false);
        }
        b.setLength(0);
        b = null;
    }
    
    
    protected abstract void transformStringDocument(
           String reference, StringBuilder content, Properties metadata,
           boolean parsed, boolean partialContent);
   
   
    // Writes the buffer to output stream.  If content was cut-out due to memory,
    // try to cut the text wisely nead the end.
    private void flushBuffer(StringBuilder content, Writer out, boolean full)
            throws IOException {
        String remainingText = null;
        if (full) {
            int index = -1;
            int fromIndex = 0;
            if (content.length() > 1000) {
                fromIndex = content.length() - 1000;
            }
            index = content.lastIndexOf("\n", fromIndex);
            if (index == -1) {
                index = content.lastIndexOf("\r", fromIndex);
            }
            if (index == -1) {
                index = content.lastIndexOf(". ", fromIndex);
            }
            if (index == -1) {
                index = content.lastIndexOf(" ", fromIndex);
            }
            if (index > -1) {
                remainingText = content.substring(index);
                content.delete(index, content.length());
            }
        }
        while (content.length() != 0) {
            int write_chunk_size = Math.min(content.length(), 1000);
            char[] chars = new char[write_chunk_size];
            content.getChars(0, write_chunk_size, chars, 0);           
            out.write(chars);
            content.delete(0, write_chunk_size);
            chars = null;
        }
        if (remainingText != null) {
            content.append(remainingText);
        }
    }
    
    // We ensure buffer size never goes beyond half available memory.
    private boolean isTakingTooMuchMemory(StringBuilder b) {
        int maxMem = (int) getFreeMemory() / 2;
        int bufMem = b.length() * 2;
        boolean busted = bufMem > maxMem;
        if (busted) {
            LOG.warn("Text document processed via transformer is quite big for "
                + "remaining JVM memory.  It was split in text chunks and "
                + "a transformation will be applied on each chunk.  This "
                + "may sometimes result in unexpected transformation. "
                + "To eliminate this risk, increase the JVM maximum heap "
                + "space to more than double the processed content size "
                + "by using the -xmx flag to your startup script "
                + "(e.g. -xmx1024m for 1 Gigabyte).  In addition, "
                + "reducing the number of threads may help (if applicable). "
                + "As an alternative, you can also implement a new solution " 
                + "using AbstractCharSteamTransformaer instead, which relies "
                + "on streams (taking very little fixed-size memory when "
                + "done right).");
        }
        return busted;
    }
   
    private long getFreeMemory() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return runtime.freeMemory() 
                + (runtime.maxMemory() - runtime.totalMemory());
    }


    @Override
    public String toString() {
        return "AbstractStringTransformer [getContentTypeRegex()="
                + getContentTypeRegex() + "]";
    }

}