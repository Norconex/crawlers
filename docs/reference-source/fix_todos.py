#!/usr/bin/env python3
"""
Script to replace TODO: Add documentation for this property. lines
with proper descriptions in MDX files.
"""

import os
import re

# Mapping: (file_basename, property_name) -> description
lookup = {
    # CrawlConfig.mdx
    ('CrawlConfig.mdx', 'id'): 'Unique identifier for the crawler, used in logging and cluster coordination.',
    ('CrawlConfig.mdx', 'numThreads'): 'Number of threads used to crawl documents in parallel.',
    ('CrawlConfig.mdx', 'maxDepth'): 'Maximum crawl depth from start references. A depth of 0 means only the start references themselves are processed.',
    ('CrawlConfig.mdx', 'maxDocuments'): 'Maximum total number of documents to process across all crawl runs combined.',
    ('CrawlConfig.mdx', 'maxDocumentsPerRun'): 'Maximum number of documents to process in a single crawl run.',
    ('CrawlConfig.mdx', 'maxRunDocuments'): 'Maximum number of documents to process in a single crawl run.',
    ('CrawlConfig.mdx', 'maxCrawlDuration'): 'Maximum duration a crawl run is allowed to run before stopping gracefully.',
    ('CrawlConfig.mdx', 'maxQueueBatchSize'): 'Number of references dequeued and dispatched to crawl threads at a time.',
    ('CrawlConfig.mdx', 'maxStreamCacheSize'): 'Maximum memory a single document stream may use before spilling to disk.',
    ('CrawlConfig.mdx', 'maxStreamCachePoolSize'): 'Maximum total memory used across all in-memory document stream caches.',
    ('CrawlConfig.mdx', 'minProgressLoggingInterval'): 'Minimum interval between crawl progress log entries.',
    ('CrawlConfig.mdx', 'idleTimeout'): 'How long the crawler waits with no activity before stopping. Helps detect stalled crawls.',
    ('CrawlConfig.mdx', 'deferredShutdownDuration'): 'Grace period given to in-flight work when a shutdown signal is received.',
    ('CrawlConfig.mdx', 'importer'): 'The importer pipeline configuration for document parsing and metadata enrichment.',
    ('CrawlConfig.mdx', 'fetchers'): 'One or more fetchers responsible for retrieving document content from the source.',
    ('CrawlConfig.mdx', 'fetchersMaxRetries'): 'Maximum number of retry attempts when a fetcher fails to retrieve a document.',
    ('CrawlConfig.mdx', 'fetchersRetryDelay'): 'Time to wait between fetcher retry attempts.',
    ('CrawlConfig.mdx', 'committers'): 'One or more committers that send processed documents to target systems (e.g., search engines, databases).',
    ('CrawlConfig.mdx', 'eventListeners'): 'Handlers invoked in response to crawl lifecycle events such as document fetched, rejected, or committed.',
    ('CrawlConfig.mdx', 'documentFilters'): 'Filters that determine which documents are included in or excluded from processing.',
    ('CrawlConfig.mdx', 'metadataFilters'): 'Filters applied to document metadata to include or exclude documents based on field values.',
    ('CrawlConfig.mdx', 'metadataChecksummer'): 'Computes a checksum from document metadata fields, used for change detection and deduplication.',
    ('CrawlConfig.mdx', 'documentChecksummer'): 'Computes a checksum from document content, used for change detection and deduplication.',
    ('CrawlConfig.mdx', 'metadataDeduplicate'): 'When enabled, documents with a previously seen metadata checksum are skipped.',
    ('CrawlConfig.mdx', 'documentDeduplicate'): 'When enabled, documents with a previously seen content checksum are skipped.',
    ('CrawlConfig.mdx', 'metadataFetchSupport'): 'Controls whether a metadata-only (e.g., HTTP HEAD) fetch is attempted before a full document fetch.',
    ('CrawlConfig.mdx', 'documentFetchSupport'): 'Controls whether document content is fetched. Disable for metadata-only crawling.',
    ('CrawlConfig.mdx', 'cluster'): 'Configuration for distributed, multi-node crawling. Leave unset for single-node operation.',
    ('CrawlConfig.mdx', 'spoiledReferenceStrategizer'): 'Strategy for handling references that repeatedly fail to be processed (spoiled references).',
    # WebCrawlerConfig.mdx - shared with CrawlConfig
    ('WebCrawlerConfig.mdx', 'id'): 'Unique identifier for the crawler, used in logging and cluster coordination.',
    ('WebCrawlerConfig.mdx', 'numThreads'): 'Number of threads used to crawl documents in parallel.',
    ('WebCrawlerConfig.mdx', 'maxDepth'): 'Maximum crawl depth from start references. A depth of 0 means only the start references themselves are processed.',
    ('WebCrawlerConfig.mdx', 'maxDocuments'): 'Maximum total number of documents to process across all crawl runs combined.',
    ('WebCrawlerConfig.mdx', 'maxDocumentsPerRun'): 'Maximum number of documents to process in a single crawl run.',
    ('WebCrawlerConfig.mdx', 'maxRunDocuments'): 'Maximum number of documents to process in a single crawl run.',
    ('WebCrawlerConfig.mdx', 'maxCrawlDuration'): 'Maximum duration a crawl run is allowed to run before stopping gracefully.',
    ('WebCrawlerConfig.mdx', 'maxQueueBatchSize'): 'Number of references dequeued and dispatched to crawl threads at a time.',
    ('WebCrawlerConfig.mdx', 'maxStreamCacheSize'): 'Maximum memory a single document stream may use before spilling to disk.',
    ('WebCrawlerConfig.mdx', 'maxStreamCachePoolSize'): 'Maximum total memory used across all in-memory document stream caches.',
    ('WebCrawlerConfig.mdx', 'minProgressLoggingInterval'): 'Minimum interval between crawl progress log entries.',
    ('WebCrawlerConfig.mdx', 'idleTimeout'): 'How long the crawler waits with no activity before stopping. Helps detect stalled crawls.',
    ('WebCrawlerConfig.mdx', 'deferredShutdownDuration'): 'Grace period given to in-flight work when a shutdown signal is received.',
    ('WebCrawlerConfig.mdx', 'importer'): 'The importer pipeline configuration for document parsing and metadata enrichment.',
    ('WebCrawlerConfig.mdx', 'fetchers'): 'One or more fetchers responsible for retrieving document content from the source.',
    ('WebCrawlerConfig.mdx', 'fetchersMaxRetries'): 'Maximum number of retry attempts when a fetcher fails to retrieve a document.',
    ('WebCrawlerConfig.mdx', 'fetchersRetryDelay'): 'Time to wait between fetcher retry attempts.',
    ('WebCrawlerConfig.mdx', 'committers'): 'One or more committers that send processed documents to target systems (e.g., search engines, databases).',
    ('WebCrawlerConfig.mdx', 'eventListeners'): 'Handlers invoked in response to crawl lifecycle events such as document fetched, rejected, or committed.',
    ('WebCrawlerConfig.mdx', 'documentFilters'): 'Filters that determine which documents are included in or excluded from processing.',
    ('WebCrawlerConfig.mdx', 'metadataFilters'): 'Filters applied to document metadata to include or exclude documents based on field values.',
    ('WebCrawlerConfig.mdx', 'metadataChecksummer'): 'Computes a checksum from document metadata fields, used for change detection and deduplication.',
    ('WebCrawlerConfig.mdx', 'documentChecksummer'): 'Computes a checksum from document content, used for change detection and deduplication.',
    ('WebCrawlerConfig.mdx', 'metadataDeduplicate'): 'When enabled, documents with a previously seen metadata checksum are skipped.',
    ('WebCrawlerConfig.mdx', 'documentDeduplicate'): 'When enabled, documents with a previously seen content checksum are skipped.',
    ('WebCrawlerConfig.mdx', 'metadataFetchSupport'): 'Controls whether a metadata-only (e.g., HTTP HEAD) fetch is attempted before a full document fetch.',
    ('WebCrawlerConfig.mdx', 'documentFetchSupport'): 'Controls whether document content is fetched. Disable for metadata-only crawling.',
    ('WebCrawlerConfig.mdx', 'cluster'): 'Configuration for distributed, multi-node crawling. Leave unset for single-node operation.',
    # WebCrawlerConfig.mdx only
    ('WebCrawlerConfig.mdx', 'delayResolver'): 'Determines the politeness delay between consecutive requests to the same host.',
    ('WebCrawlerConfig.mdx', 'linkExtractors'): 'Extractors that discover outbound links within crawled documents.',
    ('WebCrawlerConfig.mdx', 'urlNormalizers'): 'Normalizers that standardize URL formats before they are stored or followed.',
    ('WebCrawlerConfig.mdx', 'urlScopeResolver'): 'Determines whether a discovered URL falls within the crawl scope.',
    ('WebCrawlerConfig.mdx', 'recrawlableResolver'): 'Determines whether a previously crawled document is eligible for re-crawling on this run.',
    ('WebCrawlerConfig.mdx', 'sitemapLocator'): 'Locates sitemap files for crawled websites.',
    ('WebCrawlerConfig.mdx', 'sitemapResolver'): 'Parses and processes sitemap files to extract crawl references.',
    ('WebCrawlerConfig.mdx', 'robotsTxtProvider'): 'Provides robots.txt crawl rules for a domain.',
    ('WebCrawlerConfig.mdx', 'robotsMetaProvider'): 'Extracts robot directives from HTML meta tags and HTTP response headers.',
    ('WebCrawlerConfig.mdx', 'canonicalLinkDetector'): 'Detects canonical link relationships to avoid crawling duplicate pages.',
    ('WebCrawlerConfig.mdx', 'keepReferencedLinks'): 'Which referenced links to keep as metadata on the crawled document (e.g., in-scope, out-of-scope, or all).',
    ('WebCrawlerConfig.mdx', 'postImportLinks'): 'References discovered after the importer has processed the document.',
    ('WebCrawlerConfig.mdx', 'postImportLinksKeep'): 'When enabled, links discovered after import are retained as document metadata.',
    ('WebCrawlerConfig.mdx', 'startReferencesSitemaps'): 'Sitemap URLs used as additional sources of start references.',
    # ClusterConfig.mdx
    ('ClusterConfig.mdx', 'connector'): 'The cluster connector implementation to use (e.g., Hazelcast, MVStore).',
    ('ClusterConfig.mdx', 'clustered'): 'When enabled, the crawler operates in distributed cluster mode.',
    ('ClusterConfig.mdx', 'adminDisabled'): 'When enabled, the cluster admin HTTP interface is not started.',
    ('ClusterConfig.mdx', 'adminPort'): 'Port on which the cluster admin HTTP interface listens.',
    # HttpClientFetcher.mdx
    ('HttpClientFetcher.mdx', 'authentication'): 'HTTP authentication configuration (supports Basic, Digest, NTLM, and form-based authentication).',
    ('HttpClientFetcher.mdx', 'connectionTimeout'): 'Maximum time to wait for a connection to be established, in milliseconds.',
    ('HttpClientFetcher.mdx', 'connectionRequestTimeout'): 'Maximum time to wait for a connection from the connection pool, in milliseconds.',
    ('HttpClientFetcher.mdx', 'maxConnections'): 'Maximum total number of concurrent HTTP connections.',
    ('HttpClientFetcher.mdx', 'maxConnectionsPerRoute'): 'Maximum concurrent HTTP connections per target host.',
    ('HttpClientFetcher.mdx', 'maxConnectionIdleTime'): 'Maximum time a pooled connection may remain idle before being evicted, in milliseconds.',
    ('HttpClientFetcher.mdx', 'maxConnectionInactiveTime'): 'Maximum time before an idle connection is validated against the server, in milliseconds.',
    ('HttpClientFetcher.mdx', 'maxRedirects'): 'Maximum number of HTTP redirects to follow per request.',
    ('HttpClientFetcher.mdx', 'userAgent'): 'The User-Agent header value sent with HTTP requests.',
    ('HttpClientFetcher.mdx', 'requestHeaders'): 'Additional HTTP headers included with every request.',
    ('HttpClientFetcher.mdx', 'headersPrefix'): 'Prefix applied to HTTP response header names when they are stored as document metadata fields.',
    ('HttpClientFetcher.mdx', 'cookieSpec'): 'Cookie handling policy to apply (e.g., standard, ignoreCookies).',
    ('HttpClientFetcher.mdx', 'validStatusCodes'): 'HTTP status codes treated as a successful document fetch.',
    ('HttpClientFetcher.mdx', 'notFoundStatusCodes'): 'HTTP status codes indicating the document does not exist (treated as a deletion signal).',
    ('HttpClientFetcher.mdx', 'trustAllSSLCertificates'): 'When enabled, SSL certificate validation is skipped. Use only in trusted environments.',
    ('HttpClientFetcher.mdx', 'sslProtocols'): 'SSL/TLS protocol versions to enable for HTTPS connections (e.g., TLSv1.2, TLSv1.3).',
    ('HttpClientFetcher.mdx', 'hstsDisabled'): 'When enabled, HTTP Strict Transport Security (HSTS) response headers are ignored.',
    ('HttpClientFetcher.mdx', 'sniDisabled'): 'When enabled, the TLS Server Name Indication (SNI) extension is not sent.',
    ('HttpClientFetcher.mdx', 'localAddress'): 'Local network address to bind outgoing connections to.',
    ('HttpClientFetcher.mdx', 'expectContinueEnabled'): 'When enabled, sends an Expect: 100-continue header before large request bodies.',
    ('HttpClientFetcher.mdx', 'etagDisabled'): 'When enabled, ETag-based conditional requests (If-None-Match) are not used.',
    ('HttpClientFetcher.mdx', 'ifModifiedSinceDisabled'): 'When enabled, If-Modified-Since conditional requests are not used.',
    ('HttpClientFetcher.mdx', 'forceContentTypeDetection'): 'When enabled, content type is detected from content regardless of the server-declared type.',
    ('HttpClientFetcher.mdx', 'forceCharsetDetection'): 'When enabled, character encoding is detected from content regardless of the declared charset.',
    ('HttpClientFetcher.mdx', 'httpMethods'): 'HTTP methods to use for metadata-only fetch vs. full document fetch.',
    ('HttpClientFetcher.mdx', 'redirectUrlProvider'): 'Plugin that resolves the final target URL after HTTP redirects.',
    # GenericRedirectUrlProvider.mdx
    ('GenericRedirectUrlProvider.mdx', 'fallbackCharset'): 'Character encoding to assume when the redirect URL has no declared encoding.',
    # Credentials.mdx
    ('Credentials.mdx', 'username'): 'The authentication username.',
    ('Credentials.mdx', 'password'): 'The authentication password. Supports encrypted values.',
    # ArchiveFetcher.mdx
    ('ArchiveFetcher.mdx', 'credentials'): 'Credentials for accessing the underlying file system that hosts the archive (e.g., FTP or SFTP).',
    # WebDriverFetcher.mdx
    ('WebDriverFetcher.mdx', 'browser'): 'Browser type to use: CHROME, FIREFOX, EDGE, or OPERA.',
    ('WebDriverFetcher.mdx', 'browserPath'): 'Path to the browser executable.',
    ('WebDriverFetcher.mdx', 'driverPath'): 'Path to the browser driver executable (e.g., chromedriver).',
    ('WebDriverFetcher.mdx', 'remoteURL'): 'URL of a remote WebDriver or Selenium Grid endpoint.',
    ('WebDriverFetcher.mdx', 'windowSize'): 'Browser window dimensions (width x height in pixels).',
    ('WebDriverFetcher.mdx', 'arguments'): 'Command-line arguments passed to the browser on launch.',
    ('WebDriverFetcher.mdx', 'capabilities'): 'Standard Selenium WebDriver capabilities as key-value pairs (e.g., for proxy, logging, or browser-specific options).',
    ('WebDriverFetcher.mdx', 'implicitlyWait'): 'Default implicit wait time for WebDriver element lookups, in milliseconds.',
    ('WebDriverFetcher.mdx', 'pageLoadTimeout'): 'Maximum time to wait for a page to fully load, in milliseconds.',
    ('WebDriverFetcher.mdx', 'scriptTimeout'): 'Maximum time to wait for a JavaScript execution to complete, in milliseconds.',
    ('WebDriverFetcher.mdx', 'threadWait'): 'Time to pause between thread operations, in milliseconds.',
    ('WebDriverFetcher.mdx', 'cleanupInterval'): 'How frequently stale browser instances are recycled, in milliseconds.',
    ('WebDriverFetcher.mdx', 'browserMaxAge'): 'Maximum lifetime of a browser instance before it is recycled.',
    ('WebDriverFetcher.mdx', 'browserMaxNavigations'): 'Maximum number of page navigations before a browser instance is recycled.',
    ('WebDriverFetcher.mdx', 'earlyPageScript'): 'JavaScript executed immediately after page navigation begins.',
    ('WebDriverFetcher.mdx', 'latePageScript'): 'JavaScript executed after the page has fully loaded.',
    ('WebDriverFetcher.mdx', 'waitForElementSelector'): 'CSS selector of an element to wait for before considering the page ready for crawling.',
    ('WebDriverFetcher.mdx', 'waitForElementTimeout'): 'Maximum time to wait for the target element to appear, in milliseconds.',
    ('WebDriverFetcher.mdx', 'waitForElementType'): 'Condition used when waiting for an element (e.g., PRESENT, VISIBLE, CLICKABLE).',
    ('WebDriverFetcher.mdx', 'httpSniffer'): 'Sniffs HTTP traffic through the browser to capture response headers and metadata.',
    ('WebDriverFetcher.mdx', 'screenshotHandler'): 'Configures automatic page screenshot capture during crawling.',
    ('WebDriverFetcher.mdx', 'useHtmlUnit'): 'When enabled, uses HtmlUnit as the browser backend instead of a real browser.',
    # HttpSniffer.mdx
    ('HttpSniffer.mdx', 'networkInterface'): 'Network interface to listen on for HTTP traffic sniffing.',
    ('HttpSniffer.mdx', 'responseTimeout'): 'Maximum time to wait for a sniffer response, in milliseconds.',
    # Regex.mdx
    ('Regex.mdx', 'canonEq'): 'Enables Unicode canonical equivalence in matching (Java Pattern.CANON_EQ flag).',
    ('Regex.mdx', 'comments'): 'Allows whitespace and # comments inside the regex pattern (Java Pattern.COMMENTS flag).',
    ('Regex.mdx', 'dotAll'): 'Makes . match newline characters as well (Java Pattern.DOTALL flag).',
    ('Regex.mdx', 'literal'): 'Treats the pattern as a literal string rather than a regular expression.',
    ('Regex.mdx', 'matchEmpty'): 'When enabled, null or empty strings are treated as a positive match.',
    ('Regex.mdx', 'multiline'): 'Makes ^ and $ match at line boundaries rather than the full string (Java Pattern.MULTILINE flag).',
    ('Regex.mdx', 'unicodeCase'): 'Enables Unicode-aware case folding (Java Pattern.UNICODE_CASE flag).',
    ('Regex.mdx', 'unicodeCharacterClass'): 'Enables Unicode character class semantics (Java Pattern.UNICODE_CHARACTER_CLASS flag).',
    ('Regex.mdx', 'unixLines'): r'Makes \n the only recognized line terminator (Java Pattern.UNIX_LINES flag).',
    ('Regex.mdx', 'ignoreDiacritic'): 'When enabled, accented characters match their unaccented equivalents.',
    # TextMatcher.mdx
    ('TextMatcher.mdx', 'ignoreDiacritic'): 'When enabled, accented characters match their unaccented equivalents.',
    ('TextMatcher.mdx', 'matchEmpty'): 'When enabled, null or empty strings are treated as a positive match.',
    ('TextMatcher.mdx', 'method'): 'Matching strategy to use: BASIC (exact), WILDCARD, CSV (comma-separated list), or REGEX.',
    ('TextMatcher.mdx', 'negateMatches'): 'When enabled, the match result is inverted — non-matching values are accepted instead.',
    ('TextMatcher.mdx', 'partial'): 'When enabled, the pattern matches anywhere within the value rather than the full value.',
    ('TextMatcher.mdx', 'replaceAll'): 'When enabled, all occurrences of the pattern are replaced, not just the first.',
    # RegexFieldValueExtractor.mdx
    ('RegexFieldValueExtractor.mdx', 'fieldGroup'): 'Index of the regex capture group whose value is used as the metadata field name.',
    ('RegexFieldValueExtractor.mdx', 'valueGroup'): 'Index of the regex capture group whose value is used as the metadata field value.',
    # If.mdx and IfNot.mdx
    ('If.mdx', 'condition'): 'The condition evaluated to decide whether to apply the associated handler(s).',
    ('If.mdx', 'else'): 'Handler(s) applied when the condition evaluates to false.',
    ('If.mdx', 'name'): 'Optional descriptive name for this conditional handler, shown in logs.',
    ('IfNot.mdx', 'condition'): 'The condition evaluated to decide whether to apply the associated handler(s).',
    ('IfNot.mdx', 'else'): 'Handler(s) applied when the condition evaluates to false.',
    ('IfNot.mdx', 'name'): 'Optional descriptive name for this conditional handler, shown in logs.',
    # ScriptCondition.mdx and ScriptTransformer.mdx
    ('ScriptCondition.mdx', 'engineName'): 'The scripting engine to use (e.g., JavaScript, groovy).',
    ('ScriptTransformer.mdx', 'engineName'): 'The scripting engine to use (e.g., JavaScript, groovy).',
    # SplitOperation.mdx
    ('SplitOperation.mdx', 'separator'): 'The literal string used to split field values.',
    ('SplitOperation.mdx', 'separatorRegex'): 'A regex pattern used to split field values.',
    # MergeOperation.mdx
    ('MergeOperation.mdx', 'singleValue'): 'When enabled, all merged values are joined into a single string.',
    ('MergeOperation.mdx', 'singleValueSeparator'): 'String used to join values when singleValue is enabled.',
    ('MergeOperation.mdx', 'deleteFromFields'): 'When enabled, source field values are removed after merging.',
    # ReplaceOperation.mdx
    ('ReplaceOperation.mdx', 'toValue'): 'The replacement value or pattern. Supports capture group references such as $1, $2.',
    ('ReplaceOperation.mdx', 'discardUnchanged'): 'When enabled, fields whose values were not changed by the replacement are dropped from the result.',
    # HierarchyOperation.mdx
    ('HierarchyOperation.mdx', 'keepEmptySegments'): 'When enabled, empty segments in the path hierarchy are retained.',
    # DomOperation.mdx
    ('DomOperation.mdx', 'defaultValue'): 'Value to use when the selector matches no DOM elements.',
    ('DomOperation.mdx', 'matchBlanks'): 'When enabled, elements with blank (empty or whitespace-only) values are also matched.',
    ('DomOperation.mdx', 'delete'): 'When enabled, DOM elements matched by the selector are removed from the field.',
    # StripBetweenOperation.mdx and TextBetweenOperation.mdx
    ('StripBetweenOperation.mdx', 'endMatcher'): 'Pattern that marks the end boundary of the region to strip or extract.',
    ('TextBetweenOperation.mdx', 'endMatcher'): 'Pattern that marks the end boundary of the region to strip or extract.',
    # Reject.mdx
    ('Reject.mdx', 'message'): 'The message logged when a document is rejected by this handler.',
    # BlankCondition.mdx
    ('BlankCondition.mdx', 'matchAnyBlank'): 'When enabled, the condition is true if any matched field contains a blank value.',
    # NumericValueMatcher.mdx
    ('NumericValueMatcher.mdx', 'number'): 'The numeric value to compare against.',
    # DateValueMatcher.mdx
    ('DateValueMatcher.mdx', 'date'): 'The date value to compare against.',
    ('DateValueMatcher.mdx', 'zoneId'): 'The time zone used to interpret the configured date value.',
    # DateCondition.mdx
    ('DateCondition.mdx', 'format'): 'Date format pattern for parsing the date value (e.g., yyyy-MM-dd).',
    ('DateCondition.mdx', 'docZoneId'): 'Time zone applied to the document date when comparing.',
    # LanguageTransformer.mdx
    ('LanguageTransformer.mdx', 'languages'): 'Language codes to detect or restrict processing to (e.g., en, fr).',
    # TitleGeneratorTransformer.mdx
    ('TitleGeneratorTransformer.mdx', 'detectHeadingMinLength'): 'Minimum character length a text block must have to be considered a heading candidate.',
    # EmbeddedConfig.mdx
    ('EmbeddedConfig.mdx', 'maxEmbeddedDepth'): 'Maximum nesting depth for extracting embedded documents.',
    ('EmbeddedConfig.mdx', 'skipEmbeddedContentTypes'): 'Content types of embedded files to skip during extraction.',
    ('EmbeddedConfig.mdx', 'skipEmbeddedOfContentTypes'): 'Parent document content types for which embedded extraction is entirely skipped.',
    ('EmbeddedConfig.mdx', 'splitContentTypes'): 'Content types of embedded documents that are extracted as separate crawl records.',
    # TranslatorSplitter.mdx
    ('TranslatorSplitter.mdx', 'api'): 'Translation API to use: microsoft, google, yandex, or moses.',
    ('TranslatorSplitter.mdx', 'clientId'): 'Microsoft Azure client ID for the translation API.',
    ('TranslatorSplitter.mdx', 'clientSecret'): 'Microsoft Azure client secret for the translation API.',
    ('TranslatorSplitter.mdx', 'userKey'): 'API key for Google or Yandex translation services.',
    ('TranslatorSplitter.mdx', 'smtPath'): 'Path to the Moses SMT (Statistical Machine Translation) model directory.',
    ('TranslatorSplitter.mdx', 'scriptPath'): 'Path to the script used with the Moses translation engine.',
    ('TranslatorSplitter.mdx', 'sourceLanguage'): 'Language code of the source documents (e.g., en).',
    ('TranslatorSplitter.mdx', 'sourceLanguageField'): 'Metadata field containing the source language code.',
    ('TranslatorSplitter.mdx', 'targetLanguages'): 'One or more target language codes for translation output.',
    ('TranslatorSplitter.mdx', 'ignoreNonTranslatedFields'): 'When enabled, fields that were not translated are omitted from the output document.',
    # MinFrequency.mdx
    ('MinFrequency.mdx', 'applyTo'): 'Whether the frequency applies to CONTENT_TYPE or REFERENCE.',
    ('MinFrequency.mdx', 'matcher'): 'Pattern matching the content type or reference URL that this frequency rule applies to.',
    ('MinFrequency.mdx', 'value'): 'Minimum recrawl frequency — a duration string (e.g., monthly) or a value in milliseconds.',
    # GenericRecrawlableResolver.mdx
    ('GenericRecrawlableResolver.mdx', 'sitemapSupport'): 'How sitemap changefreq directives influence recrawl decisions: FIRST, LAST, MIN, or MAX (relative to other rules).',
    ('GenericRecrawlableResolver.mdx', 'minFrequencies'): 'Rules defining minimum recrawl frequencies per content type or reference pattern.',
    # ExtractionPattern.mdx
    ('ExtractionPattern.mdx', 'match'): 'Regex pattern used to match a link or value within document content.',
    ('ExtractionPattern.mdx', 'replace'): 'Replacement expression forming the extracted value. Supports regex capture group references (e.g., $1).',
    # GenericSpoiledReferenceStrategizer.mdx
    ('GenericSpoiledReferenceStrategizer.mdx', 'fallbackStrategy'): 'Strategy applied to spoiled references not matched by any specific mapping rule.',
    ('GenericSpoiledReferenceStrategizer.mdx', 'mappings'): 'Strategy mappings for specific content types or reference patterns.',
    # HtmlLinkExtractor.mdx
    ('HtmlLinkExtractor.mdx', 'commentsEnabled'): 'When enabled, links found inside HTML comments are also extracted.',
    ('HtmlLinkExtractor.mdx', 'maxURLLength'): 'Maximum URL length accepted. URLs longer than this are ignored.',
    ('HtmlLinkExtractor.mdx', 'tagAttribs'): 'HTML tag and attribute combinations from which links are extracted.',
    ('HtmlLinkExtractor.mdx', 'extractBetweens'): 'Text regions (delimited by start/end patterns) from which links are extracted.',
    ('HtmlLinkExtractor.mdx', 'noExtractBetweens'): 'Text regions excluded from link extraction.',
    ('HtmlLinkExtractor.mdx', 'extractSelectors'): 'CSS selectors identifying elements from which links are extracted.',
    ('HtmlLinkExtractor.mdx', 'noExtractSelectors'): 'CSS selectors identifying elements excluded from link extraction.',
    # RegexLinkExtractor.mdx
    ('RegexLinkExtractor.mdx', 'maxUrlLength'): 'Maximum URL length accepted. URLs longer than this are ignored.',
    ('RegexLinkExtractor.mdx', 'patterns'): 'Regex patterns used to extract links from document content.',
    # DomLinkExtractor.mdx
    ('DomLinkExtractor.mdx', 'extractSelectors'): 'CSS selectors identifying elements from which links are extracted.',
    ('DomLinkExtractor.mdx', 'noExtractSelectors'): 'CSS selectors identifying elements excluded from link extraction.',
    ('DomLinkExtractor.mdx', 'linkSelectors'): 'CSS selectors that identify link elements (e.g., a[href]).',
    # FeaturedImageResolver.mdx
    ('FeaturedImageResolver.mdx', 'domSelector'): 'CSS selector identifying image elements on the page.',
    ('FeaturedImageResolver.mdx', 'pageContentTypePattern'): 'Content type pattern of pages to scan for featured images.',
    ('FeaturedImageResolver.mdx', 'imageCacheDir'): 'Directory used to cache downloaded images during resolution.',
    ('FeaturedImageResolver.mdx', 'imageCacheSize'): 'Maximum number of images held in the resolution cache.',
    ('FeaturedImageResolver.mdx', 'imageFormat'): 'Image format used when saving the featured image (e.g., png, jpg).',
    ('FeaturedImageResolver.mdx', 'minDimensions'): 'Minimum image dimensions (width x height) for an image to be considered a candidate.',
    ('FeaturedImageResolver.mdx', 'scaleDimensions'): 'Dimensions to scale the featured image to before storing.',
    ('FeaturedImageResolver.mdx', 'scaleQuality'): 'Quality setting for the scaled image: LOW, MEDIUM, HIGH, or MAX.',
    ('FeaturedImageResolver.mdx', 'scaleStretch'): 'When enabled, images are stretched to fit the exact target dimensions rather than scaled proportionally.',
    ('FeaturedImageResolver.mdx', 'largest'): 'When enabled, the largest qualifying image on the page is preferred as the featured image.',
    ('FeaturedImageResolver.mdx', 'storageDiskDir'): 'Root directory where featured images are saved on disk.',
    ('FeaturedImageResolver.mdx', 'storageDiskField'): 'Metadata field storing the disk path of the saved featured image.',
    ('FeaturedImageResolver.mdx', 'storageDiskStructure'): 'Directory layout for disk storage: URL2PATH (mirror URL structure), DATE, or DATETIME.',
    ('FeaturedImageResolver.mdx', 'storageInlineField'): 'Metadata field storing the featured image as a Base64 inline data string.',
    ('FeaturedImageResolver.mdx', 'storageUrlField'): 'Metadata field storing the absolute URL of the featured image.',
    ('FeaturedImageResolver.mdx', 'storages'): 'One or more storage strategies for the featured image: URL (store image URL), INLINE (store as Base64), or DISK (save to file system).',
    # DocImageHandler.mdx
    ('DocImageHandler.mdx', 'imageFormat'): 'Image format used when saving extracted images (e.g., png, jpg).',
    ('DocImageHandler.mdx', 'targetDir'): 'Directory where extracted images are saved.',
    ('DocImageHandler.mdx', 'targetDirField'): 'Metadata field whose value overrides the target directory path.',
    ('DocImageHandler.mdx', 'targetDirStructure'): 'Directory layout for saved images: URL2PATH, DATE, or DATETIME.',
    ('DocImageHandler.mdx', 'targetMetaField'): 'Metadata field where the path of the saved image is stored.',
    ('DocImageHandler.mdx', 'targets'): 'Where to store extracted images: METADATA (store inline in a metadata field), DIRECTORY (save to disk), or both.',
    # HazelcastClusterConnector.mdx and HazelcastClusterConnectorConfig.mdx
    ('HazelcastClusterConnector.mdx', 'clusterName'): 'Name of the Hazelcast cluster to join or form.',
    ('HazelcastClusterConnector.mdx', 'configurer'): 'Custom Hazelcast configuration provider for advanced cluster tuning.',
    ('HazelcastClusterConnector.mdx', 'nodeExpiryTimeout'): 'Time before an inactive cluster node is considered expired and removed.',
    ('HazelcastClusterConnectorConfig.mdx', 'clusterName'): 'Name of the Hazelcast cluster to join or form.',
    ('HazelcastClusterConnectorConfig.mdx', 'configurer'): 'Custom Hazelcast configuration provider for advanced cluster tuning.',
    ('HazelcastClusterConnectorConfig.mdx', 'nodeExpiryTimeout'): 'Time before an inactive cluster node is considered expired and removed.',
    # JdbcHazelcastConfigurer.mdx
    ('JdbcHazelcastConfigurer.mdx', 'jdbcDriver'): 'Fully qualified JDBC driver class name (e.g., org.h2.Driver).',
    ('JdbcHazelcastConfigurer.mdx', 'jdbcUrl'): 'JDBC connection URL for the backing database.',
    ('JdbcHazelcastConfigurer.mdx', 'jdbcUsername'): 'JDBC authentication username.',
    ('JdbcHazelcastConfigurer.mdx', 'jdbcPassword'): 'JDBC authentication password.',
    ('JdbcHazelcastConfigurer.mdx', 'sqlMerge'): 'Custom SQL MERGE/UPSERT statement. Defaults to H2 MERGE syntax.',
    ('JdbcHazelcastConfigurer.mdx', 'initialLoadMode'): 'How existing data is loaded from the database at startup: LAZY (on demand) or EAGER (all at once).',
    ('JdbcHazelcastConfigurer.mdx', 'columnKeyType'): 'JDBC SQL type of the key column (e.g., VARCHAR).',
    ('JdbcHazelcastConfigurer.mdx', 'columnValueType'): 'JDBC SQL type of the value column (e.g., BLOB).',
    ('JdbcHazelcastConfigurer.mdx', 'maxPoolSize'): 'Maximum JDBC connection pool size.',
    ('JdbcHazelcastConfigurer.mdx', 'writeDelaySeconds'): 'Seconds to delay writes to the database, enabling write-behind buffering.',
    ('JdbcHazelcastConfigurer.mdx', 'jetEnabled'): 'When enabled, Hazelcast Jet stream-processing engine is activated.',
    ('JdbcHazelcastConfigurer.mdx', 'autoDiscoveryEnabled'): 'When enabled, Hazelcast automatically discovers cluster members on the network.',
    ('JdbcHazelcastConfigurer.mdx', 'tcpMembers'): 'Explicit list of TCP/IP cluster member addresses (used when auto-discovery is disabled).',
    ('JdbcHazelcastConfigurer.mdx', 'backupCount'): 'Number of synchronous backup copies maintained for each distributed map entry.',
    ('JdbcHazelcastConfigurer.mdx', 'hazelcastProperties'): 'Additional Hazelcast properties passed directly to the cluster configuration.',
    # FsQueue.mdx
    ('FsQueue.mdx', 'batchSize'): 'Number of documents batched together before a commit is sent to the target system.',
    ('FsQueue.mdx', 'maxPerFolder'): 'Maximum number of batch files stored per queue subdirectory.',
    ('FsQueue.mdx', 'commitLeftoversOnInit'): 'When enabled, uncommitted documents left from a previous run are committed on startup.',
    ('FsQueue.mdx', 'onCommitFailure'): 'Controls retry and error-handling behavior when a commit fails (max retries, retry delay, batch splitting, and whether to ignore errors).',
    # Neo4jCommitter.mdx and SqlCommitter.mdx
    ('Neo4jCommitter.mdx', 'multiValuesJoiner'): 'String used to join multiple field values into a single string when the target system does not support multi-value fields.',
    ('SqlCommitter.mdx', 'multiValuesJoiner'): 'String used to join multiple field values into a single string when the target system does not support multi-value fields.',
    # DeleteRejectedEventListener.mdx
    ('DeleteRejectedEventListener.mdx', 'eventMatcher'): 'Matches crawl event types that identify rejected documents to be deleted.',
    # StopCrawlerOnMaxEventListener.mdx
    ('StopCrawlerOnMaxEventListener.mdx', 'eventMatcher'): 'Matches crawl event types whose occurrences are counted toward the maximum.',
    ('StopCrawlerOnMaxEventListener.mdx', 'maximum'): 'Maximum event count — when reached, the crawler stops gracefully.',
    ('StopCrawlerOnMaxEventListener.mdx', 'onMultiple'): 'When tracking multiple event types: ANY (stop when any type reaches max), ALL (stop when all types reach max), or SUM (stop when their combined total reaches max).',
    # StdRobotsTxtFilter.mdx
    ('StdRobotsTxtFilter.mdx', 'onMatch'): "Whether references matching the robots.txt rule are INCLUDE'd (allowed) or EXCLUDE'd (blocked).",
    # UrlStatusCrawlerEventListener.mdx
    ('UrlStatusCrawlerEventListener.mdx', 'outputDir'): 'Directory where the URL status report file is written.',
    ('UrlStatusCrawlerEventListener.mdx', 'statusCodes'): 'HTTP status codes to include in the report. When empty, all non-successful codes are reported.',
    ('UrlStatusCrawlerEventListener.mdx', 'timestamped'): 'When enabled, a timestamp is appended to the report file name to avoid overwriting previous reports.',
    # GenericSitemapLocator.mdx
    ('GenericSitemapLocator.mdx', 'paths'): 'URL paths to check for sitemap files on each crawled host (e.g., /sitemap.xml).',
    ('GenericSitemapLocator.mdx', 'robotsTxtSitemapDisabled'): 'When enabled, sitemap URLs referenced in robots.txt are ignored.',
    # GenericSitemapResolver.mdx
    ('GenericSitemapResolver.mdx', 'lenient'): 'When enabled, sitemap parsing errors are tolerated and the crawler continues with valid entries.',
    # StandardRobotsMetaProvider.mdx
    ('StandardRobotsMetaProvider.mdx', 'headersPrefix'): 'Prefix for HTTP response header names that carry robot directives (e.g., X-Robots-Tag).',
    # OcrConfig.mdx
    ('OcrConfig.mdx', 'applyRotation'): 'When enabled, attempts to automatically correct document rotation before OCR processing.',
    ('OcrConfig.mdx', 'colorSpace'): 'Color space used for image preprocessing before OCR (e.g., GRAY).',
    ('OcrConfig.mdx', 'density'): 'Image resolution in DPI used for preprocessing before OCR.',
    ('OcrConfig.mdx', 'depth'): 'Color bit depth used for image preprocessing before OCR.',
    ('OcrConfig.mdx', 'disabled'): 'When enabled, OCR processing is skipped entirely.',
    ('OcrConfig.mdx', 'enableImagePreprocessing'): 'When enabled, images are preprocessed (e.g., deskewed, resized) before OCR.',
    ('OcrConfig.mdx', 'filter'): 'Image filter applied to preprocessed images before OCR (e.g., Triangle).',
    ('OcrConfig.mdx', 'imageMagickPath'): 'Path to the ImageMagick executable used for image preprocessing.',
    ('OcrConfig.mdx', 'language'): 'Tesseract language code(s) for OCR recognition (e.g., eng, fra). Multiple codes can be combined with +.',
    ('OcrConfig.mdx', 'maxFileSizeToOcr'): 'Maximum document file size eligible for OCR processing.',
    ('OcrConfig.mdx', 'minFileSizeToOcr'): 'Minimum document file size eligible for OCR processing.',
    ('OcrConfig.mdx', 'pageSegMode'): 'Tesseract page segmentation mode (0-13). Default 3 uses auto-detection.',
    ('OcrConfig.mdx', 'pageSeparator'): 'Text inserted between OCR output of successive pages in a multi-page document.',
    ('OcrConfig.mdx', 'preserveInterwordSpacing'): 'When enabled, spacing between words is preserved in the OCR output.',
    ('OcrConfig.mdx', 'resize'): 'Percentage to resize images by before OCR (e.g., 200 to double the image size).',
    ('OcrConfig.mdx', 'skipOcr'): 'Field matcher — documents whose matching fields satisfy this matcher are skipped by OCR.',
    ('OcrConfig.mdx', 'tessdataPath'): 'Path to the Tesseract tessdata directory containing language data files.',
    ('OcrConfig.mdx', 'tesseractPath'): 'Path to the Tesseract executable.',
    ('OcrConfig.mdx', 'timeoutSeconds'): 'Maximum time in seconds allowed for an OCR operation before it is aborted.',
    # Crop.mdx
    ('Crop.mdx', 'x'): 'X coordinate (in pixels) of the top-left corner of the crop rectangle.',
    ('Crop.mdx', 'y'): 'Y coordinate (in pixels) of the top-left corner of the crop rectangle.',
    ('Crop.mdx', 'height'): 'Height of the crop rectangle in pixels.',
    # Scale.mdx
    ('Scale.mdx', 'height'): 'Target height in pixels. When set together with width, the image is scaled to fit.',
    ('Scale.mdx', 'factor'): 'Scaling factor as a decimal (e.g., 0.5 to halve the size, 2.0 to double it).',
    ('Scale.mdx', 'stretch'): 'When enabled, the image is stretched to the exact target dimensions rather than scaled proportionally.',
    # ImageTransformer.mdx
    ('ImageTransformer.mdx', 'targetFormat'): 'Output image format after transformation (e.g., png, jpg, gif).',
    # SaveDocumentTransformer.mdx
    ('SaveDocumentTransformer.mdx', 'defaultFileName'): 'File name used when the document reference cannot be mapped to a valid file name.',
    ('SaveDocumentTransformer.mdx', 'dirSplitPattern'): 'Regex pattern used to split the document reference into subdirectory path segments.',
    ('SaveDocumentTransformer.mdx', 'escape'): 'Characters to escape or replace in generated file and directory names.',
    ('SaveDocumentTransformer.mdx', 'maxPathLength'): 'Maximum allowed file path length. Longer paths are truncated.',
    ('SaveDocumentTransformer.mdx', 'pathToField'): 'Metadata field where the saved file path is stored after the document is written.',
    ('SaveDocumentTransformer.mdx', 'saveDir'): 'Root directory where documents are saved.',
    # GrobidConfig.mdx
    ('GrobidConfig.mdx', 'enabled'): 'When enabled, Grobid-based structured parsing of scientific documents is activated.',
    ('GrobidConfig.mdx', 'serviceUrl'): 'URL of the Grobid REST API endpoint.',
    # TikaParser.mdx
    ('TikaParser.mdx', 'tikaConfigFile'): 'Path to a custom Apache Tika configuration XML file.',
    # DefaultParser.mdx
    ('DefaultParser.mdx', 'grobid'): 'Grobid configuration for structured parsing of scientific documents (PDFs, articles).',
    # ImporterConfig.mdx
    ('ImporterConfig.mdx', 'maxMemoryInstance'): 'Maximum memory a single document stream instance may use before spilling to disk.',
    ('ImporterConfig.mdx', 'maxMemoryPool'): 'Maximum total memory used across all document stream instances.',
    ('ImporterConfig.mdx', 'tempDir'): 'Directory used for temporary files during import processing.',
}

TODO_TEXT = 'TODO: Add documentation for this property.'

def process_file(filepath):
    basename = os.path.basename(filepath)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    if TODO_TEXT not in content:
        return False, [], []

    lines = content.split('\n')
    new_lines = []
    i = 0
    replacements_made = []
    not_in_table = []
    current_prop = None

    while i < len(lines):
        line = lines[i]
        # Track current property heading (### propName, not #### or deeper)
        heading_match = re.match(r'^### (\S+)\s*$', line)
        if heading_match:
            current_prop = heading_match.group(1)

        # Check if this line is the TODO line
        if line.strip() == TODO_TEXT and current_prop is not None:
            key = (basename, current_prop)
            if key in lookup:
                new_lines.append(lookup[key])
                replacements_made.append(current_prop)
                i += 1
                continue
            else:
                not_in_table.append(current_prop)

        new_lines.append(line)
        i += 1

    new_content = '\n'.join(new_lines)
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True, replacements_made, not_in_table
    return False, replacements_made, not_in_table


def main():
    base_dir = r'C:\git\norconex\crawlers-v4\docs\reference-source'
    files_modified = 0
    all_not_in_table = []

    for root, dirs, files in os.walk(base_dir):
        for fname in sorted(files):
            if fname.endswith('.mdx'):
                fpath = os.path.join(root, fname)
                modified, replacements, not_in_table = process_file(fpath)
                if modified:
                    files_modified += 1
                    print(f'MODIFIED: {fpath}')
                    for prop in replacements:
                        print(f'  + replaced: {prop}')
                if not_in_table:
                    for prop in not_in_table:
                        all_not_in_table.append((fname, prop))
                        print(f'  NOT IN TABLE: {fname} / {prop}')

    print(f'\nTotal files modified: {files_modified}')
    if all_not_in_table:
        print(f'\nTODOs not in lookup table ({len(all_not_in_table)}):')
        for fname, prop in all_not_in_table:
            print(f'  {fname}: {prop}')
    else:
        print('\nAll TODOs were covered by the lookup table.')


if __name__ == '__main__':
    main()
