/* Copyright 2023 Norconex Inc.
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
package com.norconex.committer.core;

import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;

public class StringListConverter extends SimpleArgumentConverter {

    @Override
    protected Object convert(Object source, Class<?> targetType)
            throws ArgumentConversionException {
        if (source == null) {
            return null;
        }
        if (source instanceof String
                && String[].class.isAssignableFrom(targetType)) {
            return ((String) source).split("\\s*\\|\\s*");
        }
        throw new IllegalArgumentException(
                "Conversion from " + source.getClass() + " to "
                        + targetType + " not supported.");
    }
}