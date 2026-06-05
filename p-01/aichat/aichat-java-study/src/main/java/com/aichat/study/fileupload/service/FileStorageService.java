package com.aichat.study.fileupload.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.aichat.study.fileupload.model.FileMeta;

public class FileStorageService {

    private static final Path UPLOAD_DIR = Paths.get("uploads");

    private final List<FileMeta> fileMetas = new CopyOnWriteArrayList<>();

    public FileStorageService() throws IOException {
        Files.createDirectories(UPLOAD_DIR);
    }

    public FileMeta store(String originalName, long size, String contentType, InputStream in) throws IOException {
        String id = UUID.randomUUID().toString();
        String extension = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx > 0) {
            extension = originalName.substring(dotIdx);
        }
        String storedName = id + extension;
        Path target = UPLOAD_DIR.resolve(storedName);

        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);

        FileMeta meta = new FileMeta(id, originalName, storedName, size, contentType, LocalDateTime.now());
        fileMetas.add(meta);
        return meta;
    }

    public List<FileMeta> listAll() {
        return new ArrayList<>(fileMetas);
    }

    public FileMeta findByStoredName(String storedName) {
        return fileMetas.stream()
                .filter(m -> m.getStoredName().equals(storedName))
                .findFirst()
                .orElse(null);
    }

    public Path resolvePath(String storedName) {
        return UPLOAD_DIR.resolve(storedName);
    }

    public Path getUploadDir() {
        return UPLOAD_DIR;
    }
}
