package com.norconex.crawler.core._DELETE.crawler;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * Utility class for recursively scanning a directory and building a hierarchical
 * tree structure of files and directories.
 */
public class PathListParser {

    /**
     * Represents a single node (file or directory) in our custom tree structure.
     */
    @Data
    @Setter(value = AccessLevel.NONE)
    public static class FsEntry {
        private final String name;
        private final Path path;
        private final boolean isDirectory;
        private final List<FsEntry> children = new ArrayList<>();

        public FsEntry(String name, Path path, boolean isDirectory) {
            this.name = name;
            this.path = path;
            this.isDirectory = isDirectory;
        }

        public void addChild(FsEntry child) {
            this.children.add(child);
            // Sort children alphabetically (files and directories mixed) for cleaner output
            this.children.sort(Comparator.comparing(FsEntry::getName));
        }

        public List<FsEntry> getChildren() {
            return children;
        }

        public String getName() {
            return name;
        }

        public Path getPath() {
            return path;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        /**
         * Simple recursive print function to visualize the tree structure.
         */
        public void printTree(int depth) {
            String indent = "  ".repeat(depth);
            String type = isDirectory ? "[DIR]" : "[FILE]";
            System.out.println(indent + type + " " + name);
            for (FsEntry child : children) {
                child.printTree(depth + 1);
            }
        }
    }

    /**
    * Builds the FileSystemEntry tree from a flat list of absolute path strings.
    * This is designed to parse the output of a command like 'find' run inside a container.
    * * @param pathStrings A list of absolute path strings (e.g., /app/src/file.txt).
    * @return The root FileSystemEntry of the reconstructed tree.
    */
    public static FsEntry
            buildTreeFromPathList(List<String> pathStrings) {
        if (pathStrings == null || pathStrings.isEmpty()) {
            return null;
        }

        // 1. Determine the overall root path from the list (the shortest path)
        String rootPathString = pathStrings.stream()
                .min(Comparator.comparingInt(String::length))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Path list is empty."));

        // If the root is a directory, Path.getFileName() might return null/empty, so we use the full string as name
        FsEntry root = new FsEntry(
                Paths.get(rootPathString).getFileName() != null
                        ? Paths.get(rootPathString).getFileName().toString()
                        : rootPathString,
                Paths.get(rootPathString),
                // Assume the shortest path is the root directory we started from
                true);

        // Map to hold parent nodes we've already created for quick lookup
        // Key: Path of the parent, Value: The FileSystemEntry instance
        java.util.Map<Path, FsEntry> createdNodes =
                new java.util.HashMap<>();
        createdNodes.put(root.getPath(), root);

        // Sort the paths to ensure parents are processed before their children
        pathStrings.sort(Comparator.comparingInt(String::length));

        for (String pathString : pathStrings) {
            Path currentPath = Paths.get(pathString);

            // Skip the main root, as it's already created
            if (currentPath.equals(root.getPath())) {
                continue;
            }

            Path parentPath = currentPath.getParent();
            if (parentPath == null || !createdNodes.containsKey(parentPath)) {
                // Should not happen if the list is complete and well-formed, but handle defensively
                continue;
            }

            // A path that is a prefix of other paths is considered a directory
            boolean isDirectory = pathStrings.stream()
                    .anyMatch(p -> p.startsWith(pathString + "/") ||
                            p.startsWith(pathString + "\\"));

            // Create the new entry
            FsEntry newNode = new FsEntry(
                    currentPath.getFileName().toString(),
                    currentPath,
                    isDirectory);

            // Add to its parent
            FsEntry parentNode = createdNodes.get(parentPath);
            parentNode.addChild(newNode);

            // If the new node is a directory (or assumed to be one), register it as a potential parent
            if (isDirectory) {
                createdNodes.put(currentPath, newNode);
            }
        }
        return root;
    }
}
