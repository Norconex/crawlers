/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.importer;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.i18n.UncheckedException;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.DocContext;
import com.norconex.importer.handler.condition.BaseCondition;
import com.norconex.importer.handler.parser.ParseState;

public final class TestUtil {

    private static final String BASE_PATH =
         "src/site/resources/examples/books/alice-in-wonderland-book-chapter-1";

    private TestUtil() {
    }

    public static Properties newMetadata() {
        var p = new Properties();
        p.loadFromMap(MapUtil.toMap(
            "field1", "value1",
            "field2", "value2",
            "field3", List.of("value3.1", "value3.2")
        ));
        return p;
    }

    public static String getContentAsString(Doc doc)
            throws IOException {
        return IOUtils.toString(doc.getInputStream(), StandardCharsets.UTF_8);
    }
    public static String getContentAsString(DocContext docCtx)
            throws IOException {
        return IOUtils.toString(docCtx.input().asReader(UTF_8));
    }

    public static File getAlicePdfFile() {
        return new File(BASE_PATH + ".pdf");
    }
    public static File getAliceDocxFile() {
        return new File(BASE_PATH + ".docx");
    }
    public static File getAliceZipFile() {
        return new File(BASE_PATH + ".zip");
    }
    public static File getAliceHtmlFile() {
        return new File(BASE_PATH + ".html");
    }
    public static File getAliceTextFile() {
        return new File(BASE_PATH + ".txt");
    }

    public static Doc getAlicePdfDoc() {
        return newDoc(getAlicePdfFile());
    }
    public static Doc getAliceDocxDoc() {
        return newDoc(getAliceDocxFile());
    }
    public static Doc getAliceZipDoc() {
        return newDoc(getAliceZipFile());
    }
    public static Doc getAliceHtmlDoc() {
        return newDoc(getAliceHtmlFile());
    }
    public static Doc getAliceTextDoc() {
        return newDoc(getAliceTextFile());
    }

    public static Importer getTestConfigImporter() throws IOException {
        try (Reader r = new InputStreamReader(
                TestUtil.class.getResourceAsStream("test-config.xml"))) {
            return BeanMapper.DEFAULT.read(Importer.class, r, Format.XML);
        }
    }

    public static boolean condition(BaseCondition cond, String ref,
            Properties metadata, ParseState parseState) throws IOException {
        return condition(cond, ref, null, metadata, parseState);
    }
    public static boolean condition(BaseCondition cond, String ref,
            InputStream is, Properties metadata, ParseState parseState)
                    throws IOException {
        var input = is == null ? new NullInputStream(0) : is;
        return cond.test(newDocContext(ref, input, metadata));
    }

    public static void transform(Consumer<DocContext> t, String ref,
            Properties metadata, ParseState parseState)
                    throws IOException {
        transform(t, ref, null, metadata, parseState);
    }
    public static void transform(Consumer<DocContext> t, String ref,
            InputStream is, Properties metadata, ParseState parseState)
                    throws IOException {
        var input = is == null ? new NullInputStream(0) : is;
        t.accept(newDocContext(ref, input, metadata, parseState));
    }

    public static Doc newDoc(File file) {
        try {
            return new Doc(
                    file.getAbsolutePath(),
                    CachedInputStream.cache(new FileInputStream(file)));
        } catch (FileNotFoundException e) {
            throw new UncheckedException(e);
        }
    }

    public static Doc newDoc() {
        return newDoc("N/A", null, new Properties());
    }
    public static Doc newDoc(Properties meta) {
        return newDoc("N/A", null, meta);
    }
    public static Doc newDoc(String ref) {
        return newDoc(ref, null, new Properties());
    }
    public static Doc newDoc(String ref, Properties meta) {
        return newDoc(ref, null, meta);
    }
    public static Doc newDoc(String ref, InputStream in) {
        return newDoc(ref, in, new Properties());
    }
    public static Doc newDoc(
            String ref, InputStream in, Properties meta) {
        // Remove document.reference for tests that need the same count
        // as values they entered in metadata. Just keep it if explicitely
        // passed.
        var hasRef = meta != null && meta.containsKey("document.reference");
        var inputStream = in != null ? in : InputStream.nullInputStream();
        var doc = new Doc(ref, CachedInputStream.cache(inputStream), meta);
        if (!hasRef) {
            doc.getMetadata().remove("document.reference");
        }
        var ct = doc.getMetadata().getString(DocMetadata.CONTENT_TYPE);
        if (ct != null) {
            doc.getDocRecord().setContentType(ContentType.valueOf(ct));
        }

        return doc;
    }
    public static DocContext newDocContext() {
        return newDocContext("dummy-ref", null, new Properties());
    }
    public static DocContext newDocContext(
            String ref, InputStream in) {
        return newDocContext(ref, in, null, ParseState.PRE);
    }
    public static DocContext newDocContext(
            String ref, InputStream in, Properties meta) {
        return newDocContext(ref, in, meta, ParseState.PRE);
    }
    public static DocContext newDocContext(
            String ref, InputStream in,
            Properties meta, ParseState state) {
        return DocContext.builder()
                .doc(newDoc(ref, in, meta))
                .parseState(state)
                .eventManager(new EventManager())
                .build();
    }

    public static DocContext newDocContext(String ref, String body) {
        return newDocContext(ref, new ByteArrayInputStream(body.getBytes()));
    }
    public static DocContext newDocContext(String body) {
        return newDocContext(
                "dummy-ref", new ByteArrayInputStream(body.getBytes()));
    }


    public static String contentAsString(Doc doc) {
        try {
            return IOUtils.toString(
                    doc.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public static String toString(InputStream is) {
        try {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public static CachedInputStream toCachedInputStream(String str) {
        return CachedInputStream.cache(toInputStream(str));
    }
    public static InputStream toInputStream(String str) {
        return new ByteArrayInputStream(str.getBytes());
    }

    public static CachedInputStream failingCachedInputStream() {
        return new CachedStreamFactory().newInputStream(failingInputStream());
    }
    public static InputStream failingInputStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Test mock exception.");
            }
        };
    }

    public static String toUtf8UnixLineString(ByteArrayOutputStream os) {
        return os.toString(UTF_8).replace("\r", "");
    }

    public static Path resourceAsFile(
            Path folder, String resourcePath) throws IOException {
        var file = Files.createTempFile(folder, null,
                StringUtils.substringAfterLast(resourcePath, "/"));
        Files.copy(TestUtil.class.getResourceAsStream(resourcePath), file,
                StandardCopyOption.REPLACE_EXISTING);
        return file;
    }
}
