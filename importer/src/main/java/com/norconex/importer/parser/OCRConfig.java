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
package com.norconex.importer.parser;

import org.apache.commons.lang3.StringUtils;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * OCR configuration details. OCR relies the open-source
 * <a href="https://github.com/tesseract-ocr/tesseract">Tesseract OCR</a>
 * product to be already installed on your system.
 * </p>
 * <p>
 * Since 2.10.0, it is recommended to specify the full path the
 * Tesseract executable file (as opposed to its installation directory).
 * </p>
 */
@EqualsAndHashCode
@ToString
public class OCRConfig {

    private String path;
    private String languages;
    private String contentTypes;

    /**
     * Constructor.
     */
    public OCRConfig() {
    }

    /**
     * Gets the Tesseract OCR engine executable file path.
     * @return path
     */
    public String getPath() {
        return path;
    }
    /**
     * Sets the Tesseract OCR engine executable file path.
     * @param path installation path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Gets languages to use by OCR.
     * @return languages
     */
    public String getLanguages() {
        return languages;
    }
    /**
     * Sets languages to use by OCR.
     * @param languages languages to use by OCR.
     */
    public void setLanguages(String languages) {
        this.languages = languages;
    }

    /**
     * Gets the regular expression matching content types to restrict OCR to.
     * @return content types
     */
    public String getContentTypes() {
        return contentTypes;
    }
    /**
     * Sets the regular expression matching content types to restrict OCR to.
     * @param contentTypes content types
     */
    public void setContentTypes(String contentTypes) {
        this.contentTypes = contentTypes;
    }

    public boolean isEmpty() {
        return  StringUtils.isBlank(path)
                && StringUtils.isBlank(languages)
                && StringUtils.isBlank(contentTypes);
    }
}
