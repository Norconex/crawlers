/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.beam.frontier;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Normalizes URLs to avoid crawling duplicate content.
 * @author Norconex Inc.
 */
public class UrlNormalizer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private static final Pattern WWW_PATTERN = Pattern.compile("^www\\d*\\.");
    private static final Pattern DEFAULT_PORT_PATTERN = Pattern.compile(":(80|443)");
    private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/$");
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "(;|\\?)(jsessionid|phpsessid|aspsessionid|sid)=[^&?#]+", 
            Pattern.CASE_INSENSITIVE);
    
    /**
     * Normalize a URL to its canonical form.
     * @param url the URL to normalize
     * @return the normalized URL
     */
    public static String normalize(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        try {
            // Parse URL
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol().toLowerCase();
            String host = parsedUrl.getHost().toLowerCase();
            int port = parsedUrl.getPort();
            String path = parsedUrl.getPath();
            String query = parsedUrl.getQuery();
            
            // Build normalized URL
            StringBuilder normalized = new StringBuilder();
            
            // Protocol and host
            normalized.append(protocol).append("://");
            
            // Remove www prefix if present
            host = WWW_PATTERN.matcher(host).replaceFirst("");
            normalized.append(host);
            
            // Add port only if it's not the default port
            if (port != -1 && !((protocol.equals("http") && port == 80) || 
                                (protocol.equals("https") && port == 443))) {
                normalized.append(":").append(port);
            }
            
            // Path
            if (path == null || path.isEmpty()) {
                normalized.append("/");
            } else {
                normalized.append(path);
            }
            
            // Remove trailing slash (except for root path)
            if (normalized.length() > protocol.length() + 3 && 
                normalized.charAt(normalized.length() - 1) == '/' &&
                normalized.charAt(normalized.length() - 2) != '/') {
                normalized.setLength(normalized.length() - 1);
            }
            
            // Query parameters (sorted alphabetically)
            if (query != null && !query.isEmpty()) {
                // Remove session IDs
                query = SESSION_ID_PATTERN.matcher(query).replaceAll("");
                
                if (!query.isEmpty()) {
                    String[] params = query.split("&");
                    java.util.Arrays.sort(params);
                    
                    boolean firstParam = true;
                    for (String param : params) {
                        if (!param.isEmpty()) {
                            normalized.append(firstParam ? '?' : '&');
                            normalized.append(param);
                            firstParam = false;
                        }
                    }
                }
            }
            
            return normalized.toString();
            
        } catch (MalformedURLException e) {
            // For invalid URLs, return as-is
            return url;
        }
    }
}