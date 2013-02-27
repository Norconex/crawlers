package com.norconex.importer;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.importer.filter.IDocumentFilter;
import com.norconex.importer.parser.IDocumentParserFactory;
import com.norconex.importer.tagger.IDocumentTagger;
import com.norconex.importer.transformer.IDocumentTransformer;

@SuppressWarnings("nls")
public final class ImporterConfigLoader {

    private static final Logger LOG = LogManager.getLogger(
            ImporterConfigLoader.class);
    
    private ImporterConfigLoader() {
        super();
    }

    public static ImporterConfig loadImporterConfig(
            File configFile, File configVariables) throws Exception {
        ConfigurationLoader configLoader = new ConfigurationLoader();
        XMLConfiguration xml = configLoader.loadXML(
                configFile, configVariables);
        return loadImporterConfig(xml);
    }    
    
    public static ImporterConfig loadImporterConfig(
            XMLConfiguration xml) throws Exception {

        if (xml == null) {
            return null;
        }
        
        ImporterConfig config = new ImporterConfig();
        
        //--- Document Parser Factory ------------------------------------------
        config.setParserFactory((IDocumentParserFactory) newInstance(
                new XMLConfiguration(configurationAt(
                        xml, "documentParserFactory")),
                config.getParserFactory()));

        //--- Document Taggers -------------------------------------------------
        IDocumentTagger[] taggers = 
                loadTaggers(xml, "taggers.tagger");
        config.setTaggers(
                taggers.length == 0  ? config.getTaggers() : taggers);
        
        //--- Document Transformers --------------------------------------------
        IDocumentTransformer[] tfmrs = 
                loadTransformers(xml, "transformers.transformer");
        config.setTransformers(
                tfmrs.length == 0  ? config.getTransformers() : tfmrs);

        //--- Document Filters -------------------------------------------------
        IDocumentFilter[] filters = loadFilters(xml, "filters.filter");
        config.setFilters(
                filters.length == 0  ? config.getFilters() : filters);

        return config;
    }

    private static IDocumentTagger[] loadTaggers(
            XMLConfiguration node, String xmlPath)
            throws Exception {
        List<IDocumentTagger> list = new ArrayList<IDocumentTagger>();
        List<HierarchicalConfiguration> nodes = node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration committerNode : nodes) {
            IDocumentTagger item = (IDocumentTagger) newInstance(
                    new XMLConfiguration(committerNode), null);
            list.add(item);
            LOG.info("Tagger loaded: " + item);
        }
        return list.toArray(new IDocumentTagger[]{});
    }
    
    private static IDocumentTransformer[] loadTransformers(
            XMLConfiguration node, String xmlPath)
            throws Exception {
        List<IDocumentTransformer> list = new ArrayList<IDocumentTransformer>();
        List<HierarchicalConfiguration> nodes = node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration committerNode : nodes) {
            IDocumentTransformer item = (IDocumentTransformer) newInstance(
                    new XMLConfiguration(committerNode), null);
            list.add(item);
            LOG.info("Transformer loaded: " + item);
        }
        return list.toArray(new IDocumentTransformer[]{});
    }

    
    private static IDocumentFilter[] loadFilters(
            XMLConfiguration node, String xmlPath)
            throws Exception {
        List<IDocumentFilter> filters = new ArrayList<IDocumentFilter>();
        List<HierarchicalConfiguration> filterNodes = 
                node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration committerNode : filterNodes) {
            IDocumentFilter filter = (IDocumentFilter) newInstance(
                    new XMLConfiguration(committerNode), null);
            filters.add(filter);
            LOG.info("Import Filter loaded: " + filter);
        }
        return filters.toArray(new IDocumentFilter[]{});
    }

    private static Object newInstance(
            XMLConfiguration node, Object defaultObject)
            throws Exception {
        Object obj = null;
        if (node == null) {
            obj = defaultObject;
        } else {
            String clazz = node.getString("[@class]", null);
            if (clazz != null) {
                try {
                    obj = Class.forName(clazz).newInstance();
                } catch (Exception e) {
                    LOG.error("This class could not be instantiated: \""
                            + clazz + "\".");
                    throw e;
                }
            } else {
                LOG.warn("A configuration entry was found without class "
                       + "reference where one was needed; "
                       + "using default value:" + defaultObject);
                obj = defaultObject;
            }
        }
        if (obj != null && node != null && obj instanceof IXMLConfigurable) {
            StringWriter w = new StringWriter();
            node.save(w);
            StringReader r = new StringReader(w.toString());
            ((IXMLConfigurable) obj).loadFromXML(r);
            w.close();
            r.close();
        }
        return obj;
    }
    private static HierarchicalConfiguration configurationAt(
            HierarchicalConfiguration node, String key) {
        try {
            return node.configurationAt(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    

}
