/* Copyright 2010-2022 Norconex Inc.
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

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.impl.ReplaceTagger.Replacement;
import com.norconex.importer.parser.ParseState;

class ReplaceTaggerTest {

    // Test for: https://github.com/Norconex/collector-http/issues/416
    @Test
    void testNoValue()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("test", "a b c");

        Replacement r;
        var tagger = new ReplaceTagger();

        // regex
        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("test"));
        r.setToField("regex");
        r.setValueMatcher(TextMatcher.regex("\\s+b\\s+"));
        r.getValueMatcher().setPartial(true);
        r.setToValue("");
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        // normal
        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("test"));
        r.setToField("normal");
        r.setValueMatcher(TextMatcher.basic("b"));
        r.getValueMatcher().setPartial(true);
        r.setToValue("");
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("ac", meta.getString("regex"));
        Assertions.assertEquals("a  c", meta.getString("normal"));

        // XML
        var xml = """
        	<tagger>\
        	<replace toField="regexXML">\
        	  <fieldMatcher method="regex">test</fieldMatcher>\
        	  <valueMatcher method="regex" replaceAll="true" \
        	partial="true">\
        	(.{0,0})\\s+b\\s+</valueMatcher>\
        	</replace>\
        	<replace toField="normalXML">\
        	  <fieldMatcher>test</fieldMatcher>\
        	  <valueMatcher partial="true">b</valueMatcher>\
        	</replace>\
        	</tagger>""";
        tagger = new ReplaceTagger();
        tagger.loadFromXML(new XML(xml));
        r = new Replacement();
        tagger.addReplacement(r);
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("ac", meta.getString("regexXML"));
        Assertions.assertEquals("a  c", meta.getString("normalXML"));
    }

    //This is a test for https://github.com/Norconex/importer/issues/29
    //where the replaced value is equal to the original (EXP_NAME1), it should
    //still store it (was a bug).
    @Test
    void testMatchReturnSameValue()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("EXP_NAME+COUNTRY1", "LAZARUS ANDREW");
        meta.add("EXP_NAME+COUNTRY2", "LAZARUS ANDREW [US]");

        Replacement r;
        var tagger = new ReplaceTagger();

        // Author 1
        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("EXP_NAME+COUNTRY1"));
        r.setToField("EXP_NAME1");
        r.setValueMatcher(TextMatcher.regex("^(.+?)(.?\\[([A-Z]+)\\])?$"));
        r.setToValue("$1");
        r.setDiscardUnchanged(false);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("EXP_NAME+COUNTRY1"));
        r.setToField("EXP_COUNTRY1");
        r.setValueMatcher(TextMatcher.regex("^(.+?)(.?\\[([A-Z]+)\\])?$"));
        r.setToValue("$3");
        r.setDiscardUnchanged(false);
        tagger.addReplacement(r);

        // Author 2
        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("EXP_NAME+COUNTRY2"));
        r.setToField("EXP_NAME2");
        r.setValueMatcher(TextMatcher.regex("^(.+?)(.?\\[([A-Z]+)\\])?$"));
        r.setToValue("$1");
        r.setDiscardUnchanged(false);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("EXP_NAME+COUNTRY2"));
        r.setToField("EXP_COUNTRY2");
        r.setValueMatcher(TextMatcher.regex("^(.+?)(.?\\[([A-Z]+)\\])?$"));
        r.setToValue("$3");
        r.setDiscardUnchanged(false);
        tagger.addReplacement(r);

        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("LAZARUS ANDREW", meta.getString("EXP_NAME1"));
        Assertions.assertEquals("", meta.getString("EXP_COUNTRY1"));
        Assertions.assertEquals("LAZARUS ANDREW", meta.getString("EXP_NAME2"));
        Assertions.assertEquals("US", meta.getString("EXP_COUNTRY2"));
    }

    @Test
        void testWriteRead() {
        Replacement r;
        var tagger = new ReplaceTagger();

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("fromValue1"));
        r.setToValue("toValue1");
        r.setFieldMatcher(TextMatcher.basic("fromName1"));
        r.setOnSet(PropertySetter.REPLACE);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("fromValue2"));
        r.setToValue("toValue2");
        r.setFieldMatcher(TextMatcher.basic("fromName1"));
        r.setOnSet(PropertySetter.PREPEND);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("fromValue1"));
        r.setToValue("toValue1");
        r.setFieldMatcher(TextMatcher.basic("fromName2"));
        r.setToField("toName2");
        r.getValueMatcher().setIgnoreCase(true);
        r.setOnSet(PropertySetter.OPTIONAL);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("fromValue3"));
        r.setToValue("toValue3");
        r.setFieldMatcher(TextMatcher.basic("fromName3"));
        r.setToField("toName3");
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        XML.assertWriteRead(tagger, "handler");
    }


    @Test
    void testRegularReplace() throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("fullMatchField", "full value match");
        meta.add("partialNoMatchField", "partial value nomatch");
        meta.add("matchOldField", "match to new field");
        meta.add("nomatchOldField", "no match to new field");
        meta.add("caseField", "Value Of Mixed Case");

        Replacement r;
        var tagger = new ReplaceTagger();

//        r = new Replacement();
//        r.setValueMatcher(TextMatcher.basic("full value match"));
//        r.setToValue("replaced");
//        r.setFieldMatcher(TextMatcher.basic("fullMatchField"));
//        r.setDiscardUnchanged(true);
//        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("bad if you see me"));
        r.setToValue("not replaced");
        r.setFieldMatcher(TextMatcher.basic("partialNoMatchField"));
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

//        r = new Replacement();
//        r.setValueMatcher(TextMatcher.basic("match to new field"));
//        r.setToValue("replaced to new field");
//        r.setFieldMatcher(TextMatcher.basic("matchOldField"));
//        r.setToField("matchNewField");
//        r.setDiscardUnchanged(true);
//        tagger.addReplacement(r);
//
//        r = new Replacement();
//        r.setValueMatcher(TextMatcher.basic("bad if you see me"));
//        r.setToValue("not replaced");
//        r.setFieldMatcher(TextMatcher.basic("nomatchOldField"));
//        r.setToField("nomatchNewField");
//        r.setDiscardUnchanged(true);
//        tagger.addReplacement(r);
//
//        r = new Replacement();
//        r.setValueMatcher(TextMatcher.basic("value Of mixed case"));
//        r.setToValue("REPLACED");
//        r.setFieldMatcher(TextMatcher.basic("caseField"));
//        r.getValueMatcher().setIgnoreCase(true);
//        r.setDiscardUnchanged(true);
//        tagger.addReplacement(r);

        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

//        Assertions.assertEquals("replaced", meta.getString("fullMatchField"));
        Assertions.assertNull(meta.getString("partialNoMatchField"));
//        Assertions.assertEquals("replaced to new field",
//                meta.getString("matchNewField"));
//        Assertions.assertEquals("no match to new field",
//                meta.getString("nomatchOldField"));
//        Assertions.assertNull(meta.getString("nomatchNewField"));
//        Assertions.assertEquals("REPLACED", meta.getString("caseField"));
    }

    @Test
    void testRegexReplace() throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("path1", "/this/is/a/path/file.doc");
        meta.add("path2", "/that/is/a/path/file.doc");
        meta.add("path3", "/That/Is/A/Path/File.doc");

        Replacement r;
        var tagger = new ReplaceTagger();

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("(.*)/.*"));
        r.setToValue("$1");
        r.setFieldMatcher(TextMatcher.basic("path1"));
        r.getValueMatcher().setPartial(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("(.*)/.*"));
        r.setToValue("$1");
        r.setFieldMatcher(TextMatcher.basic("path2"));
        r.setToField("folder");
        r.getValueMatcher().setPartial(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("file"));
        r.setToValue("something");
        r.setFieldMatcher(TextMatcher.basic("path3"));
        r.getValueMatcher().setIgnoreCase(true);
        r.getValueMatcher().setPartial(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("/this/is/a/path", meta.getString("path1"));
        Assertions.assertEquals("/that/is/a/path", meta.getString("folder"));
        Assertions.assertEquals(
                "/that/is/a/path/file.doc", meta.getString("path2"));
        Assertions.assertEquals(
                "/That/Is/A/Path/something.doc", meta.getString("path3"));
    }

    @Test
    void testWholeAndPartialMatches()
             throws ImporterHandlerException {
        var originalValue = "One dog, two dogs, three dogs";


        var meta = new Properties();
        meta.add("field", originalValue);

        Replacement r;
        var tagger = new ReplaceTagger();

        //--- Whole-match regular replace, case insensitive --------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("One dog"));
        r.setToValue("One cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeNrmlInsensitiveUnchanged");
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("One DOG, two DOGS, three DOGS"));
        r.setToValue("One cat, two cats, three cats");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeNrmlInsensitiveCats");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Whole-match regular replace, case sensitive ----------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("One dog"));
        r.setToValue("One cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeNrmlSensitiveUnchanged1");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("One DOG, two DOGS, three DOGS"));
        r.setToValue("One cat, two cats, three cats");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeNrmlSensitiveUnchanged2");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("One dog, two dogs, three dogs"));
        r.setToValue("One cat, two cats, three cats");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeNrmlSensitiveCats");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Whole-match regex replace, case insensitive ----------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("One dog"));
        r.setToValue("One cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeRgxInsensitiveUnchanged");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("One DOG.*"));
        r.setToValue("One cat, two cats, three cats");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeRgxInsensitiveCats");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Whole-match regex replace, case sensitive ------------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("One DOG.*"));
        r.setToValue("One cat, two cats, three cats");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeRgxSensitiveUnchanged");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("One dog.*"));
        r.setToValue("One cat, two cats, three cats");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeRgxSensitiveCats");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Partial-match regular replace, case insensitive ------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("DOG"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("partNrmlInsensitive1Cat");
        r.getValueMatcher().setPartial(true);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Partial-match regular replace, case sensitive --------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("DOG"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("partNrmlSensitiveUnchanged");
        r.getValueMatcher().setPartial(true);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("dog"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("partNrmlSensitive1Cat");
        r.getValueMatcher().setPartial(true);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Partial-match regex replace, case insensitive --------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("DOG"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("partRgxInsensitive1Cat");
        r.getValueMatcher().setPartial(true);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Partial-match regex replace, case sensitive ----------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("DOG"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("partRgxSensitiveUnchanged");
        r.getValueMatcher().setPartial(true);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("dog"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("partRgxSensitive1Cat");
        r.getValueMatcher().setPartial(true);
        r.getValueMatcher().setIgnoreCase(false);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //=== Asserts ==========================================================
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        //--- Whole-match regular replace, case insensitive --------------------
        Assertions.assertNull(meta.getString("wholeNrmlInsensitiveUnchanged"));
        Assertions.assertEquals("One cat, two cats, three cats",
                meta.getString("wholeNrmlInsensitiveCats"));

        //--- Whole-match regular replace, case sensitive ----------------------
        Assertions.assertNull(meta.getString("wholeNrmlSensitiveUnchanged1"));
        Assertions.assertNull(meta.getString("wholeNrmlSensitiveUnchanged2"));
        Assertions.assertEquals("One cat, two cats, three cats",
                meta.getString("wholeNrmlSensitiveCats"));

        //--- Whole-match regex replace, case insensitive ----------------------
        Assertions.assertNull(meta.getString("wholeRgxInsensitiveUnchanged"));
        Assertions.assertEquals("One cat, two cats, three cats",
                meta.getString("wholeRgxInsensitiveCats"));

        //--- Whole-match regex replace, case sensitive ------------------------
        Assertions.assertNull(meta.getString("wholeRgxSensitiveUnchanged"));
        Assertions.assertEquals("One cat, two cats, three cats",
                meta.getString("wholeRgxSensitiveCats"));

        //--- Partial-match regular replace, case insensitive ------------------
        Assertions.assertEquals("One cat, two dogs, three dogs",
                meta.getString("partNrmlInsensitive1Cat"));

        //--- Partial-match regular replace, case sensitive --------------------
        Assertions.assertNull(meta.getString("partNrmlSensitiveUnchanged"));
        Assertions.assertEquals("One cat, two dogs, three dogs",
                meta.getString("partNrmlSensitive1Cat"));

        //--- Partial-match regex replace, case insensitive --------------------
        Assertions.assertEquals("One cat, two dogs, three dogs",
                meta.getString("partRgxInsensitive1Cat"));

        //--- Partial-match regex replace, case sensitive ----------------------
        Assertions.assertNull(meta.getString("partRgxSensitiveUnchanged"));
        Assertions.assertEquals("One cat, two dogs, three dogs",
                meta.getString("partRgxSensitive1Cat"));
    }

    @Test
    void testReplaceAll()
             throws ImporterHandlerException {


        var originalValue = "One dog, two dogs, three dogs";
        var meta = new Properties();
        meta.add("field", originalValue);

        Replacement r;
        var tagger = new ReplaceTagger();

        //--- Whole-match regular replace all ----------------------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("dog"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeNrmlUnchanged");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setReplaceAll(true);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Whole-match regex replace all ------------------------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("dog"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("wholeRegexUnchanged");
        r.getValueMatcher().setPartial(false);
        r.getValueMatcher().setReplaceAll(true);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Partial-match regular replace all --------------------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.basic("DOG"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("partialNrmlCats");
        r.getValueMatcher().setPartial(true);
        r.getValueMatcher().setReplaceAll(true);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //--- Partial-match regex replace all ----------------------------------
        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("D.G"));
        r.setToValue("cat");
        r.setFieldMatcher(TextMatcher.basic("field"));
        r.setToField("partialRegexCats");
        r.getValueMatcher().setPartial(true);
        r.getValueMatcher().setReplaceAll(true);
        r.getValueMatcher().setIgnoreCase(true);
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //=== Asserts ==========================================================
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertNull(meta.getString("wholeNrmlUnchanged"));
        Assertions.assertNull(meta.getString("wholeRegexUnchanged"));
        Assertions.assertEquals("One cat, two cats, three cats",
                meta.getString("partialNrmlCats"));
        Assertions.assertEquals("One cat, two cats, three cats",
                meta.getString("partialRegexCats"));
    }


    @Test
    void testDiscardUnchanged()
             throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("test1", "keep me");
        meta.add("test2", "throw me");

        Replacement r;
        var tagger = new ReplaceTagger();

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("nomatch"));
        r.setToValue("isaidnomatch");
        r.setFieldMatcher(TextMatcher.basic("test1"));
        r.setDiscardUnchanged(false);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setValueMatcher(TextMatcher.regex("nomatch"));
        r.setToValue("isaidnomatch");
        r.setFieldMatcher(TextMatcher.basic("test2"));
        r.setDiscardUnchanged(true);
        tagger.addReplacement(r);

        //=== Asserts ==========================================================
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("keep me", meta.getString("test1"));
        Assertions.assertNull(meta.getString("test2"));
    }

    @Test
    void testOnSet()
             throws ImporterHandlerException {

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

        Replacement r;
        var tagger = new ReplaceTagger();

        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("source1"));
        r.setToField("target1");
        r.setValueMatcher(TextMatcher.regex("value"));
        r.getValueMatcher().setPartial(true);
        r.setToValue("source value");
        r.setOnSet(PropertySetter.APPEND);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("source2"));
        r.setToField("target2");
        r.setValueMatcher(TextMatcher.regex("value"));
        r.getValueMatcher().setPartial(true);
        r.setToValue("source value");
        r.setOnSet(PropertySetter.PREPEND);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("source3"));
        r.setToField("target3");
        r.setValueMatcher(TextMatcher.regex("value"));
        r.getValueMatcher().setPartial(true);
        r.setToValue("source value");
        r.setOnSet(PropertySetter.REPLACE);
        tagger.addReplacement(r);

        r = new Replacement();
        r.setFieldMatcher(TextMatcher.basic("source4"));
        r.setToField("target4");
        r.setValueMatcher(TextMatcher.regex("value"));
        r.getValueMatcher().setPartial(true);
        r.setToValue("source value");
        r.setOnSet(PropertySetter.OPTIONAL);
        tagger.addReplacement(r);

        //=== Asserts ==========================================================
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertEquals(
                "target value 1, source value 1",
                StringUtils.join(meta.getStrings("target1"), ", "));
        Assertions.assertEquals(
                "source value 2, target value 2",
                StringUtils.join(meta.getStrings("target2"), ", "));
        Assertions.assertEquals(
                "source value 3",
                StringUtils.join(meta.getStrings("target3"), ", "));
        Assertions.assertEquals(
                "target value 4",
                StringUtils.join(meta.getStrings("target4"), ", "));
    }
}
