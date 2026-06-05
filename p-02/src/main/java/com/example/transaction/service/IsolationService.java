package com.example.transaction.service;

import com.example.transaction.entity.IsolationTest;
import com.example.transaction.jpa.repository.IsolationTestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * ========================================
 * 4种事务隔离级别演示
 * ========================================
 *
 * 隔离级别解决的并发问题：
 *
 * | 隔离级别             | 脏读 | 不可重复读 | 幻读 |
 * |---------------------|------|-----------|------|
 * | READ_UNCOMMITTED    |  ✓   |     ✓     |  ✓   |
 * | READ_COMMITTED      |  ✗   |     ✓     |  ✓   |
 * | REPEATABLE_READ     |  ✗   |     ✗     |  ✓   |
 * | SERIALIZABLE        |  ✗   |     ✗     |  ✗   |
 *
 * MySQL InnoDB 默认: REPEATABLE_READ
 * PostgreSQL 默认: READ_COMMITTED
 *
 * 并发问题说明：
 * - 脏读：读到其他事务未提交的数据
 * - 不可重复读：同一事务中两次读取同一行，结果不同（其他事务修改了数据）
 * - 幻读：同一事务中两次查询同一范围，结果集不同（其他事务插入了数据）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsolationService {

    private final IsolationTestRepository isolationTestRepository;

    /**
     * 初始化测试数据
     */
    @Transactional
    public void initTestData() {
        Optional<IsolationTest> existing = isolationTestRepository.findByName("test_key");
        if (existing.isPresent()) {
            isolationTestRepository.updateValueByName("test_key", 100);
        } else {
            IsolationTest test = IsolationTest.builder()
                    .name("test_key")
                    .value(100)
                    .build();
            isolationTestRepository.save(test);
        }
        log.info("[隔离级别] 测试数据初始化完成: test_key = 100");
    }

    // ========================================
    // 1. READ_UNCOMMITTED - 脏读演示
    // ========================================

    /**
     * 事务A：修改数据但不提交
     * 在 READ_UNCOMMITTED 下，事务B能读到这个未提交的值（脏读）
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public int dirtyRead_Write() {
        log.info("[脏读-写事务] 开始，当前值: {}", getCurrentValue());
        int newValue = 999;
        isolationTestRepository.updateValueByName("test_key", newValue);
        log.info("[脏读-写事务] 已修改为: {}（尚未提交）", newValue);

        // 模拟长时间不提交（在实际测试中，这里需要并发执行）
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        log.info("[脏读-写事务] 事务即将回滚");
        throw new RuntimeException("故意回滚，演示脏读");
    }

    /**
     * 事务B：在 READ_UNCOMMITTED 下读取数据
     * 可能读到事务A未提交的值（脏数据）
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public int dirtyRead_Read() {
        log.info("[脏读-读事务] 开始，读取当前值...");
        int value = getCurrentValue();
        log.info("[脏读-读事务] 读取到值: {}（如果是999，则发生了脏读，因为写事务会回滚）", value);
        return value;
    }

    // ========================================
    // 2. READ_COMMITTED - 不可重复读演示
    // ========================================

    /**
     * 事务A：修改数据并提交
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int nonRepeatableRead_Write() {
        log.info("[不可重复读-写事务] 开始，当前值: {}", getCurrentValue());
        int newValue = 200;
        isolationTestRepository.updateValueByName("test_key", newValue);
        log.info("[不可重复读-写事务] 已修改为: {} 并提交", newValue);
        return newValue;
    }

    /**
     * 事务B：在同一事务中读取两次
     * 在 READ_COMMITTED 下，两次读取结果可能不同（不可重复读）
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int[] nonRepeatableRead_Read() {
        log.info("[不可重复读-读事务] 开始");

        int firstRead = getCurrentValue();
        log.info("[不可重复读-读事务] 第一次读取: {}", firstRead);

        // 在实际测试中，此时事务A修改并提交数据
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        int secondRead = getCurrentValue();
        log.info("[不可重复读-读事务] 第二次读取: {}（如果不同，则发生了不可重复读）", secondRead);

        return new int[]{firstRead, secondRead};
    }

    // ========================================
    // 3. REPEATABLE_READ - 幻读演示
    // ========================================

    /**
     * 事务A：插入新数据
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void phantomRead_Write() {
        log.info("[幻读-写事务] 开始");
        IsolationTest newRecord = IsolationTest.builder()
                .name("phantom_key")
                .value(888)
                .build();
        isolationTestRepository.save(newRecord);
        log.info("[幻读-写事务] 插入新记录: phantom_key = 888");
    }

    /**
     * 事务B：在同一事务中查询两次范围数据
     * 在 REPEATABLE_READ 下，MySQL InnoDB 通过 MVCC 防止了大部分幻读
     * 但某些特殊场景（如快照读和当前读混合）仍可能出现幻读
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public long phantomRead_Read() {
        log.info("[幻读-读事务] 开始");

        long firstCount = isolationTestRepository.count();
        log.info("[幻读-读事务] 第一次查询记录数: {}", firstCount);

        // 在实际测试中，此时事务A插入新数据
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        long secondCount = isolationTestRepository.count();
        log.info("[幻读-读事务] 第二次查询记录数: {}（如果不同，则发生了幻读）", secondCount);

        return secondCount - firstCount;
    }

    // ========================================
    // 4. SERIALIZABLE - 完全串行化
    // ========================================

    /**
     * SERIALIZABLE 下的所有操作都是串行的
     * 性能最差，但数据一致性最强
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int serializableDemo() {
        log.info("[SERIALIZABLE] 开始，当前值: {}", getCurrentValue());

        int currentValue = getCurrentValue();
        int newValue = currentValue + 1;
        isolationTestRepository.updateValueByName("test_key", newValue);

        log.info("[SERIALIZABLE] 从 {} 更新为 {}", currentValue, newValue);
        return newValue;
    }

    /**
     * 获取当前测试数据值
     */
    @Transactional(readOnly = true)
    public int getCurrentValue() {
        return isolationTestRepository.findByName("test_key")
                .map(IsolationTest::getValue)
                .orElse(0);
    }

    /**
     * 重置测试数据
     */
    @Transactional
    public void resetTestData() {
        isolationTestRepository.updateValueByName("test_key", 100);
        log.info("[隔离级别] 测试数据已重置: test_key = 100");
    }
}
