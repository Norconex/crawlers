/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Committer.
 * 
 * Norconex Committer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Committer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Committer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.committer.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;

import com.norconex.committer.CommitterException;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.io.FileUtil;
import com.norconex.commons.lang.map.Properties;


/**
 * Commits a copy of files on the filesystem.  Mostly useful for 
 * troubleshooting.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;committer class="com.norconex.committer.FileSystemCommitter"&gt;
 *      &lt;directory&gt;(path where to save files)&lt;/directory&gt;
 *  &lt;/committer&gt;
 * </pre>
 * @author Pascal Essiembre
 */
@SuppressWarnings("nls")
public class FileSystemCommitter implements ICommitter, IXMLConfigurable {

    private static final long serialVersionUID = 567796374790003396L;

    public static final String DEFAULT_DIRECTORY = "./committer";
    
    private String directory = DEFAULT_DIRECTORY;

    private static final int DATE_FOLDER_CHAR_SIZE = 3;
    
    public String getDirectory() {
		return directory;
	}
	public void setDirectory(String directory) {
		this.directory = directory;
	}

	@Override
    public void queueAdd(String reference, File document, Properties metadata) {
        File dir = getAddDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            File targetFile = createFile(dir);
            FileUtil.moveFile(document, targetFile);
            FileOutputStream out = new FileOutputStream(
                    new File(targetFile.getAbsolutePath() + ".meta"));
            metadata.store(out, "");
            IOUtils.closeQuietly(out);
        } catch (IOException e) {
            throw new CommitterException(
            		"Cannot queue document addition.  Ref: " + reference
                  + " File: " + document, e);
        }
    }
    @Override
    public void queueRemove(
            String reference, File document, Properties metadata) {
        File dir = getRemoveDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            File targetFile = createFile(dir);
            org.apache.commons.io.FileUtils.writeStringToFile(
            		targetFile, reference);
            document.delete();
        } catch (IOException e) {
            throw new CommitterException(
            		"Cannot queue document removal.  Ref: " + reference, e);
        }
    }

    @Override
    public void commit() {
    	//DO NOTHING
    }

    
    public File getAddDir() {
        return new File(directory + SystemUtils.FILE_SEPARATOR + "add");
    }
    public File getRemoveDir() {
        return new File(directory + SystemUtils.FILE_SEPARATOR + "remove");
    }
    private synchronized File createFile(File dir) throws IOException {
        File addFile = null;
        do {
            String time = Long.toString(System.currentTimeMillis());
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < time.length(); i++) {
                if (i % DATE_FOLDER_CHAR_SIZE == 0) {
                    b.append(SystemUtils.FILE_SEPARATOR);
                }
                b.append(time.charAt(i));
            }
            addFile = new File(dir.getAbsolutePath() + b.toString());
        } while (addFile.exists());
        org.apache.commons.io.FileUtils.touch(addFile);
        return addFile;
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setDirectory(xml.getString("directory", DEFAULT_DIRECTORY));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("committer");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeStartElement("directory");
            writer.writeCharacters(directory);
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((directory == null) ? 0 : directory.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        FileSystemCommitter other = (FileSystemCommitter) obj;
        if (directory == null) {
            if (other.directory != null) {
                return false;
            }
        } else if (!directory.equals(other.directory)) {
            return false;
        }
        return true;
    }
    @Override
    public String toString() {
        return "FileSystemCommitter [directory=" + directory + "]";
    }
}

