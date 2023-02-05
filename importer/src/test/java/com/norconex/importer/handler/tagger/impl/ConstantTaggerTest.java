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

import java.io.InputStream;

import org.apache.commons.io.input.NullInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class ConstantTaggerTest {

    @Test
    void testWriteRead() {
        var tagger = new ConstantTagger();
        tagger.addConstant("constant1", "value1");
        tagger.addConstant("constant1", "value2");
        tagger.addConstant("constant2", "valueA");
        tagger.addConstant("constant2", "valueA");
        tagger.addConstant("constant3", "valueZ");
        tagger.setOnSet(PropertySetter.REPLACE);
        XML.assertWriteRead(tagger, "handler");
    }

    @Test
    void testOnSet() throws ImporterHandlerException {
        var m = new Properties();
        m.add("test1", "1");
        m.add("test1", "2");
        m.add("test2", "1");
        m.add("test2", "2");
        m.add("test3", "1");
        m.add("test3", "2");
        m.add("test4", "1");
        m.add("test4", "2");

        var t = new ConstantTagger();
        InputStream is;

        // APPEND
        t.setOnSet(PropertySetter.APPEND);
        t.addConstant("test1", "3");
        t.addConstant("test1", "4");
        is = new NullInputStream(0);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, m), is, ParseState.PRE);
        Assertions.assertArrayEquals(new String[]{
                "1", "2", "3", "4"}, m.getStrings("test1").toArray());
        // REPLACE
        t.setOnSet(PropertySetter.REPLACE);
        t.addConstant("test2", "3");
        t.addConstant("test2", "4");
        is = new NullInputStream(0);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, m), is, ParseState.PRE);
        Assertions.assertArrayEquals(new String[]{
                "3", "4"}, m.getStrings("test2").toArray());
        // OPTIONAL
        t.setOnSet(PropertySetter.OPTIONAL);
        t.addConstant("test3", "3");
        t.addConstant("test3", "4");
        is = new NullInputStream(0);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, m), is, ParseState.PRE);
        Assertions.assertArrayEquals(new String[]{
                "1", "2"}, m.getStrings("test3").toArray());
        // PREPEND
        t.setOnSet(PropertySetter.PREPEND);
        t.addConstant("test4", "3");
        t.addConstant("test4", "4");
        is = new NullInputStream(0);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, m), is, ParseState.PRE);
        Assertions.assertArrayEquals(new String[]{
                "3", "4", "1", "2"}, m.getStrings("test4").toArray());
    }
}
