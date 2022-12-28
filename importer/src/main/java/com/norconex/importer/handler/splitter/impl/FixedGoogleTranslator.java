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
package com.norconex.importer.handler.splitter.impl;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.tika.language.translate.GoogleTranslator;

import com.norconex.importer.ImporterRuntimeException;

/**
 * Adds accessor methods to {@link GoogleTranslator} to access the API key.
 */
class FixedGoogleTranslator extends GoogleTranslator {

    public FixedGoogleTranslator() {
        try {
            FieldUtils.writeField(this, "isAvailable", true, true);
        } catch (IllegalAccessException e) {
            throw new ImporterRuntimeException("Cannot mark as available.", e);
        }
    }
    public void setApiKey(String apiKey) {
        try {
            FieldUtils.writeField(this, "apiKey", apiKey, true);
        } catch (IllegalAccessException e) {
            throw new ImporterRuntimeException("Cannot set api key.", e);
        }
    }
    public String getApiKey() {
        try {
            return (String) FieldUtils.readField(this, "apiKey", true);
        } catch (IllegalAccessException e) {
            throw new ImporterRuntimeException("Cannot set api key.", e);
        }
    }
}
