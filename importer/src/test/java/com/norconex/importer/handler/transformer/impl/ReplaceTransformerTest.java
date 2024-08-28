/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.importer.handler.transformer.impl;

import static com.norconex.commons.lang.text.TextMatcher.basic;
import static com.norconex.commons.lang.text.TextMatcher.regex;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.parser.ParseState;

class ReplaceTransformerTest {

    private final String restrictionTestConfig = """
            <handler>
              <operations>
                <op>
                  <valueMatcher ignoreCase="true" partial="true">
                    <pattern>CAKES</pattern>
                  </valueMatcher>
                  <toValue>FRUITS</toValue>
                </op>
                <op>
                  <valueMatcher ignoreCase="true" partial="true">
                    <pattern>candies</pattern>
                  </valueMatcher>
                  <toValue>vegetables</toValue>
                </op>
              </operations>
            </handler>""";

    @Test
    void testReplaceEolWithWhiteSpace()
            throws IOException, IOException {
        var input = "line1\r\nline2\rline3\nline4";
        var expectedOutput = "line1 line2 line3 line4";

        var preserveTestConfig = """
                <handler>
                  <operations>
                    <op>
                      <valueMatcher method="regex" ignoreCase="true"
                          partial="true" replaceAll="true">
                        <pattern>[\\r\\n]+</pattern>
                      </valueMatcher>
                      <toValue> </toValue>
                    </op>
                  </operations>
                </handler>""";
        var response1 = transformTextDocument(
                preserveTestConfig, "N/A", input
        );
        Assertions.assertEquals(expectedOutput, response1);
    }

    private String transformTextDocument(
            String config, String reference, String content
    )
            throws IOException, IOException {

        var t = new ReplaceTransformer();

        try (Reader reader = new InputStreamReader(
                IOUtils.toInputStream(config, StandardCharsets.UTF_8)
        )) {
            BeanMapper.DEFAULT.read(t, reader, Format.XML);
        }

        try (var is = IOUtils.toInputStream(content, StandardCharsets.UTF_8);
                var os = new ByteArrayOutputStream();) {
            var metadata = new Properties();
            metadata.set("document.reference", reference);
            var doc = TestUtil.newHandlerContext(
                    reference, is, metadata, ParseState.POST
            );
            t.accept(doc);
            return doc.input().asString();
        }
    }

    @Test
    void testWriteRead() throws IOException {
        var t = new ReplaceTransformer();
        t.getConfiguration().setMaxReadSize(128);
        try (Reader reader = new InputStreamReader(
                IOUtils.toInputStream(
                        restrictionTestConfig, StandardCharsets.UTF_8
                )
        )) {
            BeanMapper.DEFAULT.read(t, reader, Format.XML);
        }
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }

    //--- From tagger ---

    // Test for: https://github.com/Norconex/collector-http/issues/416
    @Test
    void testNoValue()
            throws IOException {
        var meta = new Properties();
        meta.add("test", "a b c");

        var t = new ReplaceTransformer();
        t.getConfiguration().setOperations(
                List.of(

                        // regex
                        new ReplaceOperation()
                                .setFieldMatcher(basic("test"))
                                .setToField("regex")
                                .setValueMatcher(
                                        regex("\\s+b\\s+").setPartial(true)
                                )
                                .setToValue("")
                                .setDiscardUnchanged(true)
                )
        );
        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        // normal
        t.getConfiguration().setOperations(
                List.of(
                        new ReplaceOperation()
                                .setFieldMatcher(basic("test"))
                                .setToField("normal")
                                .setValueMatcher(basic("b").setPartial(true))
                                .setToValue("")
                                .setDiscardUnchanged(true)
                )
        );
        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("ac", meta.getString("regex"));
        Assertions.assertEquals("a  c", meta.getString("normal"));

        // XML
        var xml =
                """
                        <t>
                          <operations>
                            <op toField="regexXML">
                              <fieldMatcher method="regex">
                                <pattern>test</pattern>
                              </fieldMatcher>
                              <valueMatcher method="regex" replaceAll="true" partial="true">
                                <pattern>(.{0,0})\\s+b\\s+</pattern>
                              </valueMatcher>
                            </op>
                            <op toField="normalXML">
                              <fieldMatcher><pattern>test</pattern></fieldMatcher>
                              <valueMatcher partial="true">
                                <pattern>b</pattern>
                              </valueMatcher>
                            </op>
                          </operations>
                        </t>""";
        t = new ReplaceTransformer();
        try (var reader = new StringReader(xml)) {
            BeanMapper.DEFAULT.read(t, reader, Format.XML);
        }

        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("ac", meta.getString("regexXML"));
        Assertions.assertEquals("a  c", meta.getString("normalXML"));
    }

    //This is a test for https://github.com/Norconex/importer/issues/29
    //where the replaced value is equal to the original (EXP_NAME1), it should
    //still store it (was a bug).
    @Test
    void testMatchReturnSameValue()
            throws IOException {
        var meta = new Properties();
        meta.add("EXP_NAME+COUNTRY1", "LAZARUS ANDREW");
        meta.add("EXP_NAME+COUNTRY2", "LAZARUS ANDREW [US]");

        var t = new ReplaceTransformer();

        t.getConfiguration().setOperations(
                List.of(
                        // Author 1
                        new ReplaceOperation()
                                .setFieldMatcher(basic("EXP_NAME+COUNTRY1"))
                                .setToField("EXP_NAME1")
                                .setValueMatcher(
                                        regex("^(.+?)(.?\\[([A-Z]+)\\])?$")
                                )
                                .setToValue("$1")
                                .setDiscardUnchanged(false),

                        new ReplaceOperation()
                                .setFieldMatcher(basic("EXP_NAME+COUNTRY1"))
                                .setToField("EXP_COUNTRY1")
                                .setValueMatcher(
                                        regex("^(.+?)(.?\\[([A-Z]+)\\])?$")
                                )
                                .setToValue("$3")
                                .setDiscardUnchanged(false),

                        // Author 2
                        new ReplaceOperation()
                                .setFieldMatcher(basic("EXP_NAME+COUNTRY2"))
                                .setToField("EXP_NAME2")
                                .setValueMatcher(
                                        regex("^(.+?)(.?\\[([A-Z]+)\\])?$")
                                )
                                .setToValue("$1")
                                .setDiscardUnchanged(false),

                        new ReplaceOperation()
                                .setFieldMatcher(basic("EXP_NAME+COUNTRY2"))
                                .setToField("EXP_COUNTRY2")
                                .setValueMatcher(
                                        regex("^(.+?)(.?\\[([A-Z]+)\\])?$")
                                )
                                .setToValue("$3")
                                .setDiscardUnchanged(false)
                )
        );

        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("LAZARUS ANDREW", meta.getString("EXP_NAME1"));
        Assertions.assertEquals("", meta.getString("EXP_COUNTRY1"));
        Assertions.assertEquals("LAZARUS ANDREW", meta.getString("EXP_NAME2"));
        Assertions.assertEquals("US", meta.getString("EXP_COUNTRY2"));
    }

    @Test
    void testWriteReadDetailed() {
        var t = new ReplaceTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new ReplaceOperation()
                                .setValueMatcher(regex("fromValue1"))
                                .setToValue("toValue1")
                                .setFieldMatcher(basic("fromName1"))
                                .setOnSet(PropertySetter.REPLACE)
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(regex("fromValue2"))
                                .setToValue("toValue2")
                                .setFieldMatcher(basic("fromName1"))
                                .setOnSet(PropertySetter.PREPEND)
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("fromValue1").setIgnoreCase(true)
                                )
                                .setToValue("toValue1")
                                .setFieldMatcher(basic("fromName2"))
                                .setToField("toName2")
                                .setOnSet(PropertySetter.OPTIONAL)
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("fromValue3").setIgnoreCase(true)
                                )
                                .setToValue("toValue3")
                                .setFieldMatcher(basic("fromName3"))
                                .setToField("toName3")
                                .setDiscardUnchanged(true)
                )
        );
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }

    @Test
    void testRegularReplace() throws IOException {
        var meta = new Properties();
        meta.add("fullMatchField", "full value match");
        meta.add("partialNoMatchField", "partial value nomatch");
        meta.add("matchOldField", "match to new field");
        meta.add("nomatchOldField", "no match to new field");
        meta.add("caseField", "Value Of Mixed Case");

        var t = new ReplaceTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new ReplaceOperation()
                                .setValueMatcher(basic("full value match"))
                                .setToValue("replaced")
                                .setFieldMatcher(basic("fullMatchField"))
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(basic("bad if you see me"))
                                .setToValue("not replaced")
                                .setFieldMatcher(basic("partialNoMatchField"))
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(basic("match to new field"))
                                .setToValue("replaced to new field")
                                .setFieldMatcher(basic("matchOldField"))
                                .setToField("matchNewField")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(basic("bad if you see me"))
                                .setToValue("not replaced")
                                .setFieldMatcher(basic("nomatchOldField"))
                                .setToField("nomatchNewField")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("value Of mixed case")
                                                .setIgnoreCase(true)
                                )
                                .setToValue("REPLACED")
                                .setFieldMatcher(basic("caseField"))
                                .setDiscardUnchanged(true)
                )
        );

        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("replaced", meta.getString("fullMatchField"));
        Assertions.assertNull(meta.getString("partialNoMatchField"));
        Assertions.assertEquals(
                "replaced to new field",
                meta.getString("matchNewField")
        );
        Assertions.assertEquals(
                "no match to new field",
                meta.getString("nomatchOldField")
        );
        Assertions.assertNull(meta.getString("nomatchNewField"));
        Assertions.assertEquals("REPLACED", meta.getString("caseField"));
    }

    @Test
    void testRegexReplace() throws IOException {
        var meta = new Properties();
        meta.add("path1", "/this/is/a/path/file.doc");
        meta.add("path2", "/that/is/a/path/file.doc");
        meta.add("path3", "/That/Is/A/Path/File.doc");

        var t = new ReplaceTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("(.*)/.*").setPartial(true)
                                )
                                .setToValue("$1")
                                .setFieldMatcher(basic("path1"))
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("(.*)/.*").setPartial(true)
                                )
                                .setToValue("$1")
                                .setFieldMatcher(basic("path2"))
                                .setToField("folder")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("file")
                                                .setPartial(true)
                                                .setIgnoreCase(true)
                                )
                                .setToValue("something")
                                .setFieldMatcher(basic("path3"))
                                .setDiscardUnchanged(true)
                )
        );

        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("/this/is/a/path", meta.getString("path1"));
        Assertions.assertEquals("/that/is/a/path", meta.getString("folder"));
        Assertions.assertEquals(
                "/that/is/a/path/file.doc", meta.getString("path2")
        );
        Assertions.assertEquals(
                "/That/Is/A/Path/something.doc", meta.getString("path3")
        );
    }

    @Test
    void testWholeAndPartialMatches()
            throws IOException {
        var originalValue = "One dog, two dogs, three dogs";

        var meta = new Properties();
        meta.add("field", originalValue);

        var t = new ReplaceTransformer();
        t.getConfiguration().setOperations(
                List.of(

                        //--- Whole-match regular replace, case insensitive ----------------
                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("One dog")
                                                .setIgnoreCase(true)
                                )
                                .setToValue("One cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeNrmlInsensitiveUnchanged")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("One DOG, two DOGS, three DOGS")
                                                .setIgnoreCase(true)
                                )
                                .setToValue("One cat, two cats, three cats")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeNrmlInsensitiveCats")
                                .setDiscardUnchanged(true),

                        //--- Whole-match regular replace, case sensitive ------------------
                        new ReplaceOperation()
                                .setValueMatcher(basic("One dog"))
                                .setToValue("One cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeNrmlSensitiveUnchanged1")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("One DOG, two DOGS, three DOGS")
                                )
                                .setToValue("One cat, two cats, three cats")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeNrmlSensitiveUnchanged2")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("One dog, two dogs, three dogs")
                                )
                                .setToValue("One cat, two cats, three cats")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeNrmlSensitiveCats")
                                .setDiscardUnchanged(true),

                        //--- Whole-match regex replace, case insensitive ------------------
                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("One dog").setIgnoreCase(true)
                                )
                                .setToValue("One cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeRgxInsensitiveUnchanged")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("One DOG.*").setIgnoreCase(true)
                                )
                                .setToValue("One cat, two cats, three cats")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeRgxInsensitiveCats")
                                .setDiscardUnchanged(true),

                        //--- Whole-match regex replace, case sensitive --------------------
                        new ReplaceOperation()
                                .setValueMatcher(regex("One DOG.*"))
                                .setToValue("One cat, two cats, three cats")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeRgxSensitiveUnchanged")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(regex("One dog.*"))
                                .setToValue("One cat, two cats, three cats")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeRgxSensitiveCats")
                                .setDiscardUnchanged(true),

                        //--- Partial-match regular replace, case insensitive --------------
                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("DOG")
                                                .setPartial(true)
                                                .setIgnoreCase(true)
                                )
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("partNrmlInsensitive1Cat")
                                .setDiscardUnchanged(true),

                        //--- Partial-match regular replace, case sensitive ----------------
                        new ReplaceOperation()
                                .setValueMatcher(basic("DOG").setPartial(true))
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("partNrmlSensitiveUnchanged")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(basic("dog").setPartial(true))
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("partNrmlSensitive1Cat")
                                .setDiscardUnchanged(true),

                        //--- Partial-match regex replace, case insensitive ----------------
                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("DOG")
                                                .setPartial(true)
                                                .setIgnoreCase(true)
                                )
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("partRgxInsensitive1Cat")
                                .setDiscardUnchanged(true),

                        //--- Partial-match regex replace, case sensitive ------------------
                        new ReplaceOperation()
                                .setValueMatcher(regex("DOG").setPartial(true))
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("partRgxSensitiveUnchanged")
                                .setDiscardUnchanged(true),

                        new ReplaceOperation()
                                .setValueMatcher(regex("dog").setPartial(true))
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("partRgxSensitive1Cat")
                                .setDiscardUnchanged(true)
                )
        );

        //=== Asserts ==========================================================
        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        //--- Whole-match regular replace, case insensitive --------------------
        Assertions.assertNull(meta.getString("wholeNrmlInsensitiveUnchanged"));
        Assertions.assertEquals(
                "One cat, two cats, three cats",
                meta.getString("wholeNrmlInsensitiveCats")
        );

        //--- Whole-match regular replace, case sensitive ----------------------
        Assertions.assertNull(meta.getString("wholeNrmlSensitiveUnchanged1"));
        Assertions.assertNull(meta.getString("wholeNrmlSensitiveUnchanged2"));
        Assertions.assertEquals(
                "One cat, two cats, three cats",
                meta.getString("wholeNrmlSensitiveCats")
        );

        //--- Whole-match regex replace, case insensitive ----------------------
        Assertions.assertNull(meta.getString("wholeRgxInsensitiveUnchanged"));
        Assertions.assertEquals(
                "One cat, two cats, three cats",
                meta.getString("wholeRgxInsensitiveCats")
        );

        //--- Whole-match regex replace, case sensitive ------------------------
        Assertions.assertNull(meta.getString("wholeRgxSensitiveUnchanged"));
        Assertions.assertEquals(
                "One cat, two cats, three cats",
                meta.getString("wholeRgxSensitiveCats")
        );

        //--- Partial-match regular replace, case insensitive ------------------
        Assertions.assertEquals(
                "One cat, two dogs, three dogs",
                meta.getString("partNrmlInsensitive1Cat")
        );

        //--- Partial-match regular replace, case sensitive --------------------
        Assertions.assertNull(meta.getString("partNrmlSensitiveUnchanged"));
        Assertions.assertEquals(
                "One cat, two dogs, three dogs",
                meta.getString("partNrmlSensitive1Cat")
        );

        //--- Partial-match regex replace, case insensitive --------------------
        Assertions.assertEquals(
                "One cat, two dogs, three dogs",
                meta.getString("partRgxInsensitive1Cat")
        );

        //--- Partial-match regex replace, case sensitive ----------------------
        Assertions.assertNull(meta.getString("partRgxSensitiveUnchanged"));
        Assertions.assertEquals(
                "One cat, two dogs, three dogs",
                meta.getString("partRgxSensitive1Cat")
        );
    }

    @Test
    void testReplaceAll()
            throws IOException {

        var originalValue = "One dog, two dogs, three dogs";
        var meta = new Properties();
        meta.add("field", originalValue);

        var t = new ReplaceTransformer();

        t.getConfiguration().setOperations(
                List.of(
                        //--- Whole-match regular replace all ------------------------------
                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("dog")
                                                .setReplaceAll(true)
                                                .setIgnoreCase(true)
                                )
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeNrmlUnchanged")
                                .setDiscardUnchanged(true),

                        //--- Whole-match regex replace all --------------------------------
                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("dog")
                                                .setReplaceAll(true)
                                                .setIgnoreCase(true)
                                )
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("wholeRegexUnchanged")
                                .setDiscardUnchanged(true),

                        //--- Partial-match regular replace all ----------------------------
                        new ReplaceOperation()
                                .setValueMatcher(
                                        basic("DOG")
                                                .setPartial(true)
                                                .setReplaceAll(true)
                                                .setIgnoreCase(true)
                                )
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("partialNrmlCats")
                                .setDiscardUnchanged(true),

                        //--- Partial-match regex replace all ------------------------------
                        new ReplaceOperation()
                                .setValueMatcher(
                                        regex("D.G")
                                                .setPartial(true)
                                                .setReplaceAll(true)
                                                .setIgnoreCase(true)
                                )
                                .setToValue("cat")
                                .setFieldMatcher(basic("field"))
                                .setToField("partialRegexCats")
                                .setDiscardUnchanged(true)
                )
        );

        //=== Asserts ==========================================================
        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertNull(meta.getString("wholeNrmlUnchanged"));
        Assertions.assertNull(meta.getString("wholeRegexUnchanged"));
        Assertions.assertEquals(
                "One cat, two cats, three cats",
                meta.getString("partialNrmlCats")
        );
        Assertions.assertEquals(
                "One cat, two cats, three cats",
                meta.getString("partialRegexCats")
        );
    }

    @Test
    void testDiscardUnchanged()
            throws IOException {
        var meta = new Properties();
        meta.add("test1", "keep me");
        meta.add("test2", "throw me");

        var t = new ReplaceTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new ReplaceOperation()
                                .setValueMatcher(regex("nomatch"))
                                .setToValue("isaidnomatch")
                                .setFieldMatcher(basic("test1"))
                                .setDiscardUnchanged(false),

                        new ReplaceOperation()
                                .setValueMatcher(regex("nomatch"))
                                .setToValue("isaidnomatch")
                                .setFieldMatcher(basic("test2"))
                                .setDiscardUnchanged(true)
                )
        );

        //=== Asserts ==========================================================
        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("keep me", meta.getString("test1"));
        Assertions.assertNull(meta.getString("test2"));
    }

    @Test
    void testOnSet()
            throws IOException {

        // Test what happens when target field already has a value

        var meta = new Properties();
        meta.add("source1", "value 1");
        meta.add("target1", "target value 1");
        meta.add("source2", "value 2");
        meta.add("target2", "target value 2");
        meta.add("source3", "value 3");
        meta.add("target3", "target value 3");
        meta.add("source4", "value 4");
        meta.add("target4", "target value 4");

        var t = new ReplaceTransformer();
        t.getConfiguration().setOperations(
                List.of(

                        new ReplaceOperation()
                                .setFieldMatcher(basic("source1"))
                                .setToField("target1")
                                .setValueMatcher(
                                        regex("value").setPartial(true)
                                )
                                .setToValue("source value")
                                .setOnSet(PropertySetter.APPEND),

                        new ReplaceOperation()
                                .setFieldMatcher(basic("source2"))
                                .setToField("target2")
                                .setValueMatcher(
                                        regex("value").setPartial(true)
                                )
                                .setToValue("source value")
                                .setOnSet(PropertySetter.PREPEND),

                        new ReplaceOperation()
                                .setFieldMatcher(basic("source3"))
                                .setToField("target3")
                                .setValueMatcher(
                                        regex("value").setPartial(true)
                                )
                                .setOnSet(PropertySetter.REPLACE)
                                .setToValue("source value"),

                        new ReplaceOperation()
                                .setToField("target4")
                                .setValueMatcher(
                                        regex("value").setPartial(true)
                                )
                                .setToValue("source value")
                                .setOnSet(PropertySetter.OPTIONAL)
                                .setFieldMatcher(basic("source4"))
                )
        );

        //=== Asserts ==========================================================
        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals(
                "target value 1, source value 1",
                StringUtils.join(meta.getStrings("target1"), ", ")
        );
        Assertions.assertEquals(
                "source value 2, target value 2",
                StringUtils.join(meta.getStrings("target2"), ", ")
        );
        Assertions.assertEquals(
                "source value 3",
                StringUtils.join(meta.getStrings("target3"), ", ")
        );
        Assertions.assertEquals(
                "target value 4",
                StringUtils.join(meta.getStrings("target4"), ", ")
        );
    }
}
