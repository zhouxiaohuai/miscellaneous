package com.example.transaction.architecture.patterns.template;

import java.util.Map;

/**
 * 导出请求
 */
public record ExportRequest(String fileName, Map<String, Object> queryParams) {}
