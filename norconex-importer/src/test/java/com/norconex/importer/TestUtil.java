package com.norconex.importer;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class TestUtil {

    private static final String BASE_PATH = 
            "src/main/examples/books/alice-in-wonderland-book-chapter-1";
    
    private TestUtil() {
        super();
    }

    public static File getAlicePdfFile() {
        return new File(BASE_PATH + ".pdf");
    }
    public static File getAliceDocxFile() {
        return new File(BASE_PATH + ".docx");
    }
    public static File getAliceZipFile() {
        return new File(BASE_PATH + ".zip");
    }
    public static File getAliceHtmlFile() {
        return new File(BASE_PATH + ".html");
    }
    public static Importer getTestConfigImporter() {
        InputStream is = TestUtil.class.getResourceAsStream("test-config.xml");
        ImporterConfig config = ImporterConfigLoader.loadImporterConfig(
                new InputStreamReader(is));
        return new Importer(config);
    }
}
