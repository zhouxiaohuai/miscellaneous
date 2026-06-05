package com.example.transaction.architecture.patterns.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 模板方法模式 — 具体实现
 */
public class ExportServiceImpl {

    // ==================== 数据记录 ====================

    public record OrderRecord(String orderId, String userName, String product, int quantity, String amount) {}

    // ==================== Excel 导出 ====================

    /**
     * Excel 导出 — 需要上传到 OSS
     */
    @Slf4j
    @Component("excelExportService")
    public static class ExcelExportService extends ExportService<OrderRecord> {

        @Override
        protected List<OrderRecord> queryData(ExportRequest request) {
            log.info("[Excel] 查询订单数据: params={}", request.queryParams());
            return List.of(
                    new OrderRecord("ORD-001", "张三", "iPhone 16", 1, "7999.00"),
                    new OrderRecord("ORD-002", "李四", "MacBook Pro", 1, "14999.00"),
                    new OrderRecord("ORD-003", "王五", "AirPods", 2, "1798.00")
            );
        }

        @Override
        protected byte[] convertData(List<OrderRecord> data) {
            log.info("[Excel] 转换为 Excel 格式: {} 条记录", data.size());
            StringBuilder sb = new StringBuilder();
            sb.append("订单号,用户,商品,数量,金额\n");
            data.forEach(r -> sb.append(String.format("%s,%s,%s,%d,%s\n",
                    r.orderId(), r.userName(), r.product(), r.quantity(), r.amount())));
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected String getFormat() { return "XLSX"; }
    }

    // ==================== CSV 导出 ====================

    /**
     * CSV 导出 — 不需要上传到 OSS（重写钩子方法）
     */
    @Slf4j
    @Component("csvExportService")
    public static class CsvExportService extends ExportService<OrderRecord> {

        @Override
        protected List<OrderRecord> queryData(ExportRequest request) {
            log.info("[CSV] 查询订单数据: params={}", request.queryParams());
            return List.of(
                    new OrderRecord("ORD-001", "张三", "iPhone 16", 1, "7999.00"),
                    new OrderRecord("ORD-002", "李四", "MacBook Pro", 1, "14999.00")
            );
        }

        @Override
        protected byte[] convertData(List<OrderRecord> data) {
            log.info("[CSV] 转换为 CSV 格式: {} 条记录", data.size());
            StringBuilder sb = new StringBuilder();
            sb.append("orderId,userName,product,quantity,amount\n");
            data.forEach(r -> sb.append(String.format("%s,%s,%s,%d,%s\n",
                    r.orderId(), r.userName(), r.product(), r.quantity(), r.amount())));
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected String getFormat() { return "CSV"; }

        @Override
        protected boolean needUpload() {
            return false;
        }
    }

    // ==================== JSON 导出 ====================

    /**
     * JSON 导出
     */
    @Slf4j
    @Component("jsonExportService")
    public static class JsonExportService extends ExportService<OrderRecord> {

        @Override
        protected List<OrderRecord> queryData(ExportRequest request) {
            log.info("[JSON] 查询订单数据: params={}", request.queryParams());
            return List.of(
                    new OrderRecord("ORD-001", "张三", "iPhone 16", 1, "7999.00")
            );
        }

        @Override
        protected byte[] convertData(List<OrderRecord> data) {
            log.info("[JSON] 转换为 JSON 格式: {} 条记录", data.size());
            StringBuilder sb = new StringBuilder("[\n");
            data.forEach(r -> sb.append(String.format(
                    "  {\"orderId\":\"%s\",\"userName\":\"%s\",\"product\":\"%s\",\"quantity\":%d,\"amount\":\"%s\"},\n",
                    r.orderId(), r.userName(), r.product(), r.quantity(), r.amount())));
            sb.append("]");
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected String getFormat() { return "JSON"; }
    }
}
