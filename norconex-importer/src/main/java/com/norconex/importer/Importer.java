package com.norconex.importer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;

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
        File inputFile = new File(cmd.getOptionValue("inputFile"));
        ContentType contentType = 
                ContentType.newContentType(cmd.getOptionValue("contentType"));
        String output = cmd.getOptionValue("outputFile");
        if (StringUtils.isBlank(output)) {
            output = cmd.getOptionValue("inputFile") + "-imported.txt";
        }
        File outputFile = new File(output);
        File metadataFile = new File(output + ".meta");
        String reference = cmd.getOptionValue("reference");
        Properties metadata = new Properties();
        try {
            ImporterConfig config = null;
            if (cmd.hasOption("config")) {
                config = ImporterConfigLoader.loadImporterConfig(
                        new File(cmd.getOptionValue("config")), null);
            }
            new Importer(config).importDocument(
                    inputFile, contentType, outputFile, metadata, reference);
            FileOutputStream out = new FileOutputStream(metadataFile);
            metadata.store(out, null);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
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
        FileWriter inputWriter = new FileWriter(tmpInput);
        IOUtils.copy(input, inputWriter);

        File tmpOutput = File.createTempFile("NorconexImporter", "output");
        
        ContentType finalContentType = contentType;
        if (finalContentType == null) {
            Tika tika = new Tika();
            finalContentType = ContentType.newContentType(tika.detect(input));
        }
        return importDocument(
                tmpInput, contentType, tmpOutput, metadata, docReference);
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
            File input, ContentType contentType, 
            File output, Properties metadata, String docReference)
            throws IOException {

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
    	metadata.addString(
    	        DOC_CONTENT_TYPE, finalContentType.toString()); 
        
    	parseDocument(input, finalContentType, output, metadata, finalDocRef);
        tagDocument(finalDocRef, output, metadata);
        transformDocument(finalDocRef, output, metadata);
        boolean accepted = acceptDocument(output, metadata);
        if (!accepted) {
            return false;
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
        } catch (ParseException e1) {
            e1.printStackTrace();
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
            // TODO handle me
            e.printStackTrace();
        }
        input.close();
        output.close();
    }

    private void tagDocument(
            String docReference, File outputFile, Properties metadata)
            throws IOException {
        IDocumentTagger[] taggers = importerConfig.getTaggers();
        if (taggers == null) {
            return;
        }
        for (IDocumentTagger tagger : taggers) {
            FileReader reader = new FileReader(outputFile);
            tagger.tagDocument(docReference, reader, metadata);
            reader.close();
        }
    }
    
    private void transformDocument(
            String docReference, File outputFile, Properties metadata)
            throws IOException {
        IDocumentTransformer[] trsfmrs = importerConfig.getTransformers();
        if (trsfmrs == null) {
            return;
        }
        for (IDocumentTransformer transformer : trsfmrs) {
            transformDocument(docReference, transformer, outputFile, metadata);
        }
    }
    
    private boolean acceptDocument(
            File outFile, Properties metadata)
            throws IOException {
        IDocumentFilter[] filters = importerConfig.getFilters();
        if (filters == null) {
            return true;
        }
        for (IDocumentFilter filter : filters) {
            FileReader reader = new FileReader(outFile);
            boolean accepted = filter.acceptDocument(reader, metadata);
            reader.close();
            if (!accepted) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Document import rejected. Filter=" + filter);
                }
                return false;
            }
        }
        return true;
    }

    private void transformDocument(
            String docReference,
            IDocumentTransformer transformer,
            File inputFile, Properties metadata)
            throws IOException {
        File outputFile = new File(inputFile.getAbsolutePath()
                + "." + System.currentTimeMillis());
        FileReader reader = new FileReader(inputFile);
        FileWriter writer = new FileWriter(outputFile);
        transformer.transformDocument(docReference, reader, writer, metadata);
        reader.close();
        writer.close();
        FileUtils.deleteQuietly(inputFile);
        FileUtils.moveFile(outputFile, inputFile);
        if (LOG.isDebugEnabled() && inputFile.length() == 0) {
            LOG.debug("Transformer \"" + transformer.getClass()
                    + "\" did not return any content for: " + docReference);
        }
    }
}
