package com.norconex.crawler.core.junit.cluster.node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.healthmarketscience.jackcess.RuntimeIOException;
import com.norconex.crawler.core.junit.WithLogLevel;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

/**
 * Simple process creator for a cluster node.
 */
@Builder
public class JvmProcess {

    //TODO merge with CrawlerNodeLaucher?

    public static final String STDOUT_FILE_NAME = "stdout.log";
    public static final String STDERR_FILE_NAME = "stderr.log";

    @Singular
    private final List<String> appArgs;
    @Singular
    private final List<String> jvmArgs;
    @Singular
    private final List<WithLogLevel> logLevels;
    @NonNull
    private final Class<?> mainClass;
    @NonNull
    private final Path workDir;

    public Process start() {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + "/bin/java");
        jvmArgs.forEach(command::add);
        command.add("-Dfile.encoding=UTF8");
        command.add("-Djava.net.preferIPv4Stack=true");
        command.add("-Djava.net.preferIPv6Addresses=false");
        command.add("-Djava.net.disableIPv6=true");
        command.add("-cp");
        command.add(buildClasspath());
        command.add(mainClass.getName());
        appArgs.forEach(command::add);

        var pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());

        // Redirect STDOUT and STDERR to separate log files
        try {
            Files.createDirectories(workDir);
            pb.redirectOutput(workDir.resolve(STDOUT_FILE_NAME).toFile());
            pb.redirectError(workDir.resolve(STDERR_FILE_NAME).toFile());
            return pb.start();
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private String buildClasspath() {
        var classpath = System.getProperty("java.class.path");

        // Ensure test-classes directory is included in classpath
        // We need to find the absolute path to test-classes by using
        // the location of this class itself
        var testClassLocation = JvmProcess.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();

        // Only add if not already in classpath
        if (!classpath.contains(testClassLocation)) {
            classpath = testClassLocation + System.getProperty(
            "path.separator") + classpath;
        }

        return classpath;
    }
}
