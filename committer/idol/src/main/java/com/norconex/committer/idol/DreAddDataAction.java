/* Copyright 2020-2023 Norconex Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.equalsAny;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.url.HttpURL;

/* IDOL "DREADDDATA" index action.
 *
 *   POST http://server:port//DREADDDATA?:
 *   #DREREFERENCE 1234
 *   #DREFIELD myField1="value 1"
 *   #DREFIELD myField2="value 2"
 *   #DRETITLE
 *   Document Title
 *   #DRECONTENT
 *   Document content.
 *   #DREDBNAME some_database
 *   #DREENDDOC
 *   #DREREFERENCE ...
 *   ... repeat DREREFERENCE-DREENDDOC block as needed ...
 *   #DREENDDOC
 *   #DREENDDATAREFERENCE
 *
 * Reference material:
 *
 * https://www.microfocus.com/documentation/idol/IDOL_12_7/
 * DIH_12.7_Documentation/Help/#Index%20Actions/IndexData/
 * _IX_DREADDDATA.htm%3FTocPath%3DIndex%2520Actions%7CIndex%2520Data%7C_____2
 */
class DreAddDataAction implements IIdolIndexAction {

    private final IdolCommitterConfig config;

    DreAddDataAction(IdolCommitterConfig config) {
        this.config = config;
    }

    @Override
    public URL url(List<CommitterRequest> batch, HttpURL url)
            throws CommitterException {
        url.setPath(StringUtils.appendIfMissing(
                url.getPath(), "/") + "DREADDDATA");
        return url.toURL();
    }
    @Override
    public void writeTo(List<CommitterRequest> batch, Writer w)
            throws CommitterException {
        try {
            for (CommitterRequest req : batch) {
                writeIdxDocument(w, (UpsertRequest) req);
            }
            w.append("\n#DREENDDATANOOP\n\n");
        } catch (IOException e) {
            throw new CommitterException(
                    "Could not convert committer batch to IDX.", e);
        }
    }

    private void writeIdxDocument(Writer w, UpsertRequest req)
            throws CommitterException, IOException {

        String refField = config.getSourceReferenceField();
        String contentField = config.getSourceContentField();

        //--- Document reference ---
        String ref = req.getReference();
        if (StringUtils.isNotBlank(refField)) {
            ref = req.getMetadata().getString(refField);
            if (StringUtils.isBlank(ref)) {
                throw new CommitterException("Source reference field '"
                        + refField + "' has no value for document: "
                        + req.getReference());
            }
        }
        w.append("\n#DREREFERENCE ").append(ref);

        //--- Document metadata ---
        for (Entry<String, List<String>> en : req.getMetadata().entrySet()) {
            String name = en.getKey();
            List<String> values = en.getValue();
            if (values == null || equalsAny(name, refField, contentField)) {
                continue;
            }
            for (String value : values) {
                w.append("\n#DREFIELD ");
                w.append(name).append("=\"").append(value).append("\"");
            }
        }

        //--- IDOL Database ---
        if (StringUtils.isNotBlank(config.getDatabaseName())) {
            w.append("\n#DREDBNAME ");
            w.append(config.getDatabaseName());
        }

        //--- Document content ---
        String content;
        if (StringUtils.isNotBlank(contentField)) {
            content = StringUtils.trimToEmpty(String.join("\n\n",
                    req.getMetadata().getStrings(contentField)));
        } else {
            content = IOUtils.toString(req.getContent(), UTF_8);
        }
        w.append("\n#DRECONTENT\n");
        w.append(content);
        w.append("\n#DREENDDOC");

        w.append("\n");
    }
}
