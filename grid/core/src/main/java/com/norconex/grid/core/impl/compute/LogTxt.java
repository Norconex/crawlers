/* Copyright 2025 Norconex Inc.
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
package com.norconex.grid.core.impl.compute;

import org.apache.commons.lang3.StringUtils;

public final class LogTxt {

    private LogTxt() {
    }

    public static final String RECV_FROM = "\u2190-----"; // ←-----
    public static final String SEND_TO_ONE = "-----\u2192"; // -----→
    public static final String SEND_TO_MANY = "---\u2192"; // ---→
    public static final String COORD =
            new String(Character.toChars(0x1F3D7)); // 🏗
    public static final String WORKER =
            new String(Character.toChars(0x1F477)); // 👷

    public static String msg(String action, Object... details) {
        var msg = StringUtils.leftPad("[" + action + "] ", 15);
        for (var dtl : details) {
            msg += " " + dtl; //NOSONAR
        }
        return msg;
    }
}
