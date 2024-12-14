/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch.util;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * A very unsafe trust manager accepting ALL certificates. This includes
 * ones with encryption algorithms deemed unsafe, self-signed, etc.
 * @since 2.4.0
 */
public class TrustAllX509TrustManager extends X509ExtendedTrustManager {
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[] {};
    }

    @Override
    public void checkClientTrusted( //NOSONAR
            X509Certificate[] chain, String authType) {
        //NOOP
    }

    @Override
    public void checkServerTrusted( //NOSONAR
            X509Certificate[] chain, String authType) {
        //NOOP
    }

    @Override
    public void checkClientTrusted( //NOSONAR
            X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        //NOOP
    }

    @Override
    public void checkServerTrusted( //NOSONAR
            X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        //NOOP
    }

    @Override
    public void checkClientTrusted( //NOSONAR
            X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        //NOOP
    }

    @Override
    public void checkServerTrusted( //NOSONAR
            X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        //NOOP
    }
}
