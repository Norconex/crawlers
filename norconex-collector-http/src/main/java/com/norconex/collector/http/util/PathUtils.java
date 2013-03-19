package com.norconex.collector.http.util;

import java.io.File;

import org.apache.commons.lang.StringUtils;

public class PathUtils {

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
        	if (i % 5 == 0) {
        		b.append(sep);
        	}
        	b.append(path.charAt(i));
		}
        path = b.toString();
        
        //TODO is truncating after 256 a risk of creating false duplicates?
        if (path.length() > 256) {
            path = StringUtils.right(path, 256);
            if (!path.startsWith(File.separator)) {
                path = File.separator + path;
            }
        }
        return domain + path;
    }


}
