package com.configtool.agent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class FileOperations {
    private final File baseDir;
    private final Logger logger;
    private static final Set<String> ALLOWED_EXT;
    private static final long MAX_SIZE = 1024 * 1024;

    static {
        ALLOWED_EXT = new HashSet<String>();
        ALLOWED_EXT.add(".yml");
        ALLOWED_EXT.add(".yaml");
        ALLOWED_EXT.add(".json");
    }

    public FileOperations(File baseDir, Logger logger) {
        this.baseDir = baseDir;
        this.logger = logger;
    }

    public Map<String, Object> listFiles(String directory, int offset, int limit) throws IOException {
        File dir = resolve(directory);
        if (!dir.isDirectory()) throw new IllegalArgumentException("Not a directory");

        List<Map<String, Object>> allFiles = new ArrayList<Map<String, Object>>();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (File f : files) {
                if (f.getName().startsWith(".")) continue;
                String relPath = getRelativePath(f);
                if (f.isDirectory()) {
                    Map<String, Object> entry = new HashMap<String, Object>();
                    entry.put("path", relPath);
                    entry.put("name", f.getName());
                    entry.put("isDirectory", true);
                    entry.put("size", 0L);
                    allFiles.add(entry);
                } else if (isAllowed(f.getName())) {
                    Map<String, Object> entry = new HashMap<String, Object>();
                    entry.put("path", relPath);
                    entry.put("name", f.getName());
                    entry.put("isDirectory", false);
                    entry.put("size", f.length());
                    allFiles.add(entry);
                }
            }
        }

        int total = allFiles.size();
        int end = Math.min(offset + limit, total);
        List<Map<String, Object>> page;
        if (offset < total) {
            page = allFiles.subList(offset, end);
        } else {
            page = Collections.emptyList();
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("files", page);
        result.put("total", total);
        result.put("offset", offset);
        result.put("hasMore", end < total);
        return result;
    }

    public String readFile(String path) throws IOException {
        File f = resolve(path);
        if (!f.isFile()) throw new IllegalArgumentException("Not a file");
        if (!isAllowed(f.getName())) throw new SecurityException("File type not allowed");
        if (f.length() > MAX_SIZE) throw new IllegalArgumentException("File too large");
        byte[] bytes = Files.readAllBytes(f.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void writeFile(String path, String content) throws IOException {
        File f = resolve(path);
        if (!isAllowed(f.getName())) throw new SecurityException("File type not allowed");
        if (content.length() > MAX_SIZE) throw new IllegalArgumentException("Content too large");

        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try {
            Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
            if (f.exists()) {
                File bak = new File(f.getParentFile(), f.getName() + ".bak");
                if (bak.exists()) bak.delete();
                f.renameTo(bak);
            }
            if (!tmp.renameTo(f)) throw new IOException("Failed to rename");
        } finally {
            if (tmp.exists()) tmp.delete();
        }
        logger.info("Written: " + path);
    }

    private File resolve(String path) {
        path = path.replace('\\', '/');
        if (path.contains("..") || path.startsWith("/")) throw new SecurityException("Invalid path");
        if (!path.startsWith("plugins/") && !path.equals("plugins")) throw new SecurityException("Access denied");

        File f = new File(baseDir, path.substring("plugins/".length()));
        try {
            if (!f.getCanonicalPath().startsWith(baseDir.getCanonicalPath()))
                throw new SecurityException("Path traversal");
        } catch (IOException e) {
            throw new SecurityException("Path validation failed");
        }
        return f;
    }

    private String getRelativePath(File f) {
        return "plugins/" + baseDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
    }

    private boolean isAllowed(String name) {
        String lower = name.toLowerCase();
        for (String ext : ALLOWED_EXT) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    public void deleteFile(String path) throws IOException {
        File f = resolve(path);
        if (!f.exists()) throw new IllegalArgumentException("File not found");
        if (f.isDirectory()) {
            deleteRecursively(f);
        } else {
            if (!f.delete()) throw new IOException("Failed to delete file");
        }
        logger.info("Deleted: " + path);
    }

    private void deleteRecursively(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteRecursively(f);
                } else {
                    if (!f.delete()) throw new IOException("Failed to delete: " + f.getPath());
                }
            }
        }
        if (!dir.delete()) throw new IOException("Failed to delete directory: " + dir.getPath());
    }

    public void createFile(String path, boolean isDirectory) throws IOException {
        File f = resolve(path);
        if (f.exists()) throw new IllegalArgumentException("File already exists");

        if (isDirectory) {
            if (!f.mkdirs()) throw new IOException("Failed to create directory");
        } else {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!f.createNewFile()) throw new IOException("Failed to create file");
        }
        logger.info("Created: " + path + (isDirectory ? " (directory)" : ""));
    }

    public void renameFile(String oldPath, String newPath) throws IOException {
        File oldFile = resolve(oldPath);
        File newFile = resolve(newPath);

        if (!oldFile.exists()) throw new IllegalArgumentException("Source file not found");
        if (newFile.exists()) throw new IllegalArgumentException("Destination already exists");

        File parent = newFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        if (!oldFile.renameTo(newFile)) throw new IOException("Failed to rename file");
        logger.info("Renamed: " + oldPath + " -> " + newPath);
    }
}
