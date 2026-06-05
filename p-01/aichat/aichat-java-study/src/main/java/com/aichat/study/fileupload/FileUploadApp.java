package com.aichat.study.fileupload;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

import com.aichat.study.fileupload.handler.FileListHandler;
import com.aichat.study.fileupload.handler.StaticFileHandler;
import com.aichat.study.fileupload.handler.UploadHandler;
import com.aichat.study.fileupload.service.FileStorageService;
import com.sun.net.httpserver.HttpServer;

public class FileUploadApp {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        System.out.println("======================================");
        System.out.println("  文件上传模拟系统 v1.0");
        System.out.println("  File Upload Simulator");
        System.out.println("======================================");

        FileStorageService storageService = new FileStorageService();
        System.out.println("  [✓] 存储目录: " + storageService.getUploadDir().toAbsolutePath());

        Path webappPath = Paths.get("src/main/java/com/aichat/study/fileupload/webapp");
        if (!webappPath.toFile().exists()) {
            webappPath = Paths.get(System.getProperty("user.dir"),
                    "aichat-java-study/src/main/java/com/aichat/study/fileupload/webapp");
        }
        System.out.println("  [✓] 静态资源: " + webappPath.toAbsolutePath());

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", new StaticFileHandler(webappPath));
        server.createContext("/api/upload", new UploadHandler(storageService));
        server.createContext("/api/files", new FileListHandler(storageService));

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("  [✓] 服务已启动: http://localhost:" + PORT);
        System.out.println("======================================");
    }
}
