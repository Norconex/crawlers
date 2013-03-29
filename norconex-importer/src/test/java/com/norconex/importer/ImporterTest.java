package com.norconex.importer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.transformer.IDocumentTransformer;

public class ImporterTest {

    private Importer importer;
    
    @Before
    public void setUp() throws Exception {
        ImporterConfig config = new ImporterConfig();
        config.setTransformers(new IDocumentTransformer[] {
                new IDocumentTransformer() {
            private static final long serialVersionUID = -4814791150728184883L;
            Pattern pattern = Pattern.compile("[^a-zA-Z ]", Pattern.MULTILINE);
            @Override
            public void transformDocument(String reference, Reader input,
                    Writer output, Properties metadata) throws IOException {
                // Clean up what we know is extra noise for a given format
                String txt = IOUtils.toString(input);
                txt = pattern.matcher(txt).replaceAll("");
                txt = txt.replaceAll("DowntheRabbitHole", "");
                txt = StringUtils.replace(txt, " ", "");
                txt = StringUtils.replace(txt, "httppdfreebooksorg", "");
                IOUtils.write(txt, output);
            }
        }});
        importer = new Importer(config);
    }

    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void testImportDocument() throws IOException {
        // Save the same story found under three format and compare body outputs
        String inputName = 
                "examples/books/alice-in-wonderland-book-chapter-1";
        
        // MS Doc
        File docxInput = new File(inputName + ".docx");
        File docxOutput = File.createTempFile("ImporterTest-doc-", ".txt");
        Properties metaDocx = new Properties();
        importer.importDocument(docxInput, docxOutput, metaDocx);
        
        // PDF
        File pdfInput = new File(inputName + ".pdf");
        File pdfOutput = File.createTempFile("ImporterTest-pdf-", ".txt");
        Properties metaPdf = new Properties();
        importer.importDocument(pdfInput, pdfOutput, metaPdf);

        // ZIP/RTF
        File rtfInput = new File(inputName + ".zip");
        File rtfOutput = File.createTempFile("ImporterTest-zip-rtf-", ".txt");
        Properties metaRtf = new Properties();
        importer.importDocument(rtfInput, rtfOutput, metaRtf);

        Assert.assertTrue("Converted file size is too small to be valid.",
                pdfOutput.length() > 10);

        double doc = docxOutput.length();
        double pdf = pdfOutput.length();
        double rtf = rtfOutput.length();
        if (Math.abs(pdf - doc) / 1024.0 > 0.03 
                || Math.abs(pdf - rtf) / 1024.0 > 0.03) {
            Assert.fail("Content extracted from examples documents are too "
                    + "different from each other. They were not deleted to "
                    + "help you troubleshoot under: " 
                    + FileUtils.getTempDirectoryPath() + "ImporterTest-*");
        } else {
            FileUtils.deleteQuietly(docxOutput);
            FileUtils.deleteQuietly(pdfOutput);
            FileUtils.deleteQuietly(rtfOutput);
        }
    }

}
