# 大事务处理最佳实践

## 核心原则

### 1. 事务最小化
- 只在真正需要事务的方法上加@Transactional
- 查询和校验在事务外完成
- 只有写操作在事务内完成

### 2. 远程调用外提
- RPC/HTTP/文件操作必须在事务外完成
- 先做远程调用，再开事务只做DB操作
- 避免事务中包含耗时操作

### 3. 批量操作优化
- 使用批量查询+批量更新，减少SQL次数
- 避免N+1循环查询
- 使用JPA的saveAll()方法

### 4. 非核心异步
- 日志、通知等非核心操作异步执行
- 事务只管核心数据一致性
- 使用@Async注解

### 5. 延迟加锁
- SELECT FOR UPDATE尽量靠近更新操作
- 缩小锁持有时间
- 减少其他事务等待时间

---

## 分片提交策略

### 场景
批量操作（>1000条）

### 实现方式
```java
public void chunkedCommit(List<User> users, int chunkSize) {
    for (int i = 0; i < users.size(); i += chunkSize) {
        List<User> chunk = users.subList(i, Math.min(i + chunkSize, users.size()));

        // 每个分片独立事务
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("chunk-tx-" + i);
        def.setTimeout(30); // 每个分片独立超时
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            userRepository.saveAll(chunk);
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            // 记录失败，继续处理下一批
        }
    }
}
```

### 优势
- 每个事务只持有连接很短时间
- 某批失败只回滚当前批次
- Undo Log及时回收

### 选择建议
- 数据量小（<1000）：单事务批量插入
- 数据量中等（1000-10000）：分批提交（每100-500条一批）
- 数据量大（>10000）：分批提交 + 异步处理

---

## 超时控制策略

### 场景
所有写事务

### 三层超时防护

#### 1. Spring层面超时
```java
@Transactional(timeout = 3) // 3秒超时
public void businessMethod() {
    // 业务逻辑
}
```

#### 2. 数据库层面超时
```sql
-- 查看当前超时设置
SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';

-- 设置锁等待超时（单位：秒）
SET GLOBAL innodb_lock_wait_timeout = 50;
```

#### 3. 连接池层面超时
```yaml
spring:
  datasource:
    hikari:
      maxLifetime: 1800000      # 30分钟
      idleTimeout: 600000       # 10分钟
      connectionTimeout: 30000  # 30秒
      leakDetectionThreshold: 30000  # 30秒
```

### 编程式动态超时
```java
public void dynamicTimeout(int timeoutSeconds) {
    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setTimeout(timeoutSeconds);
    def.setName("dynamic-timeout-tx");

    TransactionStatus status = transactionManager.getTransaction(def);
    try {
        // 业务操作
        transactionManager.commit(status);
    } catch (Exception e) {
        transactionManager.rollback(status);
    }
}
```

---

## 异步处理策略

### 场景
非实时要求的批量操作

### 实现方式
```java
// 核心业务在事务内
@Transactional
public void coreBusiness(Long userId) {
    // 核心业务逻辑
    User user = userRepository.findById(userId).orElseThrow();
    user.setStatus(2);
    userRepository.save(user);
}

// 非核心操作异步执行
@Async
public void asyncNotification(Long userId) {
    // 发送通知
    sendNotification(userId);
    // 记录日志
    saveLog(userId);
}
```

### 优势
- 接口响应快，用户体验好
- 连接占用少，系统吞吐量高
- 非核心操作失败不影响核心业务

---

## 生产环境优化建议

### 1. 代码层面优化
- **事务最小化**：只在真正需要事务的方法上加@Transactional
- **远程调用外提**：RPC/HTTP/文件操作必须在事务外完成
- **批量操作优化**：使用批量查询+批量更新，减少SQL次数
- **非核心异步**：日志、通知等非核心操作异步执行
- **延迟加锁**：SELECT FOR UPDATE尽量靠近更新操作

### 2. 数据库层面优化
- **索引优化**：确保查询条件有合适索引，避免全表扫描
- **锁粒度优化**：使用行锁而非表锁，减少锁冲突
- **批量提交**：大批量数据分片提交，每片100-500条
- **读写分离**：查询走从库，写操作走主库

### 3. 连接池配置优化
```yaml
spring:
  datasource:
    hikari:
      maximumPoolSize: 20-50      # 根据并发量调整
      minimumIdle: 5-10
      maxLifetime: 1800000        # 30分钟
      idleTimeout: 600000         # 10分钟
      connectionTimeout: 30000    # 30秒
      leakDetectionThreshold: 30000  # 30秒
```

### 4. 监控告警
- **事务耗时监控**：正常<200ms，关注200ms-1s，警告1s-5s，严重>5s
- **连接池监控**：活跃连接数、空闲连接数、等待线程数
- **长事务告警**：超过5秒的事务必须优化
- **慢SQL告警**：超过1秒的SQL必须优化

### 5. 应急处理
```sql
-- 长事务排查
SELECT * FROM information_schema.INNODB_TRX ORDER BY trx_started;

-- 锁等待排查
SELECT * FROM information_schema.INNODB_LOCK_WAITS;

-- 杀长事务（慎用）
KILL <thread_id>;
```

---

## 总结

### 关键原则
1. **事务最小化**：只在真正需要事务的方法上加@Transactional
2. **远程调用外提**：RPC/HTTP/文件操作必须在事务外完成
3. **批量操作优化**：使用批量查询+批量更新，减少SQL次数
4. **非核心异步**：日志、通知等非核心操作异步执行
5. **延迟加锁**：SELECT FOR UPDATE尽量靠近更新操作

### 选择策略
- **数据量小（<1000）**：单事务批量插入
- **数据量中等（1000-10000）**：分批提交（每100-500条一批）
- **数据量大（>10000）**：分批提交 + 异步处理
- **要求严格原子性**：单事务（但要注意连接超时）

### 生产环境建议
1. 使用Micrometer + Prometheus监控事务耗时
2. 设置告警：事务 > 1s为WARNING，> 5s为CRITICAL
3. 开启HikariCP的leakDetectionThreshold检测连接泄露
4. 定期排查长事务：`SELECT * FROM information_schema.INNODB_TRX`
5. 建立应急处理流程：发现长事务 → 排查原因 → 优化代码
