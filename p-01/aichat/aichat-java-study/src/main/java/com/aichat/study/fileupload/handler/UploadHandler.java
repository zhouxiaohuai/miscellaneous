package com.aichat.study.fileupload.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.aichat.study.fileupload.model.FileMeta;
import com.aichat.study.fileupload.service.FileStorageService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class UploadHandler implements HttpHandler {

    private static final int MAX_UPLOAD_SIZE = 50 * 1024 * 1024;

    private final FileStorageService storageService;

    public UploadHandler(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("仅支持 POST 请求"));
            return;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("multipart/form-data")) {
            sendJson(exchange, 400, error("请求必须为 multipart/form-data"));
            return;
        }

        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            sendJson(exchange, 400, error("无法解析 boundary"));
            return;
        }

        byte[] body = readFullBody(exchange.getRequestBody(), MAX_UPLOAD_SIZE);

        try {
            FileMeta meta = parseAndStore(body, boundary);
            Map<String, Object> fileData = new HashMap<>();
            fileData.put("id", meta.getId());
            fileData.put("originalName", meta.getOriginalName());
            fileData.put("storedName", meta.getStoredName());
            fileData.put("size", meta.getSize());
            fileData.put("formattedSize", meta.getFormattedSize());
            fileData.put("contentType", meta.getContentType());
            fileData.put("uploadTime", meta.getFormattedTime());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("file", fileData);

            sendJson(exchange, 200, JsonUtil.toJson(result));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, error(e.getMessage()));
        }
    }

    private FileMeta parseAndStore(byte[] body, String boundary) throws IOException {
        String marker = "--" + boundary;
        String endMarker = marker + "--";

        String raw = new String(body, StandardCharsets.ISO_8859_1);

        int partStart = raw.indexOf(marker);
        if (partStart < 0) {
            throw new IllegalArgumentException("未找到 multipart 数据");
        }
        partStart = raw.indexOf("\r\n\r\n", partStart);
        if (partStart < 0) {
            throw new IllegalArgumentException("格式错误");
        }
        partStart += 4;

        int partEnd = raw.indexOf(marker, partStart);
        if (partEnd < 0) {
            partEnd = raw.indexOf(endMarker, partStart);
            if (partEnd < 0) {
                throw new IllegalArgumentException("未找到 multipart 结束标记");
            }
        }

        String headerSection = raw.substring(0, raw.indexOf("\r\n\r\n"));

        String originalName = extractFileName(headerSection);
        if (originalName == null) {
            throw new IllegalArgumentException("未找到文件名，请确认表单项名称为 file");
        }

        byte[] fileBytes = new byte[partEnd - partStart];
        System.arraycopy(body, partStart, fileBytes, 0, fileBytes.length);

        if (fileBytes.length >= 2 && fileBytes[fileBytes.length - 1] == '\n') {
            int trim = (fileBytes.length >= 2 && fileBytes[fileBytes.length - 2] == '\r') ? 2 : 1;
            byte[] trimmed = new byte[fileBytes.length - trim];
            System.arraycopy(fileBytes, 0, trimmed, 0, trimmed.length);
            fileBytes = trimmed;
        }

        String mimeType = extractContentType(headerSection);
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        try (InputStream in = new java.io.ByteArrayInputStream(fileBytes)) {
            return storageService.store(originalName, fileBytes.length, mimeType, in);
        }
    }

    private String extractFileName(String headerSection) {
        String lower = headerSection.toLowerCase();
        int idx = lower.indexOf("name=\"file\"");
        if (idx < 0) {
            return null;
        }
        int fnIdx = lower.indexOf("filename=\"", idx);
        if (fnIdx < 0) {
            return null;
        }
        fnIdx += 10;
        int end = lower.indexOf("\"", fnIdx);
        if (end < 0) {
            return null;
        }
        return headerSection.substring(fnIdx, end);
    }

    private String extractContentType(String headerSection) {
        int idx = headerSection.toLowerCase().indexOf("content-type:");
        if (idx < 0) {
            return null;
        }
        int start = idx + 13;
        int end = headerSection.indexOf("\r\n", start);
        if (end < 0) {
            end = headerSection.length();
        }
        return headerSection.substring(start, end).trim();
    }

    private String extractBoundary(String contentType) {
        int idx = contentType.indexOf("boundary=");
        if (idx < 0) {
            return null;
        }
        String bound = contentType.substring(idx + 9).trim();
        if (bound.startsWith("\"") && bound.endsWith("\"")) {
            bound = bound.substring(1, bound.length() - 1);
        }
        return bound;
    }

    private byte[] readFullBody(InputStream in, int maxSize) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxSize) {
                throw new IOException("文件大小超过限制 (50MB)");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String error(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("success", false);
        m.put("error", msg);
        return JsonUtil.toJson(m);
    }
}
