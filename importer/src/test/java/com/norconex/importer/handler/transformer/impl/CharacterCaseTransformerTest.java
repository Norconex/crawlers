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
package com.norconex.importer.handler.transformer.impl;

import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.APPLY_BOTH;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.APPLY_FIELD;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.APPLY_VALUE;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_LOWER;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_SENTENCES;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_SENTENCES_FULLY;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_STRING;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_STRING_FULLY;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_SWAP;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_UPPER;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_WORDS;
import static com.norconex.importer.handler.transformer.impl.CharacterCaseTransformerConfig.CASE_WORDS_FULLY;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;

class CharacterCaseTransformerTest {

    @Test
    void testUpperLowerValues()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("field1", "Doit être upper");
        meta.add("field1", "Must be upper");
        meta.set("field2", "DOIT ÊTRE LOWER");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();

        InputStream is;
        cfg.setFieldMatcher(TextMatcher.basic("field1"));
        cfg.setCaseType(CASE_UPPER);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("field2"));
        cfg.setCaseType(CASE_LOWER);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        Assertions.assertEquals(
                "DOIT ÊTRE UPPER", meta.getStrings("field1").get(0));
        Assertions.assertEquals(
                "MUST BE UPPER", meta.getStrings("field1").get(1));
        Assertions.assertEquals("doit être lower", meta.getString("field2"));
    }

    @Test
    void testUpperLowerField()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("fieldMustBeUpper", "value 1");
        meta.add("fieldMustBeLower", "value 2");
        meta.set("fieldMustBeCapitalized", "value 3");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();
        InputStream is;

        cfg.setFieldMatcher(TextMatcher.basic("fieldMustBeUpper"));
        cfg.setCaseType(CASE_UPPER);
        cfg.setApplyTo(APPLY_FIELD);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("fieldMustBeLower"));
        cfg.setCaseType(CASE_LOWER);
        cfg.setApplyTo(APPLY_BOTH);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("fieldMustBeCapitalized"));
        cfg.setCaseType(CASE_WORDS);
        cfg.setApplyTo(APPLY_FIELD);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        var fields = meta.keySet().toArray(ArrayUtils.EMPTY_STRING_ARRAY);
        for (String field : fields) {
            Assertions.assertTrue(EqualsUtil.equalsAny(
                    field, "FIELDMUSTBEUPPER", "fieldmustbelower",
                    "FieldMustBeCapitalized"));
        }
    }

    @Test
    void testSwapCase()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("fieldMustBeSwapped", "ValUe Swap. \n  OK.");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();
        InputStream is;

        cfg.setFieldMatcher(TextMatcher.basic("fieldMustBeSwapped"));
        cfg.setCaseType(CASE_SWAP);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        Assertions.assertEquals("vALuE sWAP. \n  ok.",
                meta.getString("fieldMustBeSwapped"));
    }

    @Test
    void testCapitalizeString()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("string1", "normal String. another One.");
        meta.add("string2", " string starting with a Space.");
        meta.add("string3", "1 string starting with a Number.");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();
        InputStream is;

        cfg.setFieldMatcher(TextMatcher.basic("string1"));
        cfg.setCaseType(CASE_STRING);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string2"));
        cfg.setCaseType(CASE_STRING);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string3"));
        cfg.setCaseType(CASE_STRING);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        Assertions.assertEquals("Normal String. another One.",
                meta.getString("string1"));
        Assertions.assertEquals(" String starting with a Space.",
                meta.getString("string2"));
        Assertions.assertEquals("1 string starting with a Number.",
                meta.getString("string3"));
    }

    @Test
    void testCapitalizeStringFully()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("string1", "normal String. another One.");
        meta.add("string2", " string starting with a Space.");
        meta.add("string3", "1 string starting with a Number.");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();

        InputStream is;

        cfg.setFieldMatcher(TextMatcher.basic("string1"));
        cfg.setCaseType(CASE_STRING_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string2"));
        cfg.setCaseType(CASE_STRING_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string3"));
        cfg.setCaseType(CASE_STRING_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        Assertions.assertEquals("Normal string. another one.",
                meta.getString("string1"));
        Assertions.assertEquals(" String starting with a space.",
                meta.getString("string2"));
        Assertions.assertEquals("1 string starting with a number.",
                meta.getString("string3"));
    }

    @Test
    void testCapitalizeWords()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("string1", "normal String. another One.");
        meta.add("string2", " string starTing with a Space.");
        meta.add("string3", "1 string starTing with a Number.");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();

        InputStream is;

        cfg.setFieldMatcher(TextMatcher.basic("string1"));
        cfg.setCaseType(CASE_WORDS);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string2"));
        cfg.setCaseType(CASE_WORDS);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string3"));
        cfg.setCaseType(CASE_WORDS);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        Assertions.assertEquals("Normal String. Another One.",
                meta.getString("string1"));
        Assertions.assertEquals(" String StarTing With A Space.",
                meta.getString("string2"));
        Assertions.assertEquals("1 String StarTing With A Number.",
                meta.getString("string3"));
    }

    @Test
    void testCapitalizeWordsFully()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("string1", "normal String. another One.");
        meta.add("string2", " string starTing with a Space.");
        meta.add("string3", "1 string starTing with a Number.");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();

        InputStream is;

        cfg.setFieldMatcher(TextMatcher.basic("string1"));
        cfg.setCaseType(CASE_WORDS_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string2"));
        cfg.setCaseType(CASE_WORDS_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string3"));
        cfg.setCaseType(CASE_WORDS_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        Assertions.assertEquals("Normal String. Another One.",
                meta.getString("string1"));
        Assertions.assertEquals(" String Starting With A Space.",
                meta.getString("string2"));
        Assertions.assertEquals("1 String Starting With A Number.",
                meta.getString("string3"));
    }

    @Test
    void testCapitalizeSentences()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("string1", "normal String. another One.");
        meta.add("string2", " string starTing with a Space.");
        meta.add("string3", "1 string starTing with a Number. pLUS this");
        meta.add("string4", "yes.no. yes. . ");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();

        InputStream is;

        cfg.setFieldMatcher(TextMatcher.basic("string1"));
        cfg.setCaseType(CASE_SENTENCES);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string2"));
        cfg.setCaseType(CASE_SENTENCES);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string3"));
        cfg.setCaseType(CASE_SENTENCES);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string4"));
        cfg.setCaseType(CASE_SENTENCES);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        Assertions.assertEquals("Normal String. Another One.",
                meta.getString("string1"));
        Assertions.assertEquals(" String starTing with a Space.",
                meta.getString("string2"));
        Assertions.assertEquals("1 string starTing with a Number. PLUS this",
                meta.getString("string3"));
        Assertions.assertEquals("Yes.no. Yes. . ", meta.getString("string4"));
    }

    @Test
    void testCapitalizeSentencesFully()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("string1", "normal String. another One.");
        meta.add("string2", " string starTing with a Space.");
        meta.add("string3", "1 string starTing with a Number. pLUS this");

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();

        InputStream is;

        cfg.setFieldMatcher(TextMatcher.basic("string1"));
        cfg.setCaseType(CASE_SENTENCES_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string2"));
        cfg.setCaseType(CASE_SENTENCES_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        cfg.setFieldMatcher(TextMatcher.basic("string3"));
        cfg.setCaseType(CASE_SENTENCES_FULLY);
        cfg.setApplyTo(APPLY_VALUE);
        is = new NullInputStream(0);
        t.accept(TestUtil.newDocContext("blah", is, meta));

        Assertions.assertEquals("Normal string. Another one.",
                meta.getString("string1"));
        Assertions.assertEquals(" String starting with a space.",
                meta.getString("string2"));
        Assertions.assertEquals("1 string starting with a number. Plus this",
                meta.getString("string3"));
    }

    @Test
    void testOnBody() throws ImporterHandlerException {

        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();

        var body = """
                normal String. another One.
                 string starTing with a Space.
                1 string starTing with a Number. pLUS this
                """.getBytes();

        cfg.setCaseType(CASE_SENTENCES);
        var out = new ByteArrayOutputStream();
        t.accept(TestUtil.newDocContext(
                "blah", new ByteArrayInputStream(body), out, new Properties()));

        assertThat(out.toString()).isEqualToIgnoringNewLines("""
                Normal String. Another One.
                 String starTing with a Space.
                1 string starTing with a Number. PLUS this
                """);
    }

    @Test
    void testWriteRead() {
        var t = new CharacterCaseTransformer();
        var cfg = t.getConfiguration();
        cfg.setCaseType(CASE_UPPER);
        cfg.setApplyTo(APPLY_BOTH);
        cfg.setFieldMatcher(TextMatcher.regex(".*"));
        BeanMapper.DEFAULT.assertWriteRead(t);
    }
}
