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
package com.norconex.importer.handler.filter;

import com.norconex.commons.lang.xml.XML;

/**
 * Constants indicating the action to perform upon matching a condition.
 * Typically used with filtering conditions.
 */
public enum OnMatch {
    INCLUDE, EXCLUDE;

    public static OnMatch includeIfNull(OnMatch onMatch) {
        return onMatch != null ? onMatch : INCLUDE;
    }
    public static OnMatch excludeIfNull(OnMatch onMatch) {
        return onMatch != null ? onMatch : EXCLUDE;
    }


    /**
     * Load an {@link OnMatch} from "onMatch" attributed (if any) on
     * supplied XML element.
     * @param xml the XML to get the OnMatch from
     * @param defaultOnMatch default OnMatch if it does not exist in XML
     * @return OnMatch or <code>null</code> if defaultOnMatch
     *     is <code>null</code>
         */
    public static OnMatch loadFromXML(XML xml, OnMatch defaultOnMatch) {
        if (xml == null) {
            return defaultOnMatch;
        }
        return xml.getEnum("@onMatch", OnMatch.class, defaultOnMatch);
    }
    /**
     * Saves an {@link OnMatch} to supplied XML element as an "onMatch"
     * attribute. Saving a <code>null</code> OnMatch
     * has no effect.
     * @param xml the XML to save the OnMatch to
     * @param onMatch the OnMatch to save
         */
    public static void saveToXML(XML xml, OnMatch onMatch) {
        xml.setAttribute("onMatch", onMatch);
    }
}
