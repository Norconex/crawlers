/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.importer.handler.parser.impl;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.response.ImporterResponse;

import lombok.Data;

/**
 * {@nx.block #doc
 * <p>
 * For documents containing embedded documents (e.g. zip files), the default
 * behavior of this treat them as a single document, merging all
 * embedded documents content and metadata into the parent document.
 * You can tell this parser to "split" embedded
 * documents to have them treated as if they were individual documents.  When
 * split, each embedded documents will go through the entire import cycle,
 * going through your handlers and even this parser again
 * (just like any regular document would).
 * </p>
 * <p>
 * You can also decide to skip the parsing of embedded documents. Either
 * by the content type of the parent document, or the content types of the
 * embedded documents.
 * </p>
 * <p>
 * When embedded are being parsed, the resulting
 * {@link ImporterResponse} will contain nested documents, which in turn,
 * might contain some (tree-like structure).
 * </p>
 * }
 *
 * {@nx.xml.usage
 * <embedded>
 *     <splitEmbeddedOf>
 *       <!-- "matcher" is repeatable -->
 *       <matcher>
 *         (content type matcher of files to split, having their
 *          embedded documents extracted and treated as individual
 *          documents instead)
 *       </matcher>
 *     </splitEmbeddedOf>
 *     <skipEmmbbededOf>
 *       <matcher>
 *         (content type matcher of files you do not want to have their
 *          embedded files parsed)
 *       </matcher>
 *     </skipEmmbbededOf>
 *     <skipEmmbbeded>
 *       <matcher>
 *         (content type matcher of embedded files you do not want parsed)
 *       </matcher>
 *     </skipEmmbbeded>
 * </embedded>
 * }
 *
 * {@nx.xml.example
 * <embedded>
 *   <splitEmbeddedOf>
 *     <matcher>application/zip</matcher>
 *   </splitEmbeddedOf>
 * </embedded>
 * }
 *
 * <p>
 * The above example will treat all documents contained with a Zip file
 * as individual documents, each to be processed separately.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
public class EmbeddedConfig { //{


    //TODO change to:

    // Parent files to split
    private final TextMatcher containerTypeMatcher = new TextMatcher();
    // Child files to parse... unparsed ones are skipped (not read, not split).
    private final TextMatcher embeddedTypeMatcher = new TextMatcher();

    // above are white list items. They can use negate to reverse.

    //
//    private final TextMatcher parentToSplitTypeMatcher = new TextMatcher();
//    private final TextMatcher childToSkipTypeMatcher = new TextMatcher();


    // parentToSplit
    // childToSkip

//    private final List<TextMatcher> splitEmbeddedOf = new ArrayList<>();
//    private final List<TextMatcher> skipEmmbbededOf = new ArrayList<>();
//    private final List<TextMatcher> skipEmmbbeded = new ArrayList<>();

//    @JsonIgnore
//    public boolean isEmpty() {
//        return splitEmbeddedOf.isEmpty()
//                && skipEmmbbededOf.isEmpty()
//                && skipEmmbbeded.isEmpty();
//    }
//
//    /**
//     * Content type matchers of files to split, having their
//     * embedded documents extracted and treated as individual
//     * documents instead.
//     * @return content type matchers
//     */
//    public List<TextMatcher> getSplitEmbeddedOf() {
//        return Collections.unmodifiableList(splitEmbeddedOf);
//    }
//    /**
//     * Content type matchers of files to split, having their
//     * embedded documents extracted and treated as individual
//     * documents instead.
//     * @param matchers content type matchers
//     */
//    public void setSplitEmbeddedOf(List<TextMatcher> matchers) {
//        CollectionUtil.setAll(splitEmbeddedOf, matchers);
//    }
//
//    /**
//     * Content type matchers of files you do not want to have their
//     * embedded files parsed.
//     * @return content type matchers
//     */
//    public List<TextMatcher> getSkipEmmbbededOf() {
//        return Collections.unmodifiableList(skipEmmbbededOf);
//    }
//    /**
//     * Content type matchers of files you do not want to have their
//     * embedded files parsed.
//     * @param matchers content type matchers
//     */
//    public void setSkipEmmbbededOf(List<TextMatcher> matchers) {
//        CollectionUtil.setAll(skipEmmbbededOf, matchers);
//    }
//
//    /**
//     * Content type matchers of embedded files you do not want parsed.
//     * @return content type matchers
//     */
//    public List<TextMatcher> getSkipEmmbbeded() {
//        return Collections.unmodifiableList(skipEmmbbeded);
//    }
//    /**
//     * Content type matchers of embedded files you do not want parsed.
//     * @param matchers content type matchers
//     */
//    public void setSkipEmmbbeded(List<TextMatcher> matchers) {
//        CollectionUtil.setAll(skipEmmbbeded, matchers);
//    }
//
//    @Override
//    public void loadFromXML(XML xml) {
//        setSplitEmbeddedOf(
//                toMatchers(xml.getXMLList("splitEmbeddedOf/matcher")));
//        setSkipEmmbbededOf(
//                toMatchers(xml.getXMLList("skipEmmbbededOf/matcher")));
//        setSkipEmmbbeded(toMatchers(xml.getXMLList("skipEmmbbeded/matcher")));
//    }
//
//    @Override
//    public void saveToXML(XML xml) {
//        var matcherEl = "matcher";
//        xml.addElementList("splitEmbeddedOf", matcherEl, splitEmbeddedOf);
//        xml.addElementList("skipEmmbbededOf", matcherEl, skipEmmbbededOf);
//        xml.addElementList("skipEmmbbeded", matcherEl, skipEmmbbeded);
//    }
//
//    private List<TextMatcher> toMatchers(List<XML> xmls) {
//        return xmls.stream()
//            .map(x -> {
//                var tm = new TextMatcher();
//                tm.loadFromXML(x);
//                return tm;
//            })
//            .toList();
//    }
}
