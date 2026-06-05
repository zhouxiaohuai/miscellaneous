package com.example.transaction.controller;

import com.example.transaction.service.TxAdvancedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tx 框架高级用法演示：大事务 + 嵌套事务
 */
@Slf4j
@RestController
@RequestMapping("/api/tx-advanced")
@RequiredArgsConstructor
public class TxAdvancedController {

    private final TxAdvancedService txAdvancedService;

    // ========================================
    // 1. 大事务 + Tx 联动
    // ========================================

    /**
     * 分片提交：大批量数据分片处理
     * GET /api/tx-advanced/big/chunked?totalCount=1000&chunkSize=100
     */
    @GetMapping("/big/chunked")
    public Map<String, Object> bigChunked(
            @RequestParam(defaultValue = "1000") int totalCount,
            @RequestParam(defaultValue = "100") int chunkSize) {
        return txAdvancedService.bigTransaction_ChunkedCommit(totalCount, chunkSize);
    }

    /**
     * 最小事务范围：只在写操作时开启事务
     * GET /api/tx-advanced/big/minimal?userId=1
     */
    @GetMapping("/big/minimal")
    public Map<String, Object> bigMinimal(@RequestParam Long userId) {
        return txAdvancedService.bigTransaction_MinimalScope(userId);
    }

    // ========================================
    // 2. 嵌套事务 + Tx 联动
    // ========================================

    /**
     * REQUIRES_NEW：每个用户独立事务
     * GET /api/tx-advanced/nested/requires-new?userIds=1,2,3
     */
    @GetMapping("/nested/requires-new")
    public Map<String, Object> nestedRequiresNew(@RequestParam List<Long> userIds) {
        return txAdvancedService.nestedTransaction_RequiresNew(userIds);
    }

    /**
     * NESTED：内层回滚不影响外层
     * GET /api/tx-advanced/nested/nested?userId=1
     */
    @GetMapping("/nested/nested")
    public Map<String, Object> nestedNested(@RequestParam Long userId) {
        return txAdvancedService.nestedTransaction_Nested(userId);
    }

    /**
     * 组合嵌套：REQUIRES_NEW + NESTED
     * GET /api/tx-advanced/nested/combined?userId=1
     */
    @GetMapping("/nested/combined")
    public Map<String, Object> nestedCombined(@RequestParam Long userId) {
        return txAdvancedService.nestedTransaction_Combined(userId);
    }

    // ========================================
    // 3. 总览
    // ========================================

    /**
     * 总览
     * GET /api/tx-advanced/overview
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return Map.of(
                "说明", "Tx 框架高级用法：大事务 + 嵌套事务联动",
                "大事务联动", Map.of(
                        "分片提交", "GET /api/tx-advanced/big/chunked?totalCount=1000&chunkSize=100",
                        "最小事务范围", "GET /api/tx-advanced/big/minimal?userId=1"
                ),
                "嵌套事务联动", Map.of(
                        "REQUIRES_NEW", "GET /api/tx-advanced/nested/requires-new?userIds=1,2,3",
                        "NESTED", "GET /api/tx-advanced/nested/nested?userId=1",
                        "组合嵌套", "GET /api/tx-advanced/nested/combined?userId=1"
                ),
                "核心思路", Map.of(
                        "大事务", "分片提交 + 最小事务范围，用 Tx.writable() 包裹每片/写操作",
                        "嵌套事务", "用 Tx.builder().propagation() 设置 NESTED 或 REQUIRES_NEW"
                )
        );
    }
}
