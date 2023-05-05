/* Copyright 2015-2023 Norconex Inc.
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
import org.apache.tika.language.translate.impl.Lingo24Translator;

import com.norconex.importer.ImporterRuntimeException;

/**
 * Adds accessor methods to {@link Lingo24Translator} to access the user key.
 */
class FixedLingo24Translator extends Lingo24Translator {

    public FixedLingo24Translator() {
        try {
            FieldUtils.writeField(this, "isAvailable", true, true);
        } catch (IllegalAccessException e) {
            throw new ImporterRuntimeException("Cannot mark as available.", e);
        }
    }
    public void setUserKey(String userKey) {
        try {
            FieldUtils.writeField(this, "userKey", userKey, true);
        } catch (IllegalAccessException e) {
            throw new ImporterRuntimeException("Cannot set user key.", e);
        }
    }
    public String getUserKey() {
        try {
            return (String) FieldUtils.readField(this, "userKey", true);
        } catch (IllegalAccessException e) {
            throw new ImporterRuntimeException("Cannot set user key.", e);
        }
    }
}
