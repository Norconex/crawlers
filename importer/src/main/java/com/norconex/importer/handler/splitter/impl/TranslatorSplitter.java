/* Copyright 2015-2024 Norconex Inc.
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
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.language.translate.impl.CachedTranslator;
import org.apache.tika.language.translate.impl.MicrosoftTranslator;
import org.apache.tika.language.translate.impl.MosesTranslator;
import org.apache.tika.language.translate.impl.YandexTranslator;

import com.memetix.mst.language.Language;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.importer.ImporterRuntimeException;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocContext;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.DocumentHandlerException;
import com.norconex.importer.handler.HandlerContext;
import com.norconex.importer.handler.splitter.AbstractDocumentSplitter;

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
 */
@SuppressWarnings("javadoc")
@Data
public class TranslatorSplitter
        extends AbstractDocumentSplitter<TranslatorSplitterConfig> {

    private final TranslatorSplitterConfig configuration =
            new TranslatorSplitterConfig();

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(value = AccessLevel.NONE)
    private final Map<String, TranslatorStrategy> translators = new HashMap<>();

    /**
     * Constructor.
     */
    public TranslatorSplitter() {
        translators.put(
                TranslatorSplitterConfig.API_MICROSOFT,
                new TranslatorStrategy() {
                    @Override
                    public Translator createTranslator() {
                        var t = new MicrosoftTranslator();
                        t.setId(configuration.getClientId());
                        t.setSecret(configuration.getClientSecret());
                        return t;
                    }

                    @Override
                    public void validateProperties()
                            throws DocumentHandlerException {
                        if (StringUtils.isAnyBlank(
                                configuration.getClientId(),
                                configuration.getClientSecret())) {
                            throw new DocumentHandlerException(
                                    "Both clientId and clientSecret must be specified.");
                        }
                    }
                });
        translators.put(
                TranslatorSplitterConfig.API_GOOGLE,
                new TranslatorStrategy() {
                    @Override
                    public Translator createTranslator() {
                        var t = new FixedGoogleTranslator();
                        t.setApiKey(configuration.getApiKey());
                        return t;
                    }

                    @Override
                    public void validateProperties()
                            throws DocumentHandlerException {
                        if (StringUtils.isAnyBlank(configuration.getApiKey())) {
                            throw new DocumentHandlerException(
                                    "apiKey must be specified.");
                        }
                    }
                });
        translators.put(
                TranslatorSplitterConfig.API_LINGO24,
                new TranslatorStrategy() {
                    @Override
                    public Translator createTranslator() {
                        var t = new FixedLingo24Translator();
                        t.setUserKey(configuration.getUserKey());
                        return t;
                    }

                    @Override
                    public void validateProperties()
                            throws DocumentHandlerException {
                        if (StringUtils
                                .isAnyBlank(configuration.getUserKey())) {
                            throw new DocumentHandlerException(
                                    "userKey must be specified.");
                        }
                    }
                });
        translators.put(
                TranslatorSplitterConfig.API_MOSES,
                new TranslatorStrategy() {
                    @Override
                    public Translator createTranslator() {
                        return new MosesTranslator(
                                configuration.getSmtPath(),
                                configuration.getScriptPath());
                    }

                    @Override
                    public void validateProperties()
                            throws DocumentHandlerException {
                        if (StringUtils.isAnyBlank(
                                configuration.getSmtPath(),
                                configuration.getScriptPath())) {
                            throw new DocumentHandlerException(
                                    "Both smtPath and scriptPath must be specified.");
                        }
                    }
                });
        translators.put(
                TranslatorSplitterConfig.API_YANDEX,
                new TranslatorStrategy() {
                    @Override
                    public Translator createTranslator() {
                        var t = new YandexTranslator();
                        t.setApiKey(configuration.getApiKey());
                        return t;
                    }

                    @Override
                    public void validateProperties()
                            throws DocumentHandlerException {
                        if (StringUtils.isAnyBlank(configuration.getApiKey())) {
                            throw new DocumentHandlerException(
                                    "apiKey must be specified.");
                        }
                    }
                });
    }

    @Override
    public void split(HandlerContext docCtx) throws DocumentHandlerException {

        // Do not re-translate a document already translated
        if (docCtx.metadata().containsKey(DocMetadata.TRANSLATED_FROM)) {
            return;
        }

        validateProperties(docCtx);

        if (configuration.getFieldMatcher().isSet()) {
            // Fields
            try {
                for (String lang : configuration.getTargetLanguages()) {
                    translateFields(docCtx, lang);
                }
            } catch (DocumentHandlerException e) {
                throw e;
            } catch (Exception e) {
                throw new DocumentHandlerException(
                        "Could not translate document: "
                                + docCtx.reference(),
                        e);
            }
        } else {
            // Body
            var translatedDocs = docCtx.childDocs();
            try (var input = docCtx.input().asInputStream();
                    var cachedInput = input instanceof CachedInputStream cis
                            ? cis
                            : docCtx.streamFactory().newInputStream(input)) {
                for (String lang : configuration.getTargetLanguages()) {
                    var translatedDoc = translateDocumentFromStream(
                            docCtx, cachedInput, lang);
                    if (translatedDoc != null) {
                        translatedDocs.add(translatedDoc);
                    }
                }
            } catch (DocumentHandlerException e) {
                throw e;
            } catch (Exception e) {
                throw new DocumentHandlerException(
                        "Could not translate document: "
                                + docCtx.reference(),
                        e);
            }
        }
    }

    private Doc translateDocumentFromStream(
            HandlerContext docCtx,
            CachedInputStream cachedInput,
            String targetLang)
            throws DocumentHandlerException {
        if (Objects.equals(configuration.getSourceLanguage(), targetLang)) {
            return null;
        }
        cachedInput.rewind();
        try (var reader = new TextReader(
                new InputStreamReader(cachedInput, StandardCharsets.UTF_8),
                getTranslatorStrategy().getReadSize())) {
            return translateDocumentFromReader(docCtx, targetLang, reader);
        } catch (Exception e) {
            var extra = "";
            if (TranslatorSplitterConfig.API_GOOGLE.equals(
                    configuration.getApi())
                    && e instanceof IndexOutOfBoundsException) {
                extra = " \"apiKey\" is likely invalid.";
            }
            throw new DocumentHandlerException(
                    "Translation failed form \"%s\" to \"%s\" for: \"%s\". %s"
                            .formatted(
                                    configuration.getSourceLanguage(),
                                    targetLang,
                                    docCtx.reference(),
                                    extra),
                    e);
        }
    }

    private Doc translateDocumentFromReader(
            HandlerContext docCtx, String targetLang, TextReader reader)
            throws Exception {

        var translator = getTranslatorStrategy().getTranslator();
        var sourceLang = getResolvedSourceLanguage(docCtx);

        var childMeta = new Properties();
        CachedInputStream childInput = null;

        //--- Do Content ---
        try (var childContent = docCtx.streamFactory().newOuputStream()) {
            String text = null;
            while ((text = reader.readText()) != null) {
                var txt = translator.translate(
                        text, sourceLang, targetLang);
                childContent.write(txt.getBytes(StandardCharsets.UTF_8));
                childContent.flush();
            }
            try {
                reader.close();
            } catch (IOException ie) {
                /*NOOP*/}
            childInput = childContent.getInputStream();
        }

        //--- Build child document ---
        var childEmbedRef = "translation-" + targetLang;
        var childDocRef = docCtx.reference() + "!" + childEmbedRef;

        var childInfo = new DocContext(childDocRef);

        childMeta.set(DocMetadata.EMBEDDED_REFERENCE, childEmbedRef);

        childInfo.addEmbeddedParentReference(docCtx.reference());

        childMeta.set(DocMetadata.LANGUAGE, targetLang);
        childMeta.set(DocMetadata.TRANSLATED_FROM, sourceLang);

        return new Doc(childDocRef, childInput, childMeta);
    }

    private Properties translateFields(
            HandlerContext docCtx, String targetLang) throws Exception {

        var translator = getTranslatorStrategy().getTranslator();
        var sourceLang = getResolvedSourceLanguage(docCtx);

        var childMeta = new Properties();

        if (!configuration.isIgnoreNonTranslatedFields()) {
            childMeta.loadFromMap(docCtx.metadata());
        }

        var fieldMatcher = configuration.getFieldMatcher();
        if (StringUtils.isBlank(fieldMatcher.getPattern())) {
            return childMeta;
        }
        var fieldsToTranslate =
                List.copyOf(docCtx.metadata().matchKeys(fieldMatcher).keySet());
        if (fieldsToTranslate.isEmpty()) {
            return childMeta;
        }

        var b = new StringBuilder();

        for (String fld : fieldsToTranslate) {
            var values = docCtx.metadata().get(fld);
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

    private TranslatorStrategy getTranslatorStrategy() {
        var strategy = translators.get(configuration.getApi());
        if (strategy == null) {
            throw new ImporterRuntimeException(
                    "Unsupported translation api: " + configuration.getApi());
        }
        return strategy;
    }

    private void validateProperties(HandlerContext doc)
            throws DocumentHandlerException {
        if (StringUtils.isBlank(configuration.getApi())) {
            throw new DocumentHandlerException(
                    "Must specify a translation api.");
        }
        if (configuration.getTargetLanguages().isEmpty()) {
            throw new DocumentHandlerException(
                    "No translation target language(s) specified.");
        }

        var sourceLang = getResolvedSourceLanguage(doc);
        if (sourceLang == null || Language.fromString(sourceLang) == null) {
            throw new DocumentHandlerException(
                    "Unsupported source language: \"" + sourceLang + "\"");
        }
        for (String targetLang : configuration.getTargetLanguages()) {
            if (Language.fromString(targetLang) == null) {
                throw new DocumentHandlerException(
                        "Unsupported target language: \"" + targetLang + "\"");
            }
        }
        getTranslatorStrategy().validateProperties();
    }

    private String getResolvedSourceLanguage(HandlerContext docCtx) {
        var lang = docCtx.metadata().getString(
                configuration.getSourceLanguageField());
        if (StringUtils.isBlank(lang)) {
            lang = configuration.getSourceLanguage();
        }
        return lang;
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
                throws DocumentHandlerException;
    }
}
