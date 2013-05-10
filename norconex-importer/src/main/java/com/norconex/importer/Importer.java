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
package com.norconex.importer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;

import com.norconex.commons.lang.io.FileUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.filter.IDocumentFilter;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.IDocumentParser;
import com.norconex.importer.parser.IDocumentParserFactory;
import com.norconex.importer.tagger.IDocumentTagger;
import com.norconex.importer.transformer.IDocumentTransformer;

/**
 * Principal class responsible for importing documents.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 *
 */
public class Importer {
    
    public static final String IMPORTER_PREFIX = "importer.";
    public static final String DOC_REFERENCE = IMPORTER_PREFIX + "reference";
    public static final String DOC_CONTENT_TYPE = 
    		IMPORTER_PREFIX + "contentType";

	private static final Logger LOG = LogManager.getLogger(Importer.class);

	private static final String ARG_INPUTFILE = "inputFile";
    private static final String ARG_OUTPUTFILE = "outputFile";
    private static final String ARG_CONTENTTYPE = "contentType";
    private static final String ARG_REFERENCE = "reference";
    private static final String ARG_CONFIG = "config";

	private final ImporterConfig importerConfig;
    
    /**
     * Creates a new importer with default configuration.
     */
    public Importer() {
        this(new ImporterConfig());
    }
    /**
     * Creates a new importer with the given configuration.
     * @param importerConfig
     */
    public Importer(ImporterConfig importerConfig) {
        super();
        if (importerConfig != null) {
            this.importerConfig = importerConfig;
        } else {
            this.importerConfig = new ImporterConfig();
        }
    }

    /**
     * Invokes the importer from the command line.  
     * @param args Invoke it once without any arguments to get a 
     *    list of command-line options.
     */
    public static void main(String[] args) {
        
        CommandLine cmd = parseCommandLineArguments(args);
        File inputFile = new File(cmd.getOptionValue(ARG_INPUTFILE));
        ContentType contentType = 
                ContentType.newContentType(cmd.getOptionValue(ARG_CONTENTTYPE));
        String output = cmd.getOptionValue(ARG_OUTPUTFILE);
        if (StringUtils.isBlank(output)) {
            output = cmd.getOptionValue(ARG_INPUTFILE) + "-imported.txt";
        }
        File outputFile = new File(output);
        File metadataFile = new File(output + ".meta");
        String reference = cmd.getOptionValue(ARG_REFERENCE);
        Properties metadata = new Properties();
        try {
            ImporterConfig config = null;
            if (cmd.hasOption(ARG_CONFIG)) {
                config = ImporterConfigLoader.loadImporterConfig(
                        new File(cmd.getOptionValue(ARG_CONFIG)), null);
            }
            new Importer(config).importDocument(
                    inputFile, contentType, outputFile, metadata, reference);
            FileOutputStream out = new FileOutputStream(metadataFile);
            metadata.store(out, null);
            out.close();
        } catch (Exception e) {
            System.err.println("A problem occured while importing " + inputFile);
            e.printStackTrace(System.err);
        }
    }

    /**
     * Imports a document according to the importer configuration.
     * @param input document input
     * @param output document output
     * @param metadata the document starting metadata
     * @return <code>true</code> if the document has successfully been imported,
     *         <code>false</code> if the document was rejected (i.e. filtered)
     * @throws IOException problem importing document
     */
    public boolean importDocument(
            InputStream input, Writer output, Properties metadata)
            throws IOException {
        return importDocument(input, null, output, metadata, null);
    }
    /**
     * Imports a document according to the importer configuration.
     * @param input document input
     * @param contentType document content-type
     * @param output document output
     * @param metadata the document starting metadata
     * @return <code>true</code> if the document has successfully been imported,
     *         <code>false</code> if the document was rejected (i.e. filtered)
     * @param docReference document reference (e.g. URL, file path, etc)
     * @throws IOException problem importing document
     */
    public boolean importDocument(
            InputStream input, ContentType contentType, 
            Writer output, Properties metadata, String docReference)
            throws IOException {
        File tmpInput = File.createTempFile("NorconexImporter", "input");
        FileOutputStream out = new FileOutputStream(tmpInput);
        IOUtils.copy(input, out);
        out.close();

        File tmpOutput = File.createTempFile("NorconexImporter", "output");
        
        ContentType finalContentType = contentType;
        if (finalContentType == null) {
            Tika tika = new Tika();
            finalContentType = ContentType.newContentType(
                    tika.detect(tmpInput));
        }
        boolean accepted = importDocument(
                tmpInput, contentType, tmpOutput, metadata, docReference);
        InputStream is = new FileInputStream(tmpOutput);
        IOUtils.copy(is, output);
        is.close();
        return accepted;
    }
    /**
     * Imports a document according to the importer configuration.
     * @param input document input
     * @param output document output
     * @param metadata the document starting metadata
     * @return <code>true</code> if the document has successfully been imported,
     *         <code>false</code> if the document was rejected (i.e. filtered)
     * @throws IOException problem importing document
     */
    public boolean importDocument(
            File input, File output, Properties metadata)
            throws IOException {
        return importDocument(input, null, output, metadata, null);
    }
    /**
     * Imports a document according to the importer configuration.
     * @param input document input
     * @param contentType document content-type
     * @param output document output
     * @param metadata the document starting metadata
     * @return <code>true</code> if the document has successfully been imported,
     *         <code>false</code> if the document was rejected (i.e. filtered)
     * @param docReference document reference (e.g. URL, file path, etc)
     * @throws IOException problem importing document
     */    
    public boolean importDocument(
            final File input, ContentType contentType, 
            File output, Properties metadata, String docReference)
            throws IOException {

        MutableObject<File> workFile = new MutableObject<File>(input);
        
        ContentType finalContentType = contentType;
        if (finalContentType == null 
                || StringUtils.isBlank(finalContentType.toString())) {
            Tika tika = new Tika();
            finalContentType = ContentType.newContentType(tika.detect(input));
        }
        String finalDocRef = docReference;
        if (StringUtils.isBlank(docReference)) {
            finalDocRef = input.getAbsolutePath();
        }
        
        metadata.addString(DOC_REFERENCE, finalDocRef); 
    	metadata.addString(DOC_CONTENT_TYPE, finalContentType.toString()); 
        
    	if (!executeHandlers(docReference, input, workFile, metadata, 
    	        importerConfig.getPreParseHandlers(), false)) {
    	    return false;
    	}
    	
    	parseDocument(workFile.getValue(), 
    	        finalContentType, output, metadata, finalDocRef);
    	workFile.setValue(output);

    	if (!executeHandlers(docReference, input, workFile, metadata, 
                importerConfig.getPostParseHandlers(), true)) {
            return false;
        }
    	
    	if (!workFile.getValue().equals(output)) {
            FileUtil.moveFile(workFile.getValue(), output);
    	}
        return true;
    }
    
    private boolean executeHandlers(
            String docReference, File rawImportedFile, 
            MutableObject<File> inFile, Properties metadata, 
            IImportHandler[] handlers, boolean parsed)
            throws IOException {
        if (handlers == null) {
            return true;
        }
        
        for (IImportHandler h : handlers) {
            if (h instanceof IDocumentTagger) {
                tagDocument(docReference, (IDocumentTagger) h, 
                        inFile.getValue(), metadata, parsed);
            } else if (h instanceof IDocumentTransformer) {
                transformDocument(docReference, (IDocumentTransformer) h, 
                        rawImportedFile, inFile, metadata, parsed);
            } else if (h instanceof IDocumentFilter) {
                if (!acceptDocument((IDocumentFilter) h, inFile.getValue(), 
                        metadata, parsed)){
                    return false;
                }
            } else {
                LOG.error("Unsupported Import Handler: " + h);
            }
        }        
        return true;
    }
    
    private static CommandLine parseCommandLineArguments(String[] args) {
        Options options = new Options();
        options.addOption("i", "inputFile", true, 
                "Required: File to be imported.");
        options.addOption("o", "outputFile", true, 
                "Optional: File where the imported content will be stored.");
        options.addOption("t", "contentType", true, 
                "Optional: The MIME Content-type of the input file.");
        options.addOption("r", "reference", true, 
                "Optional: Alternate unique qualifier for the input file "
              + "(e.g. URL).");
        options.addOption("c", "config", true, 
                "Optional: Importer XML configuration file.");
   
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse( options, args);
            if(!cmd.hasOption("inputFile")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "importer[.bat|.sh]", options );
                System.exit(-1);
            }
        } catch (ParseException e) {
            System.err.println("A problem occured while parsing arguments.");
            e.printStackTrace(System.err);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "importer[.bat|.sh]", options );
            System.exit(-1);
        }
        return cmd;
    }
    
    private void parseDocument(
            File rawFile, ContentType contentType, 
            File outputFile, Properties metadata, String docReference)
            throws IOException {
        
        InputStream input = TikaInputStream.get(rawFile);
        Writer output = new FileWriter(outputFile);
        IDocumentParserFactory factory = importerConfig.getParserFactory();
        IDocumentParser parser = factory.getParser(docReference, contentType);
        try {
            parser.parseDocument(input, contentType, output, metadata);
        } catch (DocumentParserException e) {
            LOG.error("A problem occured while parsing " + rawFile, e);
        }
        input.close();
        output.close();
    }

    private void tagDocument(String docReference, 
            IDocumentTagger tagger, File inputFile, 
            Properties metadata, boolean parsed)
            throws IOException {
        FileInputStream is = new FileInputStream(inputFile);
        tagger.tagDocument(docReference, is, metadata, parsed);
        is.close();
    }
    
    private boolean acceptDocument(
            IDocumentFilter filter, File outFile, 
            Properties metadata, boolean parsed)
            throws IOException {
        FileInputStream reader = new FileInputStream(outFile);
        boolean accepted = filter.acceptDocument(reader, metadata, parsed);
        reader.close();
        if (!accepted) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Document import rejected. Filter=" + filter);
            }
            return false;
        }
        return true;
    }

    private void transformDocument(
            String docReference, IDocumentTransformer transformer,
            File rawImportedFile, MutableObject<File> inFile,
            Properties metadata, boolean parsed)
            throws IOException {
        String inPath = inFile.getValue().getAbsolutePath();
        File outputFile = new File(
                FileUtils.getTempDirectoryPath()
              + "/" + FilenameUtils.getBaseName(inPath) 
              + "-" + System.currentTimeMillis()
              + "." + FilenameUtils.getExtension(inPath));
        
        FileInputStream in = new FileInputStream(inFile.getValue());
        FileOutputStream out = new FileOutputStream(outputFile);
        long fileSize = outputFile.length();
        long lastModified = outputFile.lastModified();
        
        transformer.transformDocument(docReference, in, out, metadata, parsed);
        in.close();
        out.close();

        if (outputFile.lastModified() != lastModified 
                || outputFile.length() != fileSize) {
            if (!inFile.getValue().equals(rawImportedFile)) {
                FileUtils.deleteQuietly(inFile.getValue());
            }
            inFile.setValue(outputFile);
            if (LOG.isDebugEnabled() && outputFile.length() == 0) {
                LOG.debug("Transformer \"" + transformer.getClass()
                        + "\" did not return any content for: " + docReference);
            }
        }
    }
}
