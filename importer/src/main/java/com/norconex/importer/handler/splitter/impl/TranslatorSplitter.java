/* Copyright 2015-2022 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.importer.handler.splitter.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.language.translate.CachedTranslator;
import org.apache.tika.language.translate.MicrosoftTranslator;
import org.apache.tika.language.translate.MosesTranslator;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.language.translate.YandexTranslator;

import com.memetix.mst.language.Language;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.ImporterRuntimeException;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocInfo;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.splitter.AbstractDocumentSplitter;
import com.norconex.importer.parser.ParseState;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>Translate documents using one of the supported translation API.  The
 * following lists the supported APIs, along with the required authentication
 * properties or settings for each:</p>
 * <ul>
 *   <li><a href="http://blogs.msdn.com/b/translation/p/gettingstarted1.aspx">microsoft</a>
 *     <ul>
 *       <li>clientId</li>
 *       <li>clientSecret</li>
 *     </ul>
 *   </li>
 *   <li><a href="https://cloud.google.com/translate/">google</a>
 *     <ul>
 *       <li>apiKey</li>
 *     </ul>
 *   </li>
 *   <li><a href="http://www.lingo24.com/">lingo24</a>
 *     <ul>
 *       <li>userKey</li>
 *     </ul>
 *   </li>
 *   <li><a href="http://www.statmt.org/moses/">moses</a>
 *     <ul>
 *       <li>smtPath</li>
 *       <li>scriptPath</li>
 *     </ul>
 *   </li>
 *   <li><a href="https://tech.yandex.com/translate/">yandex</a>
 *     <ul>
 *       <li>apiKey</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>For example, the
 * Microsoft Translation API requires a client ID and a client
 * secret,
 * both obtained on Microsoft Azure Marketplace with your Microsoft account.
 * </p><p>
 * Translated documents will have the original document language stored in
 * a field "document.translatedFrom".
 * </p><p>
 * This class is not a document "splitter" per se, but like regular splitters,
 * the translation
 * will create children documents for each translation performed.  The parent
 * document will always remain the original document, while the children
 * will always be the translations.</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.splitter.impl.TranslatorSplitter"
 *     api="(microsoft|google|lingo24|moses|yandex)">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <ignoreContent>(false|true)</ignoreContent>
 *   <ignoreNonTranslatedFields>(false|true)</ignoreNonTranslatedFields>
 *   <fieldsToTranslate>(coma-separated list of fields)</fieldsToTranslate>
 *   <sourceLanguageField>(field containing language)</sourceLanguageField>
 *   <sourceLanguage>(language when no source language field)</sourceLanguage>
 *   <targetLanguages>(coma-separated list of languages)</targetLanguages>
 *
 *   <!-- Microsoft -->
 *   <clientId>...</clientId>
 *   <clientSecret>...</clientSecret>
 *
 *   <!-- Google -->
 *   <apiKey>...</apiKey>
 *
 *   <!-- Lingo24 -->
 *   <userKey>...</userKey>
 *
 *   <!-- Moses -->
 *   <smtPath>...</smtPath>
 *   <scriptPath>...</scriptPath>
 *
 *   <!-- Yandex -->
 *   <apiKey>...</apiKey>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="TranslatorSplitter" api="google">
 *     <sourceLanguageField>langField</sourceLanguageField>
 *     <targetLanguages>fr</targetLanguages>
 *     <apiKey>...MYKEYHERE...</apiKey>
 * </handler>
 * }
 *
 * <p>
 * The above example uses the Google translation API to translate documents into
 * French, taking the source document language from a field called "langField".
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@Data
public class TranslatorSplitter extends AbstractDocumentSplitter {

    public static final String API_MICROSOFT = "microsoft";
    public static final String API_GOOGLE = "google";
    public static final String API_LINGO24 = "lingo24";
    public static final String API_MOSES = "moses";
    public static final String API_YANDEX = "yandex";

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(value = AccessLevel.NONE)
    private final Map<String, TranslatorStrategy> translators = new HashMap<>();

    private String api;

    private boolean ignoreContent;

    private final List<String> fieldsToTranslate = new ArrayList<>();
    private boolean ignoreNonTranslatedFields;

    //MAYBE method: add a field with _fr suffix
    // splitter is for new docs... adding field should be in transformer?

    private String sourceLanguageField;
    private String sourceLanguage;
    private final List<String> targetLanguages = new ArrayList<>();

    // Microsoft
    private String clientId;
    private String clientSecret;

    // Google and Yandex
    private String apiKey;

    // Lingo24
    private String userKey;

    // Moses
    private String smtPath;
    private String scriptPath;

    public List<String> getFieldsToTranslate() {
        return Collections.unmodifiableList(fieldsToTranslate);
    }
    public void setFieldsToTranslate(List<String> fieldsToTranslate) {
        CollectionUtil.setAll(this.fieldsToTranslate, fieldsToTranslate);
    }

    public void setTargetLanguages(List<String> targetLanguages) {
        CollectionUtil.setAll(this.targetLanguages, targetLanguages);
    }

    /**
     * Constructor.
     */
    public TranslatorSplitter() {
        translators.put(API_MICROSOFT, new TranslatorStrategy() {
            @Override
            public Translator createTranslator() {
                var t = new MicrosoftTranslator();
                t.setId(getClientId());
                t.setSecret(getClientSecret());
                return t;
            }
            @Override
            public void validateProperties() throws ImporterHandlerException {
                if (StringUtils.isAnyBlank(getClientId(), getClientSecret())) {
                    throw new ImporterHandlerException(
                           "Both clientId and clientSecret must be specified.");
                }
            }
        });
        translators.put(API_GOOGLE, new TranslatorStrategy() {
            @Override
            public Translator createTranslator() {
                var t = new FixedGoogleTranslator();
                t.setApiKey(getApiKey());
                return t;
            }
            @Override
            public void validateProperties() throws ImporterHandlerException {
                if (StringUtils.isAnyBlank(getApiKey())) {
                    throw new ImporterHandlerException(
                           "apiKey must be specified.");
                }
            }
        });
        translators.put(API_LINGO24, new TranslatorStrategy() {
            @Override
            public Translator createTranslator() {
                var t = new FixedLingo24Translator();
                t.setUserKey(getUserKey());
                return t;
            }
            @Override
            public void validateProperties() throws ImporterHandlerException {
                if (StringUtils.isAnyBlank(getUserKey())) {
                    throw new ImporterHandlerException(
                           "userKey must be specified.");
                }
            }
        });
        translators.put(API_MOSES, new TranslatorStrategy() {
            @Override
            public Translator createTranslator() {
                return new MosesTranslator(getSmtPath(), getScriptPath());
            }
            @Override
            public void validateProperties() throws ImporterHandlerException {
                if (StringUtils.isAnyBlank(getSmtPath(), getScriptPath())) {
                    throw new ImporterHandlerException(
                           "Both smtPath and scriptPath must be specified.");
                }
            }
        });
        translators.put(API_YANDEX, new TranslatorStrategy() {
            @Override
            public Translator createTranslator() {
                var t = new YandexTranslator();
                t.setApiKey(apiKey);
                return t;
            }
            @Override
            public void validateProperties() throws ImporterHandlerException {
                if (StringUtils.isAnyBlank(getApiKey())) {
                    throw new ImporterHandlerException(
                           "apiKey must be specified.");
                }
            }
        });
    }

    @Override
    protected List<Doc> splitApplicableDocument(
            HandlerDoc doc, InputStream input, OutputStream output,
            ParseState parseState) throws ImporterHandlerException {

        // Do not re-translate a document already translated
        if (doc.getMetadata().containsKey(
                DocMetadata.TRANSLATED_FROM)) {
            return Collections.emptyList();
        }

        validateProperties(doc);

        List<Doc> translatedDocs = new ArrayList<>();

        CachedInputStream cachedInput = null;
        if (input instanceof CachedInputStream) {
            cachedInput = (CachedInputStream) input;
        } else {
            cachedInput = doc.getStreamFactory().newInputStream(input);
        }

        for (String lang : targetLanguages) {
            if (Objects.equals(sourceLanguage, lang)) {
                continue;
            }
            cachedInput.rewind();
            try (var reader = new TextReader(
                    new InputStreamReader(cachedInput, StandardCharsets.UTF_8),
                    getTranslatorStrategy().getReadSize())) {
                translatedDocs.add(
                        translateDocument(doc, doc.getStreamFactory(), lang, reader));
            } catch (Exception e) {
                var extra = "";
                if (API_GOOGLE.equals(api)
                        && e instanceof IndexOutOfBoundsException) {
                    extra = " \"apiKey\" is likely invalid.";
                }
                throw new ImporterHandlerException(
                        "Translation failed form \"" + sourceLanguage
                      + "\" to \"" + lang + "\" for: \""
                      + doc.getReference() + "\"." + extra, e);
            }
        }
        return translatedDocs;
    }



    private TranslatorStrategy getTranslatorStrategy() {
        var strategy = translators.get(api);
        if (strategy == null) {
            throw new ImporterRuntimeException(
                    "Unsupported translation api: " + api);
        }
        return strategy;
    }

    private Doc translateDocument(HandlerDoc doc,
            CachedStreamFactory streamFactory, String targetLang,
            TextReader reader) throws Exception {

        var translator = getTranslatorStrategy().getTranslator();
        var sourceLang = getResolvedSourceLanguage(doc);


        //--- Do Fields ---
        var childMeta = translateFields(
                doc, translator, sourceLang, targetLang);

        //--- Do Content ---
        CachedInputStream childInput = null;
        if (!ignoreContent) {
            var childContent = streamFactory.newOuputStream();

            String text = null;
            while ((text = reader.readText()) != null) {
                var txt = translator.translate(text, sourceLang, targetLang);
                childContent.write(txt.getBytes(StandardCharsets.UTF_8));
                childContent.flush();
            }
            try { reader.close(); } catch (IOException ie) {/*NOOP*/}
            childInput = childContent.getInputStream();
        } else {
            childInput = streamFactory.newInputStream();
        }

        //--- Build child document ---
        var childEmbedRef = "translation-" + targetLang;
        var childDocRef = doc.getReference() + "!" + childEmbedRef;

        var childInfo = new DocInfo(childDocRef);

        childMeta.set(
                DocMetadata.EMBEDDED_REFERENCE, childEmbedRef);

//        childInfo.setEmbeddedReference(childEmbedRef);
        childInfo.addEmbeddedParentReference(doc.getReference());

//        childMeta.setReference(childDocRef);
//        childMeta.setEmbeddedReference(childEmbedRef);
//        childMeta.setEmbeddedParentReference(doc.getReference());
//        childMeta.setEmbeddedParentRootReference(doc.getReference());
        childMeta.set(DocMetadata.LANGUAGE, targetLang);
        childMeta.set(DocMetadata.TRANSLATED_FROM, sourceLang);

        return new Doc(
                childDocRef, childInput, childMeta);
    }

    private Properties translateFields(
            HandlerDoc doc, Translator translator,
            String sourceLang, String targetLang) throws Exception {
        var childMeta = new Properties();
        if (ignoreNonTranslatedFields) {
            if (fieldsToTranslate.isEmpty()) {
                return childMeta;
            }
            for (String key : fieldsToTranslate) {
                var values = doc.getMetadata().get(key);
                if (values != null) {
                    childMeta.put(key, values);
                }
            }
        } else {
            childMeta.loadFromMap(doc.getMetadata());
            if (fieldsToTranslate.isEmpty()) {
                return childMeta;
            }
        }

        var b = new StringBuilder();
        for (String fld : fieldsToTranslate) {
            var values = doc.getMetadata().get(fld);
            for (String value : values) {
                b.append("[" + value.replaceAll("[\n\\[\\]]", " ") + "]");
            }
            b.append("\n");
        }
        if (b.length() == 0) {
            return childMeta;
        }

        var txt = translator.translate(b.toString(), sourceLang, targetLang);
        var lines = IOUtils.readLines(new StringReader(txt));
        var index = 0;
        for (String line : lines) {
            line = StringUtils.removeStart(line, "[");
            line = StringUtils.removeEnd(line, "]");
            var values = StringUtils.splitByWholeSeparator(line, "][");
            childMeta.set(fieldsToTranslate.get(index), values);
            index++;
        }
        return childMeta;
    }

    private void validateProperties(HandlerDoc doc)
            throws ImporterHandlerException {
        if (StringUtils.isBlank(getApi())) {
            throw new ImporterHandlerException(
                    "Must specify a translation api.");
        }
        if (targetLanguages.isEmpty()) {
            throw new ImporterHandlerException(
                    "No translation target language(s) specified.");
        }

        var sourceLang = getResolvedSourceLanguage(doc);
        if (sourceLang == null || Language.fromString(sourceLang) == null) {
            throw new ImporterHandlerException(
                    "Unsupported source language: \"" + sourceLang + "\"");
        }
        for (String targetLang : targetLanguages) {
            if (Language.fromString(targetLang) == null) {
                throw new ImporterHandlerException(
                        "Unsupported target language: \"" + targetLang + "\"");
            }
        }
        getTranslatorStrategy().validateProperties();
    }

    private String getResolvedSourceLanguage(HandlerDoc doc) {
        var lang = doc.getMetadata().getString(sourceLanguageField);
        if (StringUtils.isBlank(lang)) {
            lang = sourceLanguage;
        }
        return lang;
    }



    public static void main(String[] args) throws ImporterHandlerException {
        new TranslatorSplitter().splitApplicableDocument(
                null, null, null, ParseState.PRE);
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setApi(xml.getString("@api", api));
        setIgnoreContent(xml.getBoolean("ignoreContent", ignoreContent));
        setIgnoreNonTranslatedFields(xml.getBoolean(
                "ignoreNonTranslatedFields", ignoreNonTranslatedFields));
        setFieldsToTranslate(xml.getDelimitedStringList(
                "fieldsToTranslate", getFieldsToTranslate()));
        setSourceLanguageField(xml.getString(
                "sourceLanguageField", sourceLanguageField));
        setSourceLanguage(xml.getString("sourceLanguage", sourceLanguage));

        setTargetLanguages(xml.getDelimitedStringList(
                "targetLanguages", getTargetLanguages()));

        setClientId(xml.getString("clientId", clientId));
        setClientSecret(xml.getString("clientSecret", clientSecret));
        setApiKey(xml.getString("apiKey", apiKey));
        setUserKey(xml.getString("userKey", userKey));
        setSmtPath(xml.getString("smtPath", smtPath));
        setScriptPath(xml.getString("scriptPath", scriptPath));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("api", api);
        xml.addElement("ignoreContent", ignoreContent);
        xml.addElement("ignoreNonTranslatedFields", ignoreNonTranslatedFields);
        xml.addDelimitedElementList("fieldsToTranslate", fieldsToTranslate);
        xml.addElement("sourceLanguageField", sourceLanguageField);
        xml.addElement("sourceLanguage", sourceLanguage);
        xml.addDelimitedElementList("targetLanguages", targetLanguages);
        xml.addElement("clientId", clientId);
        xml.addElement("clientSecret", clientSecret);
        xml.addElement("apiKey", apiKey);
        xml.addElement("userKey", userKey);
        xml.addElement("smtPath", smtPath);
        xml.addElement("scriptPath", scriptPath);
    }

    private abstract static class TranslatorStrategy {
        private static final int DEFAULT_READ_SIZE =
                DataUnit.KB.toBytes(2).intValue();
        private Translator translator;
        public int getReadSize() {
            return DEFAULT_READ_SIZE;
        }
        public final Translator getTranslator() {
            if (translator == null) {
                translator = new CachedTranslator(createTranslator());
            }
            return translator;
        }
        protected abstract Translator createTranslator();
        public abstract void validateProperties()
                throws ImporterHandlerException;
    }
}
