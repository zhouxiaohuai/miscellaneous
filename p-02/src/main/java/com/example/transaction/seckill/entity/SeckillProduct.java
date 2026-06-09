package com.example.transaction.seckill.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_seckill_product")
public class SeckillProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商品名称 */
    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    /** 原价 */
    @Column(name = "original_price", nullable = false)
    private BigDecimal originalPrice;

    /** 秒杀价 */
    @Column(name = "seckill_price", nullable = false)
    private BigDecimal seckillPrice;

    /** 库存 */
    @Column(nullable = false)
    private Integer stock;

    /** 版本号（乐观锁） */
    @Version
    private Integer version;

    /** 秒杀开始时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 秒杀结束时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 状态：0-未开始 1-进行中 2-已结束 */
    private Integer status;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
