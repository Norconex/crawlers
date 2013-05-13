/* Copyright 2010-2013 Norconex Inc.
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

import org.apache.commons.lang.StringUtils;

public final class PathUtils {

    private static final int PATH_SEGMENT_SIZE = 5;
    private static final int MAX_PATH_LENGTH = 256;
    
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
        path = path.replaceAll("[\\W]+", "_");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
        	if (i % PATH_SEGMENT_SIZE == 0) {
        		b.append(sep);
        	}
        	b.append(path.charAt(i));
		}
        path = b.toString();
        
        //TODO is truncating after 256 a risk of creating false duplicates?
        if (path.length() > MAX_PATH_LENGTH) {
            path = StringUtils.right(path, MAX_PATH_LENGTH);
            if (!path.startsWith(File.separator)) {
                path = File.separator + path;
            }
        }
        return domain + path;
    }


}
