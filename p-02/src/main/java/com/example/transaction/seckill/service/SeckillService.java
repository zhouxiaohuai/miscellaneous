package com.example.transaction.seckill.service;

import com.example.transaction.seckill.entity.SeckillOrder;
import com.example.transaction.seckill.entity.SeckillProduct;
import com.example.transaction.seckill.repository.SeckillOrderRepository;
import com.example.transaction.seckill.repository.SeckillProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 秒杀核心服务
 *
 * 流程：
 *   1. Redis Lua 原子操作（判断库存 + 判断重复 + 扣减库存）
 *   2. 数据库创建订单 + 乐观锁扣减库存
 *
 * 为什么用 Lua？
 *   三个操作必须原子执行，否则高并发下会超卖或重复下单：
 *   - 判断库存是否 > 0
 *   - 判断用户是否已抢购
 *   - 扣减库存 + 记录用户
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final StringRedisTemplate redisTemplate;
    private final SeckillProductRepository productRepository;
    private final SeckillOrderRepository orderRepository;
    private final SeckillRateLimitService rateLimitService;
    private final SeckillDelayQueueService delayQueueService;
    private final SeckillDuplicateService duplicateService;

    // ============================
    // Redis Key 设计
    // ============================

    /** 秒杀库存 key：seckill:stock:{productId} */
    private static final String STOCK_KEY = "seckill:stock:%d";
    /** 秒杀已购用户 key：seckill:bought:{productId}（Set 结构） */
    private static final String BOUGHT_KEY = "seckill:bought:%d";

    // ============================
    // Lua 脚本（核心中的核心）
    // ============================

    /**
     * Lua 脚本：原子操作（判断库存 + 判断重复 + 扣减库存）
     *
     * KEYS[1] = 库存 key
     * KEYS[2] = 已购用户 set key
     * ARGV[1] = userId
     *
     * 返回值：
     *   1  = 成功（扣减库存 + 记录用户）
     *   0  = 库存不足
     *  -1  = 重复抢购
     */
    private static final String SECKILL_LUA = """
            -- 1. 判断用户是否已抢购过
            if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
                return -1
            end

            -- 2. 判断库存是否充足
            local stock = tonumber(redis.call('get', KEYS[1]))
            if stock == nil or stock <= 0 then
                return 0
            end

            -- 3. 扣减库存
            redis.call('decrby', KEYS[1], 1)

            -- 4. 记录用户已抢购
            redis.call('sadd', KEYS[2], ARGV[1])

            return 1
            """;

    // ============================
    // 核心方法
    // ============================

    /**
     * 库存预热：活动开始前，把库存加载到 Redis
     *
     * 为什么？数据库扛不住万级 QPS，Redis 可以扛 10 万+
     */
    public void warmUpStock(Long productId) {
        SeckillProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        String stockKey = String.format(STOCK_KEY, productId);
        String boughtKey = String.format(BOUGHT_KEY, productId);

        // 写入库存
        redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));
        // 清空已购用户集合
        redisTemplate.delete(boughtKey);

        log.info("[库存预热] productId={}, stock={}", productId, product.getStock());
    }

    /**
     * 秒杀下单（核心方法）
     *
     * @param userId    用户ID（实际项目中从 JWT/Session 获取）
     * @param productId 商品ID
     * @return 结果说明
     */
    @Transactional(rollbackFor = Exception.class)
    public String seckill(Long userId, Long productId) {
        // ========== 第零步：防重复提交 ==========
        if (!duplicateService.tryLock(userId, productId)) {
            return "请求处理中，请勿重复提交";
        }

        try {
            // ========== 第零点五步：用户限流 ==========
            if (!rateLimitService.isAllowed(userId, productId)) {
                return "操作过于频繁，请稍后再试";
            }

            // ========== 第一步：Redis Lua 原子操作 ==========
            // 快速判断库存 + 重复，99% 的请求在这一步就返回了
            String stockKey = String.format(STOCK_KEY, productId);
            String boughtKey = String.format(BOUGHT_KEY, productId);

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SECKILL_LUA, Long.class);
            Long result = redisTemplate.execute(script, List.of(stockKey, boughtKey), String.valueOf(userId));

            if (result == null) {
                return "系统异常";
            }

            if (result == -1) {
                log.warn("[秒杀失败] 用户 {} 重复抢购商品 {}", userId, productId);
                return "您已抢购过，请勿重复下单";
            }

            if (result == 0) {
                log.warn("[秒杀失败] 商品 {} 库存不足", productId);
                return "手慢了，商品已售罄";
            }

            // ========== 第二步：数据库创建订单 ==========
            // 只有 Lua 返回 1（成功扣减）才会走到这里
            log.info("[秒杀成功] 用户 {} 抢到商品 {}，开始创建订单", userId, productId);

            // 查询商品信息（获取秒杀价）
            SeckillProduct product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            // 创建订单
            SeckillOrder order = SeckillOrder.builder()
                    .userId(userId)
                    .productId(productId)
                    .orderNo(generateOrderNo())
                    .seckillPrice(product.getSeckillPrice())
                    .status(0)  // 待支付
                    .createTime(LocalDateTime.now())
                    .build();
            orderRepository.save(order);

            // 乐观锁扣减数据库库存（防 Redis 和 DB 不一致）
            int rows = productRepository.deductStockOptimistic(productId, product.getVersion());
            if (rows == 0) {
                // 极端情况：Redis 扣成功但 DB 扣失败（库存不一致）
                // 实际项目中需要补偿机制
                log.error("[库存不一致] Redis 已扣减但 DB 扣减失败，productId={}", productId);
                throw new RuntimeException("库存扣减失败，请重试");
            }

            // ========== 第三步：发送 RocketMQ 延迟消息 ==========
            // 10 分钟后未支付，自动取消 + 回补库存
            delayQueueService.sendDelayMessage(order.getOrderNo(), productId);

            log.info("[下单成功] userId={}, orderNo={}, price={}", userId, order.getOrderNo(), order.getSeckillPrice());
            return "恭喜！抢购成功，订单号：" + order.getOrderNo() + "（请在15分钟内支付）";

        } finally {
            // ========== 最后：释放锁 ==========
            // 无论成功失败，都要释放锁
            duplicateService.unlock(userId, productId);
        }
    }

    /**
     * 查询剩余库存（从 Redis 读取，快速）
     */
    public Long getStock(Long productId) {
        String stockKey = String.format(STOCK_KEY, productId);
        String stock = redisTemplate.opsForValue().get(stockKey);
        return stock != null ? Long.parseLong(stock) : 0L;
    }

    /**
     * 查询用户是否已抢购
     */
    public boolean hasBought(Long userId, Long productId) {
        String boughtKey = String.format(BOUGHT_KEY, productId);
        Boolean result = redisTemplate.opsForSet().isMember(boughtKey, String.valueOf(userId));
        return Boolean.TRUE.equals(result);
    }

    // ============================
    // 工具方法
    // ============================

    /**
     * 生成订单号：时间戳 + 随机数
     */
    private String generateOrderNo() {
        return System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }

    // ============================
    // P1-2：悲观锁版本的秒杀
    // ============================

    /**
     * 秒杀下单（悲观锁版本）
     *
     * 对比乐观锁版本：
     *
     * 乐观锁（当前默认）：
     *   Redis Lua → 创建订单 → UPDATE WHERE version=? → 失败重试
     *   优点：不锁数据库行，并发高
     *   缺点：高冲突时大量重试
     *
     * 悲观锁（这个方法）：
     *   Redis Lua → SELECT FOR UPDATE（锁行）→ 判断库存 → 扣减 → 提交释放锁
     *   优点：无重试，直接排队
     *   缺点：锁住数据库行，并发降低
     *
     * 选择建议：
     *   - 低冲突（库存充足）→ 乐观锁
     *   - 高冲突（库存紧张）→ 悲观锁
     */
    @Transactional(rollbackFor = Exception.class)
    public String seckillWithPessimisticLock(Long userId, Long productId) {
        // 防重复提交
        if (!duplicateService.tryLock(userId, productId)) {
            return "请求处理中，请勿重复提交";
        }

        try {
            // 限流
            if (!rateLimitService.isAllowed(userId, productId)) {
                return "操作过于频繁，请稍后再试";
            }

            // ========== 第一步：Redis Lua 快速判断 ==========
            String stockKey = String.format(STOCK_KEY, productId);
            String boughtKey = String.format(BOUGHT_KEY, productId);

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SECKILL_LUA, Long.class);
            Long result = redisTemplate.execute(script, List.of(stockKey, boughtKey), String.valueOf(userId));

            if (result == null) return "系统异常";
            if (result == -1) return "您已抢购过，请勿重复下单";
            if (result == 0) return "手慢了，商品已售罄";

            // ========== 第二步：悲观锁查询 ==========
            // SELECT FOR UPDATE 锁住这行，其他事务排队等待
            SeckillProduct product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            // 再次检查库存（双重检查）
            if (product.getStock() <= 0) {
                log.warn("[悲观锁秒杀] 库存不足，productId={}", productId);
                return "手慢了，商品已售罄";
            }

            // ========== 第三步：扣减库存（不需要 version 条件） ==========
            int rows = productRepository.deductStockPessimistic(productId);
            if (rows == 0) {
                throw new RuntimeException("库存扣减失败");
            }

            // ========== 第四步：创建订单 ==========
            SeckillOrder order = SeckillOrder.builder()
                    .userId(userId)
                    .productId(productId)
                    .orderNo(generateOrderNo())
                    .seckillPrice(product.getSeckillPrice())
                    .status(0)
                    .createTime(LocalDateTime.now())
                    .build();
            orderRepository.save(order);

            // ========== 第五步：发送 RocketMQ 延迟消息 ==========
            delayQueueService.sendDelayMessage(order.getOrderNo(), productId);

            log.info("[悲观锁秒杀成功] userId={}, orderNo={}", userId, order.getOrderNo());
            return "恭喜！抢购成功，订单号：" + order.getOrderNo() + "（请在15分钟内支付）";

        } finally {
            duplicateService.unlock(userId, productId);
        }
    }
}
