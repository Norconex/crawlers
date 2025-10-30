package com.norconex.crawler.core.cluster2;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.junit.WithLogLevel;
import com.norconex.crawler.core.mocks.crawler.MockCrawlDriverFactory;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * Arguments and other settings we want to pass to each crawler launched.
 */
@Data
@Accessors(chain = true)
public class CrawlerClusterLaunchParams {

    private final List<String> appArgs = new ArrayList<>();
    private final List<String> jvmArgs = new ArrayList<>();
    private final List<WithLogLevel> logLevels = new ArrayList<>();

    // private Consumer<SharedClusterClient> preLaunch;
    // private Consumer<SharedClusterClient> postLaunch;
    private Class<? extends Supplier<CrawlDriver>> driverSupplierClass =
            MockCrawlDriverFactory.class;
    private CrawlConfig crawlConfig;
    private Path clusterRootDir;

    public void toFile(@NonNull Path file) {
        try (Writer w = Files.newBufferedWriter(
                file, StandardOpenOption.CREATE_NEW)) {
            BeanMapper.DEFAULT.write(this, w, Format.YAML);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        try (Writer w = new StringWriter()) {
            BeanMapper.DEFAULT.write(this, w, Format.YAML);
            return w.toString();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static CrawlerClusterLaunchParams fromFile(@NonNull Path file) {
        try (Reader r = Files.newBufferedReader(file)) {
            return BeanMapper.DEFAULT.read(
                    CrawlerClusterLaunchParams.class, r, Format.YAML);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static CrawlerClusterLaunchParams fromString(@NonNull String str) {
        try (Reader r = new StringReader(str)) {
            return BeanMapper.DEFAULT.read(
                    CrawlerClusterLaunchParams.class, r, Format.YAML);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
