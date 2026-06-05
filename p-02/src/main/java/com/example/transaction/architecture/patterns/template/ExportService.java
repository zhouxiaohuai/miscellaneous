package com.example.transaction.architecture.patterns.template;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * ============================================================
 * 模板方法模式 — 数据导出模板
 * ============================================================
 *
 * 【定义】
 * 定义一个操作中的算法骨架，将某些步骤延迟到子类中。
 * 模板方法使得子类可以在不改变算法结构的情况下，重新定义算法的某些步骤。
 *
 * 【适用场景】
 * - 流程固定，但某些步骤可变
 * - 多个子类有相同的流程，但实现细节不同
 * - 钩子方法控制流程的可选步骤
 *
 * 【本示例场景】
 * 导出数据到不同格式（Excel、CSV、JSON），流程都是：
 * 1. 校验参数
 * 2. 查询数据
 * 3. 转换格式（可变步骤）
 * 4. 写入文件
 * 5. 上传到 OSS（可选步骤 — 钩子方法）
 *
 * 【类图】
 * ┌──────────────────────────────┐
 * │ AbstractExportService        │
 * │                              │
 * │ + export() ← 模板方法（final）│
 * │ # queryData()   ← 抽象方法   │
 * │ # convertData() ← 抽象方法   │
 * │ + needUpload()  ← 钩子方法   │
 * └──────────┬───────────────────┘
 *        ┌───┼────┐
 *        ▼   ▼    ▼
 *      Excel CSV  JSON
 */
@Slf4j
public abstract class ExportService<T> {

    /**
     * 模板方法 — 定义导出流程的骨架
     * 声明为 final，子类不能修改流程顺序
     */
    public final ExportResult export(ExportRequest request) {
        log.info("[模板方法] 开始导出: format={}, fileName={}", getFormat(), request.fileName());

        // Step 1: 校验参数
        validate(request);

        // Step 2: 查询数据
        List<T> data = queryData(request);
        log.info("[模板方法] 查询到 {} 条数据", data.size());

        // Step 3: 转换格式（抽象方法 — 子类实现）
        byte[] content = convertData(data);
        log.info("[模板方法] 数据转换完成, 大小: {} bytes", content.length);

        // Step 4: 写入文件
        String filePath = writeToFile(request.fileName(), content);

        // Step 5: 上传到 OSS（钩子方法 — 子类可选重写）
        String url = null;
        if (needUpload()) {
            url = uploadToOss(filePath);
        }

        ExportResult result = new ExportResult(true, filePath, url, data.size(), "导出成功");
        log.info("[模板方法] 导出完成: {}", result);
        return result;
    }

    // ==================== 公共方法（子类直接使用） ====================

    protected void validate(ExportRequest request) {
        if (request.fileName() == null || request.fileName().isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (request.queryParams() == null) {
            throw new IllegalArgumentException("查询参数不能为空");
        }
        log.info("[模板方法] 参数校验通过");
    }

    protected String writeToFile(String fileName, byte[] content) {
        String fullPath = "/tmp/export/" + fileName + "." + getFormat().toLowerCase();
        log.info("[模板方法] 写入文件: {}, 大小: {} bytes", fullPath, content.length);
        return fullPath;
    }

    protected String uploadToOss(String filePath) {
        String url = "https://oss.example.com/" + filePath;
        log.info("[模板方法] 上传到 OSS: {}", url);
        return url;
    }

    // ==================== 抽象方法（子类必须实现） ====================

    /**
     * 查询数据 — 每种导出的数据源不同
     */
    protected abstract List<T> queryData(ExportRequest request);

    /**
     * 数据转换 — 每种格式的转换逻辑不同
     */
    protected abstract byte[] convertData(List<T> data);

    /**
     * 获取导出格式标识
     */
    protected abstract String getFormat();

    // ==================== 钩子方法（子类可选重写） ====================

    /**
     * 是否需要上传到 OSS
     * 默认 true，子类可重写为 false
     */
    protected boolean needUpload() {
        return true;
    }
}
