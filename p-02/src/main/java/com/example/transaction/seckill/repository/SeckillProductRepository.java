package com.example.transaction.seckill.repository;

import com.example.transaction.seckill.entity.SeckillProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 秒杀商品 Repository
 */
@Repository
public interface SeckillProductRepository extends JpaRepository<SeckillProduct, Long> {

    /**
     * 乐观锁扣减库存
     *
     * 关键：WHERE stock > 0 AND version = #{version}
     * - 库存 > 0：防止超卖
     * - version 匹配：防止并发覆盖
     * - 返回 0：说明被其他线程抢先修改了
     */
    @Modifying
    @Query("UPDATE SeckillProduct p SET p.stock = p.stock - 1, p.version = p.version + 1 " +
           "WHERE p.id = :id AND p.stock > 0 AND p.version = :version")
    int deductStockOptimistic(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 悲观锁查询（SELECT FOR UPDATE）
     *
     * @Lock(LockModeType.PESSIMISTIC_WRITE) = SELECT ... FOR UPDATE
     *
     * 效果：
     *   事务内查询时，其他事务不能修改这行数据
     *   直到当前事务提交或回滚
     *
     * 适用场景：
     *   高并发下，避免大量乐观锁重试
     *   100个请求抢最后1件，悲观锁保证只有1个请求能进入
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SeckillProduct p WHERE p.id = :id")
    Optional<SeckillProduct> findByIdWithLock(@Param("id") Long id);

    /**
     * 悲观锁扣减库存（直接 UPDATE）
     *
     * 为什么不需要 version 条件？
     *   因为 SELECT FOR UPDATE 已经锁住了这行
     *   其他事务无法并发修改，所以不会出现乐观锁冲突
     */
    @Modifying
    @Query("UPDATE SeckillProduct p SET p.stock = p.stock - 1 WHERE p.id = :id AND p.stock > 0")
    int deductStockPessimistic(@Param("id") Long id);
}
