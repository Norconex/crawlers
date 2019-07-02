/* Copyright 2014-2019 Norconex Inc.
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
package com.norconex.collector.http.crawler;

public abstract class AbstractHttpTest {

//    protected Path getTempFolder() {
//        return null;
//    }
//
//    protected String getBaseUrl() {
//        return null;
//    }
//    protected String newUrl(String urlPath) {
//        return null;
//    }
//    protected File getCommitterDir(HttpCrawler crawler) {
//        FileSystemCommitter committer = (FileSystemCommitter)
//                crawler.getCrawlerConfig().getCommitter();
//        File dir = new File(committer.getDirectory());
//        if (!dir.exists()) {
//            dir.mkdirs();
//        }
//        return dir;
//    }
//
//    protected List<HttpDocument> getCommitedDocuments(HttpCrawler crawler)
//            throws IOException {
//        File addDir =  getCommitterDir(crawler);
//        Collection<File> files = FileUtils.listFiles(addDir, null, true);
//        List<HttpDocument> docs = new ArrayList<>();
//        for (File file : files) {
//            if (file.isDirectory() || !file.getName().endsWith(
//                    FileSystemCommitter.EXTENSION_CONTENT)) {
//                continue;
//            }
//            HttpMetadata meta = new HttpMetadata(file.getAbsolutePath());
//            String basePath = StringUtils.removeEnd(
//                    file.getAbsolutePath(),
//                    FileSystemCommitter.EXTENSION_CONTENT);
//            try (InputStream is = FileUtils.openInputStream(
//                    new File(basePath + ".meta"))) {
//                meta.loadFromProperties(is);
//            }
//            String reference = FileUtils.readFileToString(
//                    new File(basePath + ".ref"), StandardCharsets.UTF_8);
//
//            try (CachedInputStream is =
//                    crawler.getStreamFactory().newInputStream(file)) {
//                HttpDocument doc = new HttpDocument(reference, is);
//                // remove previous reference to avoid duplicates
//                doc.getMetadata().remove(HttpMetadata.COLLECTOR_URL);
//                doc.getMetadata().loadFromMap(meta);
//                docs.add(doc);
//
//            }
//        }
//        return docs;
//    }

//    protected HttpCrawlerConfig getCrawlerConfig(
//            HttpCollector collector, int cfgIndex) {
//        return (HttpCrawlerConfig) collector.getCollectorConfig()
//                .getCrawlerConfigs().get(cfgIndex);
//    }
//
//    protected HttpCollector newHttpCollector1Crawler(String... startURLs)
//            throws IOException {
//
//        //--- Collector ---
//        HttpCollectorConfig colConfig = new HttpCollectorConfig();
//        colConfig.setId("Unit Test HTTP Collector instance "
//                + UUID.randomUUID());
////        colConfig.setProgressDir(progressDir.getAbsolutePath());
////        colConfig.setLogsDir(logsDir.getAbsolutePath());
//        HttpCollector collector = new HttpCollector(colConfig);
//
////        File progressDir = tempFolder.newFolder("progress" + UUID.randomUUID());
////        File logsDir = tempFolder.newFolder("logs" + UUID.randomUUID());
//        Path workdir = tempFolder.resolve("workdir" + UUID.randomUUID());
//        Path committerDir = tempFolder.resolve(
//                "committedFiles_" + UUID.randomUUID());
//        colConfig.setWorkDir(workdir);
//
//        //--- Committer ---
//        //ICommitter committer = new NilCommitter();
//        FileSystemCommitter committer = new FileSystemCommitter();
//        committer.setDirectory(committerDir.toAbsolutePath().toString());
//
//        //--- Crawler ---
//        HttpCrawlerConfig httpConfig = new HttpCrawlerConfig();
//        httpConfig.setId("Unit Test HTTP Crawler instance "
//                + UUID.randomUUID());
//        String[] urls = new String[startURLs.length];
//        for (int i = 0; i < startURLs.length; i++) {
//            urls[i] = getBaseUrl() + startURLs[i];
//        }
//        httpConfig.setStartURLs(urls);
//        httpConfig.setNumThreads(1);
//        GenericDelayResolver resolver = new GenericDelayResolver();
//        resolver.setDefaultDelay(0);
//        httpConfig.setDelayResolver(resolver);
//        httpConfig.setIgnoreRobotsMeta(true);
//        httpConfig.setIgnoreSitemap(true);
//        httpConfig.setCommitter(committer);
//
//        colConfig.setCrawlerConfigs(httpConfig);
////        HttpCrawler crawler = new HttpCrawler(httpConfig, collector);
////
////        collector.setCrawlers(Arrays.asList(crawler));
//        return collector;
//    }
}
