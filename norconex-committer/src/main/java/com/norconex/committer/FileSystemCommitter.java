package com.norconex.committer;

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
import org.apache.commons.lang.SystemUtils;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.io.FileUtils;
import com.norconex.commons.lang.meta.Metadata;


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
public class FileSystemCommitter implements ICommitter, IXMLConfigurable {

//	private static final Logger LOG = LogManager.getLogger(
//			FileSystemCommitter.class);

    private static final long serialVersionUID = 567796374790003396L;

    public static final String COMMITTER_PREFIX = "committer.";
    public static final String DOC_REFERENCE = COMMITTER_PREFIX + "reference";

    public static final String DEFAULT_DIRECTORY = "./committer";
    
    private String directory = DEFAULT_DIRECTORY;

    public String getDirectory() {
		return directory;
	}
	public void setDirectory(String directory) {
		this.directory = directory;
	}

	@Override
    public void queueAdd(String reference, File document, Metadata metadata) {
	    metadata.addPropertyValue(DOC_REFERENCE, reference);
        File dir = getAddDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            File targetFile = createFile(dir);
            FileUtils.moveFile(document, targetFile);
            FileOutputStream out = new FileOutputStream(
                    new File(targetFile.getAbsolutePath() + ".meta"));
            metadata.store(out);
            IOUtils.closeQuietly(out);
        } catch (IOException e) {
            throw new CommitterException(
            		"Cannot queue document addition.  Ref: " + reference
                  + " File: " + document, e);
        }
    }
    @Override
    public void queueRemove(
            String reference, File document, Metadata metadata) {
        metadata.addPropertyValue(DOC_REFERENCE, reference);
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
                if (i % 3 == 0) {
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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FileSystemCommitter other = (FileSystemCommitter) obj;
        if (directory == null) {
            if (other.directory != null)
                return false;
        } else if (!directory.equals(other.directory))
            return false;
        return true;
    }
}

