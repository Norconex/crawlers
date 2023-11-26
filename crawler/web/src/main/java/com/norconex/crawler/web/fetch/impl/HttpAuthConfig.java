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
package com.norconex.crawler.web.fetch.impl;

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
 * {@nx.xml.usage
 *   <method>[form|basic|digest|ntlm|spnego|kerberos]</method>
 *
 *   <!-- These apply to any authentication mechanism -->
 *   <credentials>
 *     {@nx.include com.norconex.commons.lang.security.Credentials@nx.xml.usage}
 *   </credentials>
 *
 *   <!-- These apply to FORM authentication -->
 *   <formUsernameField>...</formUsernameField>
 *   <formPasswordField>...</formPasswordField>
 *   <url>
 *     (Either a login form's action target URL or the URL of a page containing
 *      a login form if a "formSelector" is specified.)
 *   </url>
 *   <formCharset>...</formCharset>
 *   <!-- Extra form parameters required to authenticate (since 2.8.0) -->
 *   <formParams>
 *     <param name="(param name)">(param value)</param>
 *     <!-- You can repeat this param tag as needed. -->
 *   </formParams>
 *   <formSelector>
 *     (CSS selector identifying the login page. E.g., "form")
 *   </formSelector>
 *
 *   <!-- These apply to both BASIC and DIGEST authentication -->
 *   <host>
 *     {@nx.include com.norconex.commons.lang.net.Host@nx.xml.usage}
 *   </host>
 *
 *   <realm>...</realm>
 *
 *   <!-- This applies to BASIC authentication -->
 *   <preemptive>[false|true]</preemptive>
 *
 *   <!-- These apply to NTLM authentication -->
 *   <host>
 *     {@nx.include com.norconex.commons.lang.net.Host@nx.xml.usage}
 *   </host>
 *   <workstation>...</workstation>
 *   <domain>...</domain>
 * }
 *
 * <p>
 * The above XML configurable options can be nested in a supporting parent
 * tag of any name.
 * The expected parent tag name is defined by the consuming classes
 * (e.g. "authentication").
 * </p>
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
@FieldNameConstants
public class HttpAuthConfig {

    //TODO consider using factory for auth configs and mechanisms.

    /** Form-based authentication method. */
    public static final String METHOD_FORM = "form";
    /** BASIC authentication method. */
    public static final String METHOD_BASIC = "basic";
    /** DIGEST authentication method. */
    public static final String METHOD_DIGEST = "digest";
    /** NTLM authentication method. */
    public static final String METHOD_NTLM = "ntlm";
    /** Experimental: SPNEGO authentication method. */
    public static final String METHOD_SPNEGO = "SPNEGO";
    /** Experimental: Kerberos authentication method. */
    public static final String METHOD_KERBEROS = "Kerberos";

    /**
     * <p>
     * The authentication method. Valid values are (case insensitive):
     * </p>
     * <ul>
     *   <li>form</li>
     *   <li>basic</li>
     *   <li>digest</li>
     *   <li>ntlm</li>
     *   <li>spnego</li>
     *   <li>kerberos</li>
     * </ul>
     * @return authentication method
     * @param method authentication method
     */
    private String method;

    /**
     * The URL for "form" authentication.
     * The username and password will be POSTed to this URL.
     * This is used only for "form" authentication.
     * @param url "form" authentication URL
     * @return "form" authentication URL
     */
    private String url;
    //TODO consider taking those out in favor of 'formParams'?

    /**
     * The name of the HTML field where the username is set.
     * This is used only for "form" authentication.
     * @param formUsernameField name of the HTML field
     * @return username name of the HTML field
     */
    private String formUsernameField;

    /**
     * The name of the HTML field where the password is set.
     * This is used only for "form" authentication.
     * @param formPasswordField name of the HTML field
     * @return name of the HTML field
     */
    private String formPasswordField;

    private final Credentials credentials = new Credentials();

    /**
     * The host for the current authentication scope.
     * <code>null</code> (default value) indicates "any host" for the
     * scope.
     * Used for BASIC and DIGEST authentication.
     * @param host host for the scope
     * @return host for the scope
     */
    private Host host;

    /**
     * The realm name for the current authentication scope.
     * <code>null</code> (default) indicates "any realm" for the scope.
     * Used for BASIC and DIGEST authentication.
     * @param realm reaml name for the scope
     * @return realm name for the scope
     */
    private String realm;

    //form
    /**
     * The authentication form character set for the form field values.
     * Default is UTF-8.
     * @param formCharset authentication form character set
     * @return authentication form character set
     */
    private Charset formCharset = StandardCharsets.UTF_8;

    /**
     * The CSS selelector that identifies the form in a login page.
     * When set, requires {@link #getUrl()} to be pointing to a login
     * page containing a login form.
     * @param formSelector form selector
     * @return form selector
     */
    private String formSelector;

    private final Map<String, String> formParams = new HashMap<>();

    /**
     * The NTLM authentication workstation name.
     * @param workstation workstation name
     * @return workstation name
     */
    private String workstation;

    /**
     * Gets the NTLM authentication domain.
     * @param domain authentication domain
     * @return authentication domain
     */
    private String domain;

    /**
     * Whether to perform preemptive authentication
     * (valid for "basic" authentication method).
     * @param preemptive
     *            <code>true</code> to perform preemptive authentication
     * @return <code>true</code> to perform preemptive authentication
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
//
//    @Override
//    public void loadFromXML(XML xml) {
//        method = xml.getString("method", method);
//        formUsernameField =
//                xml.getString("formUsernameField", formUsernameField);
//        formPasswordField =
//                xml.getString("formPasswordField", formPasswordField);
//        xml.ifXML("credentials", x -> x.populate(credentials));
//        url = xml.getString("url", url);
//        host = Host.loadFromXML(xml.getXML("host"), host);
//        realm = xml.getString("realm", realm);
//        formCharset = xml.getCharset("formCharset", formCharset);
//        workstation = xml.getString("workstation", workstation);
//        domain = xml.getString("domain", domain);
//        preemptive = xml.getBoolean("preemptive", preemptive);
//        formSelector = xml.getString("formSelector", formSelector);
//        setFormParams(xml.getStringMap(
//                "formParams/param", "@name", ".", formParams));
//    }
//
//    @Override
//    public void saveToXML(XML xml) {
//        xml.addElement("method", method);
//        credentials.saveToXML(xml.addElement("credentials"));
//        xml.addElement("formUsernameField", formUsernameField);
//        xml.addElement("formPasswordField", formPasswordField);
//        xml.addElement("url", url);
//        Host.saveToXML(xml.addElement("host"), host);
//        xml.addElement("formCharset", formCharset);
//        xml.addElement("workstation", workstation);
//        xml.addElement("domain", domain);
//        xml.addElement("realm", realm);
//        xml.addElement("preemptive", preemptive);
//        xml.addElement("formSelector", formSelector);
//
//        var xmlAuthFormParams = xml.addXML("formParams");
//        for (Entry<String, String> entry : formParams.entrySet()) {
//            xmlAuthFormParams.addXML("param").setAttribute(
//                    "name", entry.getKey()).setTextContent(entry.getValue());
//        }
//    }
}
