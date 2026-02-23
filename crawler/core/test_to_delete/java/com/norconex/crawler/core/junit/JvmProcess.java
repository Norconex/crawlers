package com.norconex.crawler.core.junit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.commons.lang3.SystemUtils;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple process creator for a cluster node.
 */
@Builder
@Slf4j
@Deprecated
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
        var classpath = buildClasspath();

        List<String> command = new ArrayList<>();
        command.add(SystemUtils.JAVA_HOME + "/bin/java");
        jvmArgs.forEach(command::add);
        command.add("-Dfile.encoding=UTF8");
        command.add("-Djava.net.preferIPv4Stack=true");
        command.add("-Djava.net.preferIPv6Addresses=false");
        command.add("-Djava.net.disableIPv6=true");
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass.getName());
        appArgs.forEach(command::add);

        // Calculate total command length
        var fullCommand = String.join(" ", command);
        var commandLength = fullCommand.length();

        LOG.info("JVM launch command length: {} characters", commandLength);
        if (commandLength > 8191) {
            LOG.warn(
                    """
                        Command line length ({}) exceeds Windows cmd.exe \
                        limit (8191). This may cause process startup \
                        to fail.""",
                    commandLength);
        }
        LOG.info("JVM launch command: {}", fullCommand);
        LOG.debug("Classpath has {} entries",
                classpath.split(File.pathSeparator).length);

        var pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());

        // Redirect STDOUT and STDERR to separate log files
        try {
            Files.createDirectories(workDir);
            pb.redirectOutput(workDir.resolve(STDOUT_FILE_NAME).toFile());
            pb.redirectError(workDir.resolve(STDERR_FILE_NAME).toFile());

            var process = pb.start();

            // Give the process a moment to fail if there's an immediate error
            try {
                Thread.sleep(100); //NOSONAR
                if (!process.isAlive()) {
                    var exitValue = process.exitValue();
                    LOG.error(
                            "Child JVM process exited immediately with "
                                    + "code {}. Check {} and {} for details.",
                            exitValue,
                            STDOUT_FILE_NAME,
                            STDERR_FILE_NAME);

                    // Try to read error output
                    var stderrFile = workDir.resolve(STDERR_FILE_NAME);
                    if (Files.exists(stderrFile)) {
                        var stderr = Files.readString(stderrFile);
                        if (!stderr.isEmpty()) {
                            LOG.error("Child JVM stderr output:\n{}", stderr);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while checking process status");
            }

            return process;
        } catch (IOException e) {
            LOG.error(
                    "Failed to start child JVM process. Command length: {}",
                    commandLength,
                    e);
            throw new UncheckedIOException(e);
        }
    }

    private String buildClasspath() {
        var classpath = SystemUtils.JAVA_CLASS_PATH;

        // Ensure test-classes directory is included in classpath
        try {
            var testClassLocation = JvmProcess.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();

            // Only add if not already in classpath
            if (!classpath.contains(testClassLocation)) {
                classpath =
                        testClassLocation + File.pathSeparator + classpath;
            }
        } catch (Exception e) {
            LOG.warn(
                    "Failed to add test-classes to classpath, "
                            + "using current classpath only",
                    e);
        }

        // On Windows, if classpath is too long, create a manifest JAR
        // to work around command line length limits
        if (SystemUtils.IS_OS_WINDOWS &&
                estimateCommandLength(classpath) > 8000) {
            try {
                LOG.info(
                        "Classpath too long for Windows command line, "
                                + "creating manifest JAR");
                return createManifestJar(classpath).toString();
            } catch (IOException e) {
                LOG.warn(
                        "Failed to create manifest JAR, "
                                + "using original classpath",
                        e);
            }
        }

        return classpath;
    }

    /**
     * Estimates the total command line length including java path,
     * JVM args, classpath, main class, and app args.
     */
    private int estimateCommandLength(String classpath) {
        return (SystemUtils.JAVA_HOME + "/bin/java").length()
                + jvmArgs.stream().mapToInt(String::length).sum()
                + appArgs.stream().mapToInt(String::length).sum()
                + mainClass.getName().length()
                + classpath.length()
                + 100;
    }

    /**
     * Creates a temporary manifest-only JAR file that contains
     * the classpath in its manifest. This works around Windows
     * command line length limitations.
     */
    private Path createManifestJar(String classpath) throws IOException {
        var manifestJar =
                workDir.resolve("classpath-manifest.jar").toAbsolutePath();

        // Convert classpath entries to file:/// URLs for manifest
        var entries = classpath.split(File.pathSeparator);
        var manifestClasspath = new StringBuilder();

        for (var i = 0; i < entries.length; i++) {
            var entry = new File(entries[i]).toURI().toURL().toString();
            manifestClasspath.append(entry);
            if (i < entries.length - 1) {
                manifestClasspath.append(" ");
            }
        }

        // Create manifest with Class-Path attribute
        var manifest = new Manifest();
        var attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(
                Attributes.Name.CLASS_PATH,
                manifestClasspath.toString());

        // Write manifest JAR
        Files.createDirectories(workDir);
        try (var jos = new JarOutputStream(
                new FileOutputStream(manifestJar.toFile()), manifest)) {
            // No entries needed, just the manifest
        }

        LOG.debug("Created manifest JAR: {}", manifestJar);
        return manifestJar;
    }
}
