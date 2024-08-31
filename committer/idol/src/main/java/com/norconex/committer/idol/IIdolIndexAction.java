/* Copyright 2024 Norconex Inc.
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

package com.norconex.committer.idol;

import java.io.Writer;
import java.net.URL;
import java.util.List;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.commons.lang.url.HttpURL;

/* Delete/add params are set by IDOL Client prior to calling
 * "prepare".
 */
interface IIdolIndexAction {

    URL url(List<CommitterRequest> batch, HttpURL startUrl)
            throws CommitterException;

    void writeTo(List<CommitterRequest> batch, Writer writer)
            throws CommitterException;
}
