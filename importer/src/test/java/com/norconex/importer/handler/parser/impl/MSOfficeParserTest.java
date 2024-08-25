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
package com.norconex.importer.handler.parser.impl;

import static com.norconex.importer.TestUtil.resourceAsFile;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

//TODO create a CSV file with all the test data?
class MSOfficeParserTest {

    private static final String CONTENT = "Hey Norconex, this is a test.";

    @TempDir
    static Path folder;

    //--- Microsoft Word -------------------------------------------------------
    // OOXML formats:
    @Test
    void test_MSOffice_Word_docx() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/word.docx")
        )
                .hasContentType(
                        "application/vnd.openxmlformats-officedocument."
                                + "wordprocessingml.document"
                )
                .hasContentFamily("Word Processor")
                .hasExtension("docx")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Word_docm() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/word.docm")
        )
                .hasContentType(
                        "application/vnd.ms-word.document.macroenabled.12"
                )
                .hasContentFamily("Word Processor")
                .hasExtension("docm")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Word_dotm() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/word.dotm")
        )
                .hasContentType(
                        "application/vnd.ms-word.template.macroenabled.12"
                )
                .hasContentFamily("Word Processor")
                .hasExtension("dotm")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Word_dotx() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/word.dotx")
        )
                .hasContentType(
                        "application/vnd.openxmlformats-officedocument."
                                + "wordprocessingml.template"
                )
                .hasContentFamily("Word Processor")
                .hasExtension("dotx")
                .contains(CONTENT);
    }

    // OLE formats:
    @Test
    void test_MSOffice_Word_doc() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/word.doc")
        )
                .hasContentType("application/msword")
                .hasContentFamily("Word Processor")
                .hasExtension("doc")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Word_dot() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/word.dot")
        )
                .hasContentType("application/msword")
                .hasContentFamily("Word Processor")
                .hasExtension("doc")
                .contains(CONTENT);
    }

    //--- Microsoft PowerPoint -------------------------------------------------
    // OOXML formats:
    @Test
    void test_MSOffice_PowerPoint_pptx() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.pptx")
        )
                .hasContentType(
                        "application/vnd.openxmlformats-officedocument."
                                + "presentationml.presentation"
                )
                .hasContentFamily("Presentation")
                .hasExtension("pptx")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_PowerPoint_pptm() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.pptm")
        )
                .hasContentType(
                        "application/vnd.ms-powerpoint.presentation.macroenabled.12"
                )
                .hasContentFamily("Presentation")
                .hasExtension("pptm")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_PowerPoint_potm() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.potm")
        )
                .hasContentType(
                        "application/vnd.ms-powerpoint.template.macroenabled.12"
                )
                .hasContentFamily("Presentation")
                .hasExtension("potm")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_PowerPoint_potx() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.potx")
        )
                .hasContentType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".presentationml.template"
                )
                .hasContentFamily("Presentation")
                .hasExtension("potx")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_PowerPoint_ppsm() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.ppsm")
        )
                .hasContentType(
                        "application/vnd.ms-powerpoint.slideshow.macroenabled.12"
                )
                .hasContentFamily("Presentation")
                .hasExtension("ppsm")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_PowerPoint_ppsx() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.ppsx")
        )
                .hasContentType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".presentationml.slideshow"
                )
                .hasContentFamily("Presentation")
                .hasExtension("ppsx")
                .contains(CONTENT);
    }

    // OLE formats:
    @Test
    void test_MSOffice_PowerPoint_ppt() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.ppt")
        )
                .hasContentType("application/vnd.ms-powerpoint")
                .hasContentFamily("Presentation")
                .hasExtension("ppt")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_PowerPoint_pot() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.pot")
        )
                .hasContentType("application/vnd.ms-powerpoint")
                .hasContentFamily("Presentation")
                .hasExtension("ppt")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_PowerPoint_pps() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/powerpoint.pps")
        )
                .hasContentType("application/vnd.ms-powerpoint")
                .hasContentFamily("Presentation")
                .hasExtension("ppt")
                .contains(CONTENT);
    }

    //--- Microsoft Excel ------------------------------------------------------
    // OOXML formats:
    @Test
    void test_MSOffice_Excel_xlsx() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel.xlsx")
        )
                .hasContentType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet"
                )
                .hasContentFamily("Spreadsheet")
                .hasExtension("xlsx")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Excel_xlam() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel.xlam")
        )
                .hasContentType(
                        "application/vnd.ms-excel.addin.macroenabled.12"
                )
                .hasContentFamily("Spreadsheet")
                .hasExtension("xlam")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Excel_xlsm() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel.xlsm")
        )
                .hasContentType(
                        "application/vnd.ms-excel.sheet.macroenabled.12"
                )
                .hasContentFamily("Spreadsheet")
                .hasExtension("xlsm")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Excel_xltm() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel.xltm")
        )
                .hasContentType(
                        "application/vnd.ms-excel.template.macroenabled.12"
                )
                .hasContentFamily("Spreadsheet")
                .hasExtension("xltm")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Excel_xltx() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel.xltx")
        )
                .hasContentType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.template"
                )
                .hasContentFamily("Spreadsheet")
                .hasExtension("xltx")
                .contains(CONTENT);
    }

    // OLE formats:
    @Test
    void test_MSOffice_Excel_xls() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel.xls")
        )
                .hasContentType("application/vnd.ms-excel")
                .hasContentFamily("Spreadsheet")
                .hasExtension("xls")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Excel_xla() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel.xla")
        )
                .hasContentType("application/vnd.ms-excel")
                .hasContentFamily("Spreadsheet")
                .hasExtension("xls")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Excel_xlt() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel.xlt")
        )
                .hasContentType("application/vnd.ms-excel")
                .hasContentFamily("Spreadsheet")
                .hasExtension("xls")
                .contains(CONTENT);
    }

    @Test
    void test_MSOffice_Excel95_xls() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/excel95.xls")
        )
                .hasContentType("application/vnd.ms-excel")
                .hasContentFamily("Spreadsheet")
                .hasExtension("xls")
                .contains(CONTENT);
    }

    //--- Microsoft Publisher --------------------------------------------------
    @Test
    void test_MSOffice_Publisher_pub() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/publisher.pub")
        )
                .hasContentType("application/x-mspublisher")
                .hasContentFamily("Publishing")
                .hasExtension("pub")
                .contains(CONTENT);
    }

    //--- Microsoft Visio ------------------------------------------------------
    @Test
    void test_MSOffice_Visio_vsd() throws Exception {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/msoffice/visio.vsd")
        )
                .hasContentType("application/vnd.visio")
                .hasContentFamily("Vector Graphic")
                .hasExtension("vsd")
                .contains(CONTENT);
    }
}
