package com.norconex.importer;

import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.ExpressionEngine;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.ConfigurationUtil;

/**
 * Importer configuration loader.  Configuration options are defined
 * as part of general product documentation.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
@SuppressWarnings("nls")
public final class ImporterConfigLoader {

    private ImporterConfigLoader() {
        super();
    }

    /**
     * Loads importer configuration.
     * @param configFile configuration file
     * @param configVariables configuration variables 
     *        (.variables or .properties)
     * @return importer configuration
     * @throws ConfigurationException problem loading configuration
     */
    public static ImporterConfig loadImporterConfig(
            File configFile, File configVariables) 
                    throws ConfigurationException {
        try {
            ConfigurationLoader configLoader = new ConfigurationLoader();
            XMLConfiguration xml = configLoader.loadXML(
                    configFile, configVariables);
            return loadImporterConfig(xml);
        } catch (Exception e) {
            throw new ConfigurationException(
                    "Could not load configuration file: " + configFile, e);
        }
    }    
    
    
    public static ImporterConfig loadImporterConfig(Reader config)
            throws ConfigurationException {
        try {
            XMLConfiguration xml = ConfigurationLoader.loadXML(config);
            return loadImporterConfig(xml);
        } catch (Exception e) {
            throw new ConfigurationException(
                    "Could not load configuration file from Reader.", e);
        }
    }
    
    
    /**
     * Loads importer configuration.
     * @param xml XMLConfiguration instance
     * @return importer configuration
     * @throws ConfigurationException problem loading configuration
     */
    public static ImporterConfig loadImporterConfig(
            XMLConfiguration xml) throws ConfigurationException {
        if (xml == null) {
            return null;
        }
        ImporterConfig config = new ImporterConfig();
        try {
            //--- Pre-Import Handlers ------------------------------------------
            config.setPreParseHandlers(
                    loadImportHandlers(xml, "preParseHandlers"));

            //--- Document Parser Factory --------------------------------------
            config.setParserFactory(ConfigurationUtil.newInstance(
                    xml, "documentParserFactory", config.getParserFactory()));

            //--- Post-Import Handlers -----------------------------------------
            config.setPostParseHandlers(
                    loadImportHandlers(xml, "postParseHandlers"));
        } catch (Exception e) {
            throw new ConfigurationException("Could not load configuration "
                    + "from XMLConfiguration instance.", e);
        }
        return config;
    }
    
    private static IImportHandler[] loadImportHandlers(
            XMLConfiguration xml, String xmlPath) throws Exception {
        List<IImportHandler> handlers = new ArrayList<IImportHandler>();

        ExpressionEngine originalEngine = xml.getExpressionEngine();
        xml.setExpressionEngine(new XPathExpressionEngine());
        List<HierarchicalConfiguration> xmlHandlers = 
                xml.configurationsAt(xmlPath + "/*");
        xml.setExpressionEngine(originalEngine);
        for (HierarchicalConfiguration xmlHandler : xmlHandlers) {
            handlers.add(
                    (IImportHandler) ConfigurationUtil.newInstance(xmlHandler));
        }
        return handlers.toArray(new IImportHandler[]{});
    }
}
