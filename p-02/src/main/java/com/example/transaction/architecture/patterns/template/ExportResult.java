package com.example.transaction.architecture.patterns.template;

/**
 * 导出结果
 */
public record ExportResult(boolean success, String filePath, String url, int recordCount, String message) {}
