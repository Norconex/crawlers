package com.norconex.importer.transformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.Importer;

/**
 * <p>Base class for transformers dealing with text documents only.  Subclasses
 * can safely be used as either pre-parse or post-parse handlers.
 * </p>
 * <p>For pre-parsing, non-text documents will simply be ignored and no
 * transformation will occur.  To find out if a document is a text-one, the
 * metadata {@link Importer#DOC_CONTENT_TYPE} value is used. By default
 * any content type starting with "text/" is considered text.  Additional
 * content types to be treated as text can be specified.
 * </p>
 * <p>For post-parsing, all documents are assumed to be text.</p>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public abstract class AbstractTextTransformer implements IDocumentTransformer {

    private static final long serialVersionUID = -7465364282740091371L;
    private static final Logger LOG = 
            LogManager.getLogger(AbstractTextTransformer.class);
    
    private final List<String> extraTextTypes = new ArrayList<String>();
    
    @Override
    public final void transformDocument(String reference, InputStream input,
            OutputStream output, Properties metadata, boolean parsed)
            throws IOException {
        String type = metadata.getString(Importer.DOC_CONTENT_TYPE);
        if (parsed || StringUtils.startsWith(type, "text/")
                ||  extraTextTypes.contains(type)) {
            InputStreamReader is = new InputStreamReader(input);
            OutputStreamWriter os = new OutputStreamWriter(output);
            transformTextDocument(reference, is, os, metadata, parsed);
            os.flush();
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Content-type \"" + type + "\" does not represent a "
                    + "text file for: " + reference);
        }
    }

    protected abstract void transformTextDocument(
            String reference, Reader input,
            Writer output, Properties metadata, boolean parsed)
            throws IOException;

    
    public void addExtraTextContentType(String... contentType) {
        if (contentType == null) {
            return;
        }
        for (String type : contentType) {
            extraTextTypes.add(StringUtils.strip(type));
        }
    }

    public List<String> getExtraTextContentTypes() {
        return extraTextTypes;
    }

    @Override
    public String toString() {
        return "AbstractTextTransformer [extraTextTypes=" + extraTextTypes
                + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((extraTextTypes == null) ? 0 : extraTextTypes.hashCode());
        return result;
    }

    /**
     * Convenience method for subclasses to load extra content types
     * (attribute "extraTextContentTypes").
     * @param xml xml configuration
     */
    protected final void loadFromXML(XMLConfiguration xml) {
        String[] types = StringUtils.split(
                xml.getString("[@extraTextContentTypes]", ""), ",");
        for (String type : types) {
            addExtraTextContentType(type.trim());
        }
    }
    
    /**
     * Convenience method for subclasses to save extra content types
     * (attribute "extraTextContentTypes").
     * @param writer XML writer
     * @throws XMLStreamException problem saving extra content types
     */
    protected void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeAttribute("extraTextContentTypes", 
                StringUtils.join(extraTextTypes, ","));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractTextTransformer other = (AbstractTextTransformer) obj;
        if (extraTextTypes == null) {
            if (other.extraTextTypes != null)
                return false;
        } else if (!extraTextTypes.equals(other.extraTextTypes))
            return false;
        return true;
    }
}
