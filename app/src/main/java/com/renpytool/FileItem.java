package com.renpytool;

import java.io.File;

/**
 * Represents a file or directory item in the file picker
 */
public class FileItem implements Comparable<FileItem> {
    private final File file;
    private final String name;
    private final boolean isDirectory;
    private final long size;
    private final long lastModified;

    public FileItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.isDirectory = file.isDirectory();
        this.size = file.length();
        this.lastModified = file.lastModified();
    }

    // Special constructor for "Up" navigation item
    public FileItem(String name, File parentFile) {
        this.file = parentFile;
        this.name = name;
        this.isDirectory = true;
        this.size = 0;
        this.lastModified = 0;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getPath() {
        return file.getAbsolutePath();
    }

    @Override
    public int compareTo(FileItem other) {
        // Directories come before files
        if (this.isDirectory && !other.isDirectory) {
            return -1;
        } else if (!this.isDirectory && other.isDirectory) {
            return 1;
        }
        // Within same type, sort alphabetically (case-insensitive)
        return this.name.compareToIgnoreCase(other.name);
    }
}
