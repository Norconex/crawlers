package com.norconex.collector.http.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.StringUtils;

public class PathUtils {

    @Deprecated
    //TODO Time will tell if this one was better
    public static String urlToPath2(String url) {
        if (url == null) {
            return null;
        }
        String sep = File.separator;
        String[] parts = url.split("[\\W\\-_]");
        String path = StringUtils.join(parts, sep);
        if (sep.equals("\\")) {
            sep = "\\" + sep;
        }
        path = path.replaceAll("[\\\\\\/]+", sep);
        
        //TODO is truncating after 256 a risk of creating false duplicates?
        if (path.length() > 256) {
            path = StringUtils.right(path, 256);
            if (path.startsWith(File.separator)) {
                path = StringUtils.removeStart(path, File.separator);
            }
        }
        return path;
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
    public static String ensureDirectoryForFile(String file)
            throws IOException {
        return ensureDirectoryForFile(new File(file)).getAbsolutePath();
        
    }
    public static File ensureDirectoryForFile(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            FileUtils.forceMkdir(parent);
            return parent;
        }
        return new File("/");
    }

    public static void moveFile(File sourceFile, File targetFile)
            throws IOException {
        FileUtils.copyFile(sourceFile, targetFile);
        FileUtils.deleteQuietly(sourceFile);
    }

    public static void moveFileToDirectory(String file, String dir)
            throws IOException {
        moveFileToDirectory(new File(file), new File(dir));
    }
    public static void moveFileToDirectory(File file, File dir)
            throws IOException {
        FileUtils.copyFileToDirectory(file, dir);
        FileUtils.deleteQuietly(file);
    }

    public static String getRelativePath(String path, String baseDir)
            throws IOException {
        String absolutePath = new File(path).getAbsolutePath();
        String basedirPath = new File(baseDir).getAbsolutePath();
        return StringUtils.removeStart(absolutePath, basedirPath);
    }

    public static int removeEmptyDirectories(File baseDir) {
        int count = 0;
        String[] files = baseDir.list(DirectoryFileFilter.INSTANCE);
        if (files == null) {
            return count;
        }
        for (String fileStr : files) {
            File file = new File(baseDir.getAbsolutePath() + "/" + fileStr);
            if (file.list().length == 0) {
                FileUtils.deleteQuietly(file);
                count++;
            } else {
                count += removeEmptyDirectories(file);
                if (file.list().length == 0) {
                    FileUtils.deleteQuietly(file);
                }
            }
        }
        return count;
    }

}
