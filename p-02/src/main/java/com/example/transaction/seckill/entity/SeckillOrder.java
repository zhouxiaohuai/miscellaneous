package com.example.transaction.seckill.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_seckill_order")
public class SeckillOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 商品ID */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 订单号 */
    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    /** 秒杀价 */
    @Column(name = "seckill_price", nullable = false)
    private BigDecimal seckillPrice;

    /** 状态：0-待支付 1-已支付 2-已取消 3-超时取消 */
    private Integer status;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
