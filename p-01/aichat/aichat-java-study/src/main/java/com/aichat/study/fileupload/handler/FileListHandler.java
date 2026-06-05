package com.aichat.study.fileupload.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aichat.study.fileupload.model.FileMeta;
import com.aichat.study.fileupload.service.FileStorageService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class FileListHandler implements HttpHandler {

    private final FileStorageService storageService;

    public FileListHandler(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();

        if ("GET".equals(method)) {
            if (path.equals("/api/files")) {
                handleList(exchange);
            } else if (path.startsWith("/api/files/")) {
                handleDownload(exchange, path);
            } else {
                sendJson(exchange, 404, error("接口不存在"));
            }
        } else {
            sendJson(exchange, 405, error("仅支持 GET 请求"));
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        List<FileMeta> metas = storageService.listAll();
        List<Map<String, Object>> list = new ArrayList<>();

        for (FileMeta meta : metas) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", meta.getId());
            item.put("originalName", meta.getOriginalName());
            item.put("storedName", meta.getStoredName());
            item.put("size", meta.getSize());
            item.put("formattedSize", meta.getFormattedSize());
            item.put("contentType", meta.getContentType());
            item.put("uploadTime", meta.getFormattedTime());
            list.add(item);
        }

        sendJson(exchange, 200, JsonUtil.toJson(list));
    }

    private void handleDownload(HttpExchange exchange, String path) throws IOException {
        String storedName = path.substring("/api/files/".length());

        if (storedName.isEmpty()) {
            sendJson(exchange, 400, error("文件名不能为空"));
            return;
        }

        FileMeta meta = storageService.findByStoredName(storedName);
        if (meta == null) {
            sendJson(exchange, 404, error("文件不存在"));
            return;
        }

        Path filePath = storageService.resolvePath(storedName);
        if (!Files.exists(filePath)) {
            sendJson(exchange, 404, error("文件在磁盘上不存在"));
            return;
        }

        byte[] content = Files.readAllBytes(filePath);

        exchange.getResponseHeaders().set("Content-Type", meta.getContentType());
        exchange.getResponseHeaders().set("Content-Disposition",
                "inline; filename=\"" + meta.getOriginalName() + "\"");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, content.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
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
