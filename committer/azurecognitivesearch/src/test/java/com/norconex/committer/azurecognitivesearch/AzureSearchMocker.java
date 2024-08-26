/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.committer.azurecognitivesearch;

import static com.norconex.committer.azurecognitivesearch.AzureSearchCommitterConfig.DEFAULT_API_VERSION;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.Parameter;

// https://docs.microsoft.com/en-us/rest/api/searchservice/addupdate-or-delete-documents
class AzureSearchMocker {

    public static final String MOCK_API_KEY = "0123456789ABCDEFGHIJ";
    private static final String INDEX_PATH = "/indexes/test/docs/index";

    private final ListOrderedMap<String, Doc> db = new ListOrderedMap<>();

    AzureSearchMocker(ClientAndServer mockServer) {
        mockServer
                .when(
                        HttpRequest.request()
                                .withMethod("POST")
                                .withPath(INDEX_PATH)
                                .withContentType(
                                        MediaType.APPLICATION_JSON_UTF_8
                                )
                                .withHeader("api-key", MOCK_API_KEY)
                                .withQueryStringParameters(
                                        Parameter.param(
                                                "api-version",
                                                DEFAULT_API_VERSION
                                        )
                                )
                )
                .respond(
                        req -> {
                            var resp = index(db, req);
                            return HttpResponse.response()
                                    .withStatusCode(resp.code)
                                    .withReasonPhrase(resp.reason)
                                    .withBody(JsonBody.json(resp.body));
                        }
                );
    }

    public Doc getDoc(int docIndex) {
        return db.getValue(docIndex);
    }

    public Doc getDoc(String docKey) {
        return db.get(docKey);
    }

    public List<Doc> getAllDocs() {
        return db.valueList();
    }

    public int docCount() {
        return db.size();
    }

    public void clear() {
        db.clear();
    }

    private static MockResponse index(Map<String, Doc> db, HttpRequest req) {
        var commitRequests = new JSONObject(
                req.getBodyAsJsonOrXmlString()
        ).getJSONArray("value");

        var responseStatus = HttpStatus.SC_OK;
        var respArray = new JSONArray();
        for (var i = 0; i < commitRequests.length(); i++) {
            var commitRequest = commitRequests.getJSONObject(i);
            List<String> fields = new ArrayList<>(commitRequest.keySet());

            // First entry must be the action:
            var actionField = fields.remove(0);
            var action = commitRequest.getString(actionField);
            if (!"@search.action".equals(actionField)) {
                responseStatus = HttpStatus.SC_MULTI_STATUS;
                respArray.put(
                        docOpResponse(
                                "Unknown", MockResponse.badRequest(
                                        "First object key must be '@search.action'. Was '"
                                                + actionField + "' with value '"
                                                + action
                                                + "'."
                                )
                        )
                );
                break;
            }

            // Second entry is the document key
            var keyField = fields.get(0);
            var key = commitRequest.getString(keyField);

            // Apply request to database
            if ("upload".equals(action)) {
                var doc = new Doc(key);
                for (String field : fields) {
                    var values = commitRequest.optJSONArray(field);
                    if (values != null) {
                        for (var j = 0; j < values.length(); j++) {
                            doc.fields.put(field, values.getString(j));
                        }
                    } else {
                        doc.fields.put(field, commitRequest.getString(field));
                    }
                }
                var previousDoc = db.put(key, doc);
                if (previousDoc == null) {
                    respArray.put(
                            docOpResponse(
                                    key, MockResponse.created(null)
                            )
                    );
                } else {
                    respArray.put(docOpResponse(key, MockResponse.ok(null)));
                }
            } else if ("delete".equals(action)) {
                db.remove(key);
                respArray.put(docOpResponse(key, MockResponse.ok(null)));
            } else {
                responseStatus = HttpStatus.SC_MULTI_STATUS;
                respArray.put(
                        docOpResponse(
                                key, MockResponse.badRequest(
                                        "Unsupported action: " + action
                                )
                        )
                );
            }
        }

        var respBody = new JSONObject();
        respBody.put("value", respArray);
        if (responseStatus == HttpStatus.SC_OK) {
            return MockResponse.ok(respBody.toString());
        }
        if (responseStatus == HttpStatus.SC_MULTI_STATUS) {
            return MockResponse.multiStatus(respBody.toString());
        }
        return new MockResponse(
                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                "Internal Server Error", null
        );
    }

    private static JSONObject docOpResponse(String key, MockResponse docResp) {
        var isgood = docResp.code >= 200 && docResp.code < 300;
        var json = new JSONObject();
        json.put("key", key);
        json.put("status", isgood);
        json.put("errorMessage", isgood ? null : docResp.reason);
        json.put("statusCode", docResp.code);
        return json;
    }

    public static class Doc {
        private final String key;

        private final MultiValuedMap<String, String> fields =
                MultiMapUtils.newListValuedHashMap();

        public Doc(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public String getFieldValue(String field) {
            var values = getFieldValues(field);
            if (!values.isEmpty()) {
                return values.get(0);
            }
            return null;
        }

        public List<String> getFieldValues(String field) {
            return (List<String>) fields.get(field);
        }
    }

    private static class MockResponse {
        private int code;
        private String reason;
        private String body;

        MockResponse(int code, String reason, String body) {
            this.code = code;
            this.reason = reason;
            this.body = body;
        }

        static MockResponse ok(String body) {
            return new MockResponse(HttpStatus.SC_OK, "OK", body);
        }

        static MockResponse created(String body) {
            return new MockResponse(HttpStatus.SC_CREATED, "Created", body);
        }

        static MockResponse multiStatus(String body) {
            return new MockResponse(
                    HttpStatus.SC_MULTI_STATUS, "Multi-Status", body
            );
        }

        static MockResponse badRequest(String reason) {
            return badRequest(reason, null);
        }

        static MockResponse badRequest(String reason, String body) {
            return new MockResponse(HttpStatus.SC_BAD_REQUEST, reason, body);
        }
    }
}
