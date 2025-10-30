package com.norconex.crawler.core.cluster2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.healthmarketscience.jackcess.RuntimeIOException;
import com.norconex.crawler.core.junit.WithLogLevel;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

/**
 * Simple process creator, tailored to this project.
 */
@Builder
public class JvmProcess {

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
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass.getName());
        appArgs.forEach(command::add);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        // Log goes into process's own directory
        pb.redirectOutput(workDir.resolve("process.log").toFile());
        try {
            return pb.start();
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
