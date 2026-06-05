# 文件上传模拟系统 — 需求分析与方案设计

> **模块路径**: `com.aichat.study.fileupload`
> **创建日期**: 2026-04-27
> **状态**: 需求分析（尚未编码）

---

## 1. 业务概述

构建一个**文件上传模拟系统**，用户打开一个精美的前端页面，通过页面选择文件并上传，文件实际上存储到本地的某个模拟目录中。同时支持对已上传文件的列表查询和展示。

这是一个**纯学习项目**，不依赖真实数据库或云存储，前端与后端在同一进程中运行（或通过简单的嵌入方式交互），重点练习 Java 后端文件处理 + 前端页面交互。

---

## 2. 核心功能需求

| # | 需求 | 描述 |
|---|------|------|
| 1 | 精美页面 | 用户打开后看到美观、友好的上传页面（现代 UI 风格） |
| 2 | 选择文件上传 | 页面提供文件选择器，用户可选择任意类型文件进行上传 |
| 3 | 本地存储模拟 | 上传的文件保存到本地文件系统的一个指定目录（如 `./uploads/`） |
| 4 | 上传列表查询 | 页面可查询已上传的文件列表 |
| 5 | 列表展示 | 以表格/卡片形式展示文件信息（文件名、大小、上传时间、类型等） |

---

## 3. 非功能需求

- **零外部依赖**：除 JDK 标准库 + 可选的内嵌 HTTP 服务器外，不引入 Spring Boot 等重型框架
- **单进程运行**：后端嵌入 HTTP Server（推荐 JDK 内建 `com.sun.net.httpserver` 或 Javalin / Spark 等轻量框架）
- **模拟存储**：不上传至云存储或数据库，直接写入本地磁盘
- **简单部署**：`java -jar` 或直接运行 main 方法即可启动

---

## 4. 技术方案选型（待定）

| 维度 | 候选方案 | 备选方案 |
|------|---------|---------|
| HTTP 服务器 | `com.sun.net.httpserver`（JDK 内置，零依赖） | Javalin / Spark（轻量三方库） |
| 前端页面 | 内嵌 HTML + CSS + JS（单页应用，纯静态资源） | Thymeleaf 模板 |
| 文件存储 | `java.nio.file` 写入本地 `./uploads/` | — |
| 文件元数据 | 内存中 `List<FileMeta>` 或 JSON 文件持久化 | — |
| 构建工具 | Maven（已有） | — |
| Java 版本 | 17（已有） | — |

**推荐方案**：
- `com.sun.net.httpserver` 作为后端
- 纯 HTML/CSS/JS 单页作为前端，内嵌在 resources 中或由后端直接 serve
- Java NIO 处理文件读写
- 内存列表存储文件元信息（启动后丢失，但已是模拟场景）

---

## 5. 页面设计构想

### 5.1 布局

```
┌────────────────────────────────────────────┐
│  🗂  文件上传系统                            │
│  ────────────────────────────────────────── │
│  ┌──────────────────────────────────────┐   │
│  │  拖拽区域 / 点击选择文件  [选择文件] │   │
│  │  ┌────────────────────────────────┐  │   │
│  │  │  已选择: xxx.pdf               │  │   │
│  │  └────────────────────────────────┘  │   │
│  │          [📤 上传]                   │   │
│  └──────────────────────────────────────┘   │
│                                             │
│  ┌──────────────────────────────────────┐   │
│  │  已上传文件列表                        │   │
│  │  ┌──────┬──────┬────────┬────────┐   │   │
│  │  │ 文件名 │ 大小 │ 类型   │ 时间   │   │   │
│  │  ├──────┼──────┼────────┼────────┤   │   │
│  │  │ ...  │ ...  │ ...    │ ...    │   │   │
│  │  └──────┴──────┴────────┴────────┘   │   │
│  └──────────────────────────────────────┘   │
└────────────────────────────────────────────┘
```

### 5.2 交互流程

1. 用户访问 `http://localhost:8080` → 加载页面
2. 点击"选择文件"或拖拽文件到上传区域
3. 点击"上传"按钮 → `POST /api/upload` → 后端接收 multipart/form-data
4. 后端生成唯一文件名，写入 `./uploads/` 目录
5. 后端记录文件元数据（原始名、大小、类型、上传时间）到内存列表
6. 返回成功响应 → 前端刷新列表
7. 页面加载 & 上传后自动调用 `GET /api/files` → 展示文件列表

---

## 6. API 设计

| 方法 | 路径 | 说明 | 请求 | 响应 |
|------|------|------|------|------|
| GET | `/` | 返回首页 HTML | — | `text/html` |
| POST | `/api/upload` | 上传文件 | `multipart/form-data` 字段 `file` | `{"success":true, "file":{...}}` |
| GET | `/api/files` | 获取文件列表 | — | `[{"name","size","type","time"},...]` |
| GET | `/api/files/{name}` | 下载/预览文件 | — | 文件二进制流 |

---

## 7. 文件元数据模型

```java
public class FileMeta {
    private String id;           // 唯一标识（UUID）
    private String originalName; // 原始文件名
    private String storedName;   // 存储文件名（防重名）
    private long size;           // 文件大小（字节）
    private String contentType;  // MIME 类型
    private LocalDateTime uploadTime; // 上传时间
}
```

---

## 8. 目录结构（待创建）

```
fileupload/
├── project.md                      # 本文（需求分析文档）
├── FileUploadApp.java              # 主启动类（内嵌 HTTP 服务器）
├── handler/
│   ├── StaticFileHandler.java      # 静态资源服务
│   ├── UploadHandler.java          # 上传处理
│   └── FileListHandler.java        # 文件列表 / 下载
├── model/
│   └── FileMeta.java               # 文件元数据模型
├── service/
│   └── FileStorageService.java     # 文件存储逻辑（写入+查询）
└── webapp/
    └── index.html                  # 前端页面（内嵌 CSS/JS）
```

---

## 9. 学习要点

通过本项目可以练习的 Java 知识点：

- `com.sun.net.httpserver` 内嵌 HTTP 服务器
- `HttpExchange` 请求/响应处理
- `multipart/form-data` 解析（手动或使用边界分割）
- Java NIO (`Files.copy`, `Path`, `Paths`)
- UUID 生成唯一文件名
- MIME 类型推断 (`Files.probeContentType`)
- `LocalDateTime` / `DateTimeFormatter`
- 内存数据结构 `ConcurrentHashMap` / `CopyOnWriteArrayList`
- 前端基础：HTML5 拖拽上传、Fetch API、响应式布局

---

## 10. 后续步骤

1. [ ] 确定技术选型（建议：JDK HttpServer + 纯前端）
2. [ ] 创建目录结构
3. [ ] 实现 `FileMeta` 模型
4. [ ] 实现 `FileStorageService` 服务层
5. [ ] 实现各个 Handler
6. [ ] 编写前端页面（HTML + CSS + JS）
7. [ ] 编写 `FileUploadApp` 主启动类
8. [ ] 测试：启动 → 上传 → 查询
