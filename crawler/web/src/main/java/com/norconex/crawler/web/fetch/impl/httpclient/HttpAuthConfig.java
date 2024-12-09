/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.httpclient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.net.Host;
import com.norconex.commons.lang.security.Credentials;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * Generic HTTP Fetcher authentication configuration.
 * </p>
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
@FieldNameConstants
public class HttpAuthConfig {

    /**
     * The authentication method.
     */
    private HttpAuthMethod method;

    /**
     * The URL for "form" authentication.
     * The username and password will be POSTed to this URL unless
     * {@link #setFormSelector(String)} is set, then it is assumed to be
     * the URL of the page containing the form.
     * This is used only for "form" authentication.
     */
    private String url;

    /**
     * The name of the HTML field where the username is set.
     * This is used only for "form" authentication.
     */
    private String formUsernameField;

    /**
     * The name of the HTML field where the password is set.
     * This is used only for "form" authentication.
     */
    private String formPasswordField;

    /**
     * User name and password.
     */
    private final Credentials credentials = new Credentials();

    /**
     * The host for the current authentication scope.
     * <code>null</code> (default value) indicates "any host" for the
     * scope.
     * Used for BASIC and DIGEST authentication.
     */
    private Host host;

    /**
     * The realm name for the current authentication scope.
     * <code>null</code> (default) indicates "any realm" for the scope.
     * Used for BASIC and DIGEST authentication.
     */
    private String realm;

    /**
     * The authentication form character set for the form field values.
     * Default is UTF-8.
     */
    private Charset formCharset = StandardCharsets.UTF_8;

    /**
     * The CSS selector that identifies the form in a login page.
     * When set, requires {@link #getUrl()} to be pointing to a login
     * page containing a login form.
     */
    private String formSelector;

    /**
     * Additional form parameters possibly expected by the login form.
     */
    private final Map<String, String> formParams = new HashMap<>();

    /**
     * The NTLM authentication workstation name.
     */
    private String workstation;

    /**
     * Gets the NTLM authentication domain.
     */
    private String domain;

    /**
     * Whether to perform preemptive authentication
     * (valid for "basic" authentication method).
     */
    private boolean preemptive;

    public Credentials getCredentials() {
        return credentials;
    }

    public void setCredentials(Credentials authCredentials) {
        credentials.copyFrom(authCredentials);
    }

    /**
     * Sets an authentication form parameter (equivalent to "input" or other
     * fields in HTML forms).
     * @param name form parameter name
     * @param value form parameter value
     */
    @JsonIgnore
    public void setFormParam(String name, String value) {
        formParams.put(name, value);
    }

    /**
     * Sets authentication form parameters (equivalent to "input" or other
     * fields in HTML forms).
     * @param params map of form parameter names and values
     */
    public void setFormParams(Map<String, String> params) {
        CollectionUtil.setAll(formParams, params);
    }

    /**
     * Gets an authentication form parameter (equivalent to "input" or other
     * fields in HTML forms).
     * @param name form parameter name
     * @return form parameter value or <code>null</code> if
     *         no match is found
     */
    @JsonIgnore
    public String getFormParam(String name) {
        return formParams.get(name);
    }

    /**
     * Gets all authentication form parameters (equivalent to "input" or other
     * fields in HTML forms).
     * @return form parameters map (name and value)
     */
    public Map<String, String> getFormParams() {
        return new HashMap<>(formParams);
    }

    /**
     * Gets all authentication form parameter names. If no form parameters
     * are set, it returns an empty array.
     * @return HTTP request header names
     */
    @JsonIgnore
    public List<String> getFormParamNames() {
        return Collections.unmodifiableList(
                new ArrayList<>(formParams.keySet()));
    }
}
