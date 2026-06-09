package com.example.transaction.seckill.controller;

import com.example.transaction.seckill.entity.SeckillProduct;
import com.example.transaction.seckill.repository.SeckillProductRepository;
import com.example.transaction.seckill.service.SeckillRateLimitService;
import com.example.transaction.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀演示接口
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final SeckillProductRepository productRepository;
    private final SeckillRateLimitService rateLimitService;

    // ============================
    // 管理接口（模拟运营后台）
    // ============================

    /**
     * 创建秒杀商品
     *
     * 示例：POST /api/seckill/product
     * Body: {"productName":"iPhone 16","originalPrice":7999,"seckillPrice":4999,"stock":100}
     */
    @PostMapping("/product")
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> body) {
        SeckillProduct product = SeckillProduct.builder()
                .productName((String) body.get("productName"))
                .originalPrice(new BigDecimal(body.get("originalPrice").toString()))
                .seckillPrice(new BigDecimal(body.get("seckillPrice").toString()))
                .stock((Integer) body.get("stock"))
                .status(0)
                .createTime(LocalDateTime.now())
                .build();
        productRepository.save(product);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "秒杀商品创建成功");
        result.put("productId", product.getId());
        return result;
    }

    /**
     * 库存预热（活动开始前调用）
     *
     * 示例：POST /api/seckill/warmup/1
     */
    @PostMapping("/warmup/{productId}")
    public Map<String, Object> warmUp(@PathVariable Long productId) {
        seckillService.warmUpStock(productId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "库存预热完成");
        result.put("productId", productId);
        return result;
    }

    // ============================
    // 用户接口
    // ============================

    /**
     * 秒杀下单（核心接口）
     *
     * 示例：POST /api/seckill/buy?userId=1&productId=1
     *
     * 高并发场景：大量用户同时请求这个接口
     */
    @PostMapping("/buy")
    public Map<String, Object> seckill(@RequestParam Long userId, @RequestParam Long productId) {
        String result = seckillService.seckill(userId, productId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("productId", productId);
        response.put("result", result);
        return response;
    }

    /**
     * 查询剩余库存
     *
     * 示例：GET /api/seckill/stock/1
     */
    @GetMapping("/stock/{productId}")
    public Map<String, Object> getStock(@PathVariable Long productId) {
        Map<String, Object> result = new HashMap<>();
        result.put("productId", productId);
        result.put("stock", seckillService.getStock(productId));
        return result;
    }

    /**
     * 查询是否已抢购
     *
     * 示例：GET /api/seckill/check?userId=1&productId=1
     */
    @GetMapping("/check")
    public Map<String, Object> checkBought(@RequestParam Long userId, @RequestParam Long productId) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("productId", productId);
        result.put("hasBought", seckillService.hasBought(userId, productId));
        return result;
    }

    // ============================
    // 测试接口
    // ============================

    /**
     * 一键初始化测试环境
     *
     * 示例：POST /api/seckill/init
     * 创建一个 100 件库存的秒杀商品，并预热到 Redis
     */
    @PostMapping("/init")
    public Map<String, Object> init() {
        // 创建商品
        SeckillProduct product = SeckillProduct.builder()
                .productName("测试秒杀商品 - iPhone 16")
                .originalPrice(new BigDecimal("7999.00"))
                .seckillPrice(new BigDecimal("4999.00"))
                .stock(100)
                .status(1)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(2))
                .createTime(LocalDateTime.now())
                .build();
        productRepository.save(product);

        // 预热库存
        seckillService.warmUpStock(product.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "测试环境初始化完成");
        result.put("productId", product.getId());
        result.put("productName", product.getProductName());
        result.put("stock", product.getStock());
        result.put("seckillPrice", product.getSeckillPrice());
        return result;
    }

    // ============================
    // P1 接口
    // ============================

    /**
     * 秒杀下单（悲观锁版本）
     *
     * 示例：POST /api/seckill/buy-pessimistic?userId=1&productId=1
     *
     * 对比：
     *   /buy              → 乐观锁版本（默认）
     *   /buy-pessimistic  → 悲观锁版本
     */
    @PostMapping("/buy-pessimistic")
    public Map<String, Object> seckillPessimistic(@RequestParam Long userId, @RequestParam Long productId) {
        String result = seckillService.seckillWithPessimisticLock(userId, productId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("productId", productId);
        response.put("result", result);
        response.put("lockType", "pessimistic");
        return response;
    }

    // ============================
    // P0 接口
    // ============================

    /**
     * 查询用户限流状态
     *
     * 示例：GET /api/seckill/rate-limit?userId=1&productId=1
     */
    @GetMapping("/rate-limit")
    public Map<String, Object> getRateLimit(@RequestParam Long userId, @RequestParam Long productId) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("productId", productId);
        result.put("currentCount", rateLimitService.getCurrentCount(userId, productId));
        result.put("maxAttempts", 5);
        result.put("windowSeconds", 60);
        return result;
    }

    /**
     * 查询延迟队列状态（已改为 RocketMQ，此接口仅做说明）
     *
     * RocketMQ 版本：
     *   - 消息发送后由 RocketMQ 管理
     *   - 可通过 RocketMQ Dashboard 查看：http://localhost:18080
     *   - 查看 Topic: seckill-order-cancel
     */
    @GetMapping("/delay-queue")
    public Map<String, Object> getDelayQueue() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "已升级为 RocketMQ 延迟消息");
        result.put("topic", "seckill-order-cancel");
        result.put("delayLevel", 14);
        result.put("delayTime", "10分钟");
        result.put("dashboard", "http://localhost:18080");
        return result;
    }
}
