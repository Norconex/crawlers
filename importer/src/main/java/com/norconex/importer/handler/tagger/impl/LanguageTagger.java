/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.importer.handler.tagger.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractStringTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Detects a document language based on Apache Tika language detection
 * capability.
 * It adds the detected language to the
 * "<code>document.language</code>" metadata field.
 * Optionally adds all potential languages detected with their
 * probability score as well as additional fields following this pattern:</p>
 * <pre>
 * document.language.&lt;rank&gt;.tag
 * document.language.&lt;rank&gt;.probability</pre>
 * <p>
 * <code>&lt;rank&gt;</code> is to indicate the match order, based
 * on match probability score (starting at 1).
 * </p>
 *
 * <p>This tagger can be used both as a pre-parse (on text only)
 * or post-parse handler.</p>
 *
 * <h3>Accuracy:</h3>
 * <p>
 * To obtain optimal detection, long enough text is expected.  The default
 * detection algorithm is optimized for document with lots of text.
 * This tagger relies on Tika language detection capabilities and future
 * versions may provide better precision for documents made of short
 * text (e.g. tweets, comments, etc).
 * </p>
 * <p>
 * If you know what mix of languages are used by your site(s), you can increase
 * accuracy in many cases by limiting the set of languages supported
 * for detection.
 * </p>
 *
 * <h3>Supported Languages:</h3>
 * <p>
 * Languages are represented as code values. As of 2.6.0, at least the
 * following 70 languages are supported by the Tika version used:
 * </p>
 *
 * <ul>
 *   <li>af Afrikaans</li>
 *   <li>an Aragonese</li>
 *   <li>ar Arabic</li>
 *   <li>ast Asturian</li>
 *   <li>be Belarusian</li>
 *   <li>br Breton</li>
 *   <li>ca Catalan</li>
 *   <li>bg Bulgarian</li>
 *   <li>bn Bengali</li>
 *   <li>cs Czech</li>
 *   <li>cy Welsh</li>
 *   <li>da Danish</li>
 *   <li>de German</li>
 *   <li>el Greek</li>
 *   <li>en English</li>
 *   <li>es Spanish</li>
 *   <li>et Estonian</li>
 *   <li>eu Basque</li>
 *   <li>fa Persian</li>
 *   <li>fi Finnish</li>
 *   <li>fr French</li>
 *   <li>ga Irish</li>
 *   <li>gl Galician</li>
 *   <li>gu Gujarati</li>
 *   <li>he Hebrew</li>
 *   <li>hi Hindi</li>
 *   <li>hr Croatian</li>
 *   <li>ht Haitian</li>
 *   <li>hu Hungarian</li>
 *   <li>id Indonesian</li>
 *   <li>is Icelandic</li>
 *   <li>it Italian</li>
 *   <li>ja Japanese</li>
 *   <li>km Khmer</li>
 *   <li>kn Kannada</li>
 *   <li>ko Korean</li>
 *   <li>lt Lithuanian</li>
 *   <li>lv Latvian</li>
 *   <li>mk Macedonian</li>
 *   <li>ml Malayalam</li>
 *   <li>mr Marathi</li>
 *   <li>ms Malay</li>
 *   <li>mt Maltese</li>
 *   <li>ne Nepali</li>
 *   <li>nl Dutch</li>
 *   <li>no Norwegian</li>
 *   <li>oc Occitan</li>
 *   <li>pa Punjabi</li>
 *   <li>pl Polish</li>
 *   <li>pt Portuguese</li>
 *   <li>ro Romanian</li>
 *   <li>ru Russian</li>
 *   <li>sk Slovak</li>
 *   <li>sl Slovene</li>
 *   <li>so Somali</li>
 *   <li>sq Albanian</li>
 *   <li>sr Serbian</li>
 *   <li>sv Swedish</li>
 *   <li>sw Swahili</li>
 *   <li>ta Tamil</li>
 *   <li>te Telugu</li>
 *   <li>th Thai</li>
 *   <li>tl Tagalog</li>
 *   <li>tr Turkish</li>
 *   <li>uk Ukrainian</li>
 *   <li>ur Urdu</li>
 *   <li>vi Vietnamese</li>
 *   <li>yi Yiddish</li>
 *   <li>zh-cn Simplified Chinese</li>
 *   <li>zh-tw Traditional Chinese</li>
 * </ul>
 *
 * <p>
 * It is possible more will be supported automatically with future Tika
 * upgrades.
 * </p>
 *
 * <p>If you do not restrict the list of language candidates to detect,
 * the default behavior is to try match all languages currently supported.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.LanguageTagger"
 *     keepProbabilities="(false|true)"
 *     toField="(custom target field to store the language)"
 *     fallbackLanguage="(default language when detection failed)"
 *     {@nx.include com.norconex.importer.handler.tagger.AbstractStringTagger#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <languages>
 *     (CSV list of language tag candidates. Defaults to the above list.)
 *   </languages>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="LanguageTagger" fallbackLanguage="en" >
 *   <languages>en, fr</languages>
 * </handler>
 * }
 * <p>
 * The above example detects whether pages are English or French, falling back
 * to English if detection failed.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@Slf4j
public class LanguageTagger extends AbstractStringTagger
        implements XMLConfigurable {

    //TODO Check if doc.size is defined in metadata? If so, use it to
    //determine if we are going with small or long text?

    //TODO provide ways to overwrite or specify custom language profiles
    // in this tagger configuration?

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private LanguageDetector detector;
    private boolean keepProbabilities;
    private final List<String> languages = new ArrayList<>();
    private String fallbackLanguage;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final Comparator<LanguageResult> langResultComparator =
            Comparator.comparing(LanguageResult::getRawScore).reversed();

    @Override
    protected void tagStringContent(HandlerDoc doc, StringBuilder content,
            ParseState parseState, int sectionIndex)
                    throws ImporterHandlerException {

        // For massive docs: only use first section of document to detect langs
        if (sectionIndex > 0) {
            return;
        }

        ensureDetectorInitialization();

        var results = detector.detectAll(content.toString());

        // leave now if no matches
        if (results.isEmpty()) {
            LOG.debug("No language found, using fallback language for {}.",
                    doc.getReference());
            doc.getMetadata().set(DocMetadata.LANGUAGE, fallbackLanguage);
//            metadata.setLanguage(fallbackLanguage);
            return;
        }

        Collections.sort(results, langResultComparator);
        doc.getMetadata().set(
                DocMetadata.LANGUAGE, results.get(0).getLanguage());
//        metadata.setLanguage(results.get(0).getLanguage());

        if (keepProbabilities) {
            var count = 0;
            for (LanguageResult lang : results) {
                count++;
                var prefix = DocMetadata.LANGUAGE + "." + count;
                doc.getMetadata().set(prefix + ".tag", lang.getLanguage());
                doc.getMetadata().set(
                        prefix + ".probability", lang.getRawScore());
            }
        }
    }

    public boolean isKeepProbabilities() {
        return keepProbabilities;
    }
    /**
     * Sets whether to keep the match probabilities for each languages
     * detected.  Default is <code>false</code>.
     * @param keepProbabilities <code>true</code> to keep probabilities
     */
    public void setKeepProbabilities(boolean keepProbabilities) {
        ensureNotInitialized();
        this.keepProbabilities = keepProbabilities;
    }

    public String getFallbackLanguage() {
        return fallbackLanguage;
    }
    /**
     * Sets the fallback language when none are detected.  Default behavior
     * is to not tag incoming documents with a language field when no detection
     * occurs.
     * @param fallbackLanguage the default languages when no detection
     */
    public void setFallbackLanguage(String fallbackLanguage) {
        ensureNotInitialized();
        this.fallbackLanguage = fallbackLanguage;
    }

    private synchronized void ensureDetectorInitialization()
            throws ImporterHandlerException {
        if (detector == null) {
            var d = new OptimaizeLangDetector();
            try {
                if (languages.isEmpty()) {
                    d.loadModels();
                } else {
                    d.loadModels(new HashSet<>(languages));
                }
            } catch (IOException e) {
                throw new ImporterHandlerException(
                        "Cannot initialize language detector.", e);
            }
            detector = d;
        }
    }

    public List<String> getLanguages() {
        return Collections.unmodifiableList(languages);
    }
    /**
     * Sets the language candidates for the language detection.
     * @param languages languages to consider for detection
     */
    public void setLanguages(List<String> languages) {
        ensureNotInitialized();
        CollectionUtil.setAll(this.languages, languages);
    }

    private void ensureNotInitialized() {
        if (detector != null) {
            throw new IllegalStateException(
                    "You cannot set LanguageTagger properties after it "
                  + "has been initialized (started tagging documents).");
        }
    }

    @Override
    protected void loadStringTaggerFromXML(XML xml) {
        setKeepProbabilities(xml.getBoolean(
                "@keepProbabilities", keepProbabilities));
        setFallbackLanguage(xml.getString(
                "@fallbackLanguage", fallbackLanguage));
        setLanguages(xml.getDelimitedStringList("languages", languages));
    }

    @Override
    protected void saveStringTaggerToXML(XML xml) {
        xml.setAttribute("keepProbabilities", keepProbabilities);
        xml.setAttribute("fallbackLanguage", fallbackLanguage);
        xml.addDelimitedElementList("languages", languages);
    }
}
