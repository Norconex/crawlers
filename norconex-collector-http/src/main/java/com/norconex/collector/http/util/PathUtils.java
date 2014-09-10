/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.file.FileUtil;

public final class PathUtils {

    private static final int MAX_PATH_LENGTH = 25;
    
    private PathUtils() {
        super();
    }

    public static String urlToPath(final String url) {
        if (url == null) {
            return null;
        }
        String sep = File.separator;
        if (sep.equals("\\")) {
            sep = "\\" + sep;
        }
        
        String domain = url.replaceFirst("(.*?)(://)(.*?)(/)(.*)", "$1_$3");
        domain = domain.replaceAll("[\\W]+", "_");
        String path = url.replaceFirst("(.*?)(://)(.*?)(/)(.*)", "$5");
        
        String[] segments = path.split("[\\/]");
        StringBuilder b = new StringBuilder();
        for (String segment : segments) {
            if (StringUtils.isNotBlank(segment)) {
                String[] segParts = splitLargeSegment(segment);
                for (String segPart : segParts) {
                    if (b.length() > 0) {
                        b.append(File.separatorChar);
                    }
                    b.append(FileUtil.toSafeFileName(segPart));
                }
            }
        }
        return domain + File.separatorChar + b.toString();
    }
    
    private static String[] splitLargeSegment(String segment) {
        if (segment.length() <= MAX_PATH_LENGTH) {
            return new String[] { segment };
        }
        
        List<String> segments = new ArrayList<>();
        StringBuilder b = new StringBuilder(segment);
        while (b.length() > MAX_PATH_LENGTH) {
            segments.add(b.substring(0, MAX_PATH_LENGTH));
            b.delete(0, MAX_PATH_LENGTH);
        }
        segments.add(b.substring(0));
        return segments.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }
}
