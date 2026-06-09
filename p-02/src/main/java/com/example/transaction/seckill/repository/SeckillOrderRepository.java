package com.example.transaction.seckill.repository;

import com.example.transaction.seckill.entity.SeckillOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 秒杀订单 Repository
 */
@Repository
public interface SeckillOrderRepository extends JpaRepository<SeckillOrder, Long> {

    /**
     * 判断用户是否已抢购过该商品
     */
    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
