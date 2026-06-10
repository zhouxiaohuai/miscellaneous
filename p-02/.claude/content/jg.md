# Java 全栈学习 — 知识点总结提炼

> 项目：transaction-demo | Spring Boot 3.2.5 | Java 17/21 | 130+ 个源文件
> 最后更新：2026-06-10

---

## 零、技能图谱总纲（java26s 2026 版）

> 全栈能力全景，指导后续学习方向。已掌握的标注 ✅，待学习的标注 ⬜。

| 大类 | 子项 | 状态 | 项目对应 |
|------|------|------|---------|
| **Java 核心** | 语言基础 (Record/Sealed/Pattern Matching) | ✅ | 项目中已用 Record + Sealed |
| | Virtual Threads (Project Loom) | ⬜ | — |
| | JVM 内存/GC/调优 | ✅ | `jvm/` 14 个 Demo + GC 深度文档 |
| | 并发编程 (线程池/CompletableFuture) | ✅ | `concurrent/` 11 个 Demo，40+ 接口 |
| **Spring 生态** | Spring Boot 3.x 核心 | ✅ | 全项目基础 |
| | Spring Cloud 微服务 | ⬜ | — |
| | Spring Security + OAuth2 | ⬜ | — |
| **数据层** | MySQL + JPA + MyBatis | ✅ | `entity/` + `jpa/` + `mybatis/` |
| | Redis (5 结构/缓存/锁/限流) | ✅ | `redis/` 5 个 Service |
| | 缓存策略 (穿透/击穿/雪崩) | ✅ | 布隆过滤器 3 种实现 |
| **前端** | HTML/CSS/JS/TypeScript | ⬜ | — |
| | Vue 3 / React 18 | ⬜ | — |
| **DevOps** | Docker + K8s | ⬜ | — |
| | CI/CD (GitLab CI/Jenkins) | ⬜ | — |
| | 监控 (Prometheus+Grafana/ELK) | ⬜ | — |
| **架构** | 设计模式 (6 种) | ✅ | `architecture/` 18 个文件 |
| | 分布式 (锁/ID/事务) | ✅ | Redisson/雪花/TCC/Saga |
| | DDD 领域驱动设计 | ✅ | 聚合根/值对象/领域事件 |
| | **通用流程引擎** | ✅ | 状态机/SpEL/业务绑定 |
| **测试** | JUnit 5 + Mockito | ⬜ | — |
| | Testcontainers 集成测试 | ⬜ | — |
| **AI 辅助** | Claude Code / Copilot | ✅ | 当前使用中 |
| | Spring AI / RAG | ⬜ | — |

**掌握度统计**：✅ 12 项 / ⬜ 9 项 = 57%

---

## 项目全景

```
p-02 (130+ files)
├── 事务模块 (13 services + 4 controllers)     ← 核心深度
├── Redis 模块 (5 services)                    ← 已实现
├── 秒杀模块 (10 files)                        ← 场景驱动学习
├── 流程引擎模块 (21 files)                    ← 通用流程引擎
├── 并发模块 (11 files)                        ← 已完成 ✅（含 CAS 深度）
├── 架构模式模块 (18 files)                     ← 已实现
├── JVM 模块 (14 files)                        ← 已完成
├── RocketMQ 环境 (podman 容器)                ← 环境已搭建
└── 配置 + 实体 + ORM (15 files)               ← 基础设施
```

---

## 一、Spring 事务管理（核心深度）

### 1.1 事务生命周期 — 6 阶段

```
解析 → 拦截 → 获取事务 → 执行业务 → 提交/回滚 → 清理
```

| 阶段 | 做什么 | 关键类 |
|------|--------|--------|
| 解析 | 读取 @Transactional 注解属性 | `AnnotationTransactionAttributeSource` |
| 拦截 | AOP 代理拦截方法调用 | `TransactionInterceptor` |
| 获取 | 新建或加入已有事务 | `PlatformTransactionManager.getTransaction()` |
| 执行 | 运行业务代码 | 目标方法 |
| 提交/回滚 | 正常返回→提交，RuntimeException→回滚 | `commit()` / `rollback()` |
| 清理 | 清理 ThreadLocal，恢复挂起事务 | `TransactionSynchronizationManager` |

**代码体现**：`TransactionLifecycleService` — 声明式（@Transactional）和编程式（手动 get/commit/rollback）两种方式对比。

### 1.2 七种传播行为

| 传播行为 | 外部有事务 | 外部无事务 | 一句话 |
|---------|-----------|-----------|--------|
| **REQUIRED**（默认） | 加入 | 新建 | 共进退 |
| **REQUIRES_NEW** | 挂起，新建 | 新建 | 各自独立 |
| **SUPPORTS** | 加入 | 非事务 | 随大流 |
| **NOT_SUPPORTED** | 挂起，非事务 | 非事务 | 我不要事务 |
| **MANDATORY** | 加入 | **抛异常** | 必须有事务 |
| **NEVER** | **抛异常** | 非事务 | 必须没事务 |
| **NESTED** | Savepoint | 新建 | 部分回滚 |

**关键对比**：`REQUIRES_NEW` vs `NESTED`
- REQUIRES_NEW：完全独立事务，需额外数据库连接，外层回滚不影响已提交的内层
- NESTED：同一事务的 Savepoint，共用连接，外层回滚内层一起回滚

**代码体现**：`PropagationService` — 每种传播行为都有 outer/inner 方法组合演示。

### 1.3 四种隔离级别

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | MySQL 默认 |
|---------|------|-----------|------|-----------|
| READ_UNCOMMITTED | ✅可能 | ✅可能 | ✅可能 | 否 |
| READ_COMMITTED | ❌避免 | ✅可能 | ✅可能 | 否（PG默认） |
| REPEATABLE_READ | ❌避免 | ❌避免 | ✅可能 | **✅是** |
| SERIALIZABLE | ❌避免 | ❌避免 | ❌避免 | 否 |

> InnoDB 在 REPEATABLE_READ 下通过 MVCC + Next-Key Lock 大幅减少幻读。

**代码体现**：`IsolationService` — init/reset/getCurrent 配合并发测试。

### 1.4 十二种事务失效场景

这是项目最核心的知识点，每种都有错误代码 + 原因分析 + 正确修复：

| # | 场景 | 根因 | 修复 |
|---|------|------|------|
| 1 | 非 public 方法 | AOP 只拦截 public | 改 public |
| 2 | **自调用（this）** | 绕过代理 | 注入自身代理 / AopContext |
| 3 | catch 未抛出 | Spring 看不到异常 | catch 后 throw |
| 4 | checked 异常 | 默认只回滚 RuntimeException | `rollbackFor=Exception.class` |
| 5 | rollbackFor 配错 | 类型不匹配 | 统一用 `Exception.class` |
| 6 | MyISAM | 不支持事务 | 改 InnoDB |
| 7 | 未被 Spring 管理 | 手动 new | @Component + 注入 |
| 8 | **多线程** | ThreadLocal 跨线程丢失 | 避免事务内开线程 |
| 9 | propagation 错误 | NOT_SUPPORTED 挂起事务 | 正确选择传播行为 |
| 10 | final/static | CGLIB 无法重写 | 避免 |
| 11 | 类未被代理 | 不在扫描路径 | @Component + 正确包路径 |
| 12 | @Async + @Transactional | 切面顺序 | @Order 控制 |

**代码体现**：`TransactionFailService` — 12 个 failXX 方法 + 对应正确写法。

### 1.5 编程式事务 — 两种 API

| API | 抽象层级 | 适用场景 |
|-----|---------|---------|
| `TransactionTemplate` | 高（模板模式） | 简单编程式事务 |
| `PlatformTransactionManager` | 低（手动管理） | 极端复杂场景 |

**核心区别**：声明式事务 = Spring 帮你调 PlatformTransactionManager；编程式事务 = 你自己调。

**代码体现**：`ProgrammaticTransactionService` — template 有返回值/手动回滚，manager 完整流程/嵌套事务。

### 1.6 嵌套事务与 Savepoint

```java
// NESTED — 内层回滚不影响外层
@Transactional
public void outer() {
    save(user1);
    try { innerService.nested(); } catch (Exception e) { /* user1 仍提交 */ }
}

// 手动 Savepoint
Object sp = status.createSavepoint();
try { save(user2); } catch (Exception e) { status.rollbackToSavepoint(sp); }
transactionManager.commit(status);  // user1 保存成功
```

**代码体现**：`NestedTransactionService` — NESTED 对比 REQUIRES_NEW，手动 savepoint。

### 1.7 只读 / 超时 / 批量

| 特性 | 注解 | 效果 |
|------|------|------|
| 只读 | `@Transactional(readOnly=true)` | 跳过脏检查，可路由从库 |
| 超时 | `@Transactional(timeout=30)` | 超时触发 TransactionTimedOutException |
| 批量 | 编程式分片提交 | 每 N 条一个事务，失败只回滚当前片 |

**批量三种策略**：

| 策略 | 适用量级 | 风险 |
|------|---------|------|
| 单事务批量 | <1000 条 | 全部回滚 |
| 分批提交 | 1000~10000 条 | 当前批回滚 |
| 逐条提交 | 关键数据 | 精确控制 |

### 1.8 大事务优化 — 反模式 vs 正确做法

| 反模式 | 正确做法 | 优化原理 |
|--------|---------|---------|
| RPC 在事务内 | RPC 移到事务外 | 减少连接占用时间 |
| N+1 循环查询 | 批量查询+更新 | 减少 SQL 次数 |
| 整个方法加 @Transactional | 只包裹写操作 | 最小事务范围 |
| 提前 SELECT FOR UPDATE | 延迟加锁 | 减少锁持有时间 |
| 同步处理日志/通知 | 异步线程池 | 非核心操作异步化 |

**三层超时控制**：
1. Spring `@Transactional(timeout=N)` — 应用层
2. MySQL `innodb_lock_wait_timeout` — 数据库层
3. HikariCP `maxLifetime` — 连接池层

**代码体现**：`BigTransactionService` — 每个反模式都有对比代码 + 计时。

### 1.9 自定义事务框架 Tx

**解决 @Transactional 的三大局限**：

| 局限 | @Transactional | Tx |
|------|----------------|-----|
| 自调用失效 | 依赖 AOP 代理 | 静态方法，不依赖代理 |
| 方法级边界 | 代理只能前后拦截 | 手动控制事务范围 |
| 必须 public | CGLIB 限制 | 无代理限制 |

**架构**：
```
Tx（门面） → TxBuilder（链式配置） → TxContext（ApplicationContextAware） → PlatformTransactionManager
```

**使用方式**：
```java
Tx.readOnly(() -> findAll());           // 只读
Tx.writable(() -> save(entity));        // 读写
Tx.builder().writable().timeout(10)     // 链式配置
    .propagation(Propagation.REQUIRES_NEW)
    .executeWithoutResult(() -> { ... });
```

**核心原理**：`TxContext` 通过 `ApplicationContextAware` 拿到 Spring 容器，静态获取 `PlatformTransactionManager`，在当前线程内手动管理事务。

**代码体现**：`framework/` 包 5 个文件 + `CustomTxService` + `TxAdvancedService`。

---

## 二、ORM 双框架 — JPA + MyBatis

### 2.1 对比总结

| 维度 | JPA/Hibernate | MyBatis |
|------|--------------|---------|
| SQL 控制 | 自动生成（JPQL） | 手写 SQL |
| 适用场景 | 标准 CRUD | 复杂查询 |
| 事务管理 | JpaTransactionManager | DataSourceTransactionManager |
| 实体 | @Entity 注解 | 无侵入 |
| 映射 | 自动（驼峰） | XML/注解 |

**关键结论**：MyBatis 本身不管理事务，事务由 Spring 统一管理。JPA + MyBatis 共存时，`JpaTransactionManager` 兼容两者。

### 2.2 已掌握的 JPA 注解

```
@Entity @Table @Id @GeneratedValue    — 实体映射
@ManyToOne(FetchType.LAZY)            — 关联查询
@Enumerated(EnumType.STRING)          — 枚举存储
@Modifying @Query @Param              — 自定义更新/查询
JpaRepository + JpaSpecificationExecutor — 接口继承
```

### 2.3 已掌握的 MyBatis 映射

```
<resultMap>       — 结果映射（下划线→驼峰）
<select>/<insert>/<update> — SQL 定义
#{param}          — 参数绑定
balance + #{amount} — 相对更新（非覆盖）
```

**代码体现**：`entity/` 4 个实体 + `jpa/repository/` 4 个接口 + `mybatis/mapper/` 3 个接口 + 3 个 XML。

---

## 三、Redis 模块（完整实现）

### 3.1 五种数据结构

| 结构 | 核心操作 | 典型场景 |
|------|---------|---------|
| String | SET/GET/SETEX/SETNX/INCR/DECR | 缓存、计数器、分布式锁 |
| List | LPUSH/RPUSH/LPOP/RPOP/LRANGE | 消息队列、最新列表 |
| Hash | HSET/HGET/HGETALL/HINCRBY | 对象属性存储 |
| Set | SADD/SMEMBERS/SINTER/SUNION | 标签、共同好友 |
| ZSet | ZADD/ZRANGE/ZREVRANGE/ZINCRBY | 排行榜、延迟队列 |

### 3.2 缓存策略

| 策略 | 实现方式 | 解决问题 |
|------|---------|---------|
| **Cache Aside** | 读：缓存→DB→回填；写：删缓存 | 基础缓存模式 |
| **空值缓存** | 查询不到时缓存 null（短 TTL） | 缓存穿透 |
| **布隆过滤器** | BitSet + 双哈希，3 种实现 | 缓存穿透（大规模） |
| **互斥锁** | SETNX 抢锁重建缓存 | 缓存击穿 |
| **随机 TTL** | baseTTL + random jitter | 缓存雪崩 |

**布隆过滤器三种实现**：
1. `BloomFilter` — 标准版，BitSet + 双哈希
2. `CountingBloomFilter` — 计数器版，支持删除
3. `ScalableBloomFilter` — 可扩展版，自动增长

### 3.3 分布式锁 — 5 种演进

| # | 实现 | 问题 | 改进 |
|---|------|------|------|
| 1 | SETNX | 死锁风险 | 加 TTL |
| 2 | SETNX + TTL | 误删别人的锁 | 加 UUID 校验 |
| 3 | SETNX + UUID | 不可重入 | ThreadLocal 计数 |
| 4 | 可重入锁 | 业务场景 | 防重复提交 |
| 5 | 秒杀扣库存 | 非原子操作 | Lua 脚本原子化 |

**秒杀 Lua 脚本核心**：`检查库存 → 检查用户是否已购买 → 扣减库存 → 记录用户`，四步原子执行。

### 3.4 限流算法 — 4 种 + Lua 原子版

| 算法 | 原理 | 特点 |
|------|------|------|
| **固定窗口** | 计数器，窗口结束重置 | 简单，窗口边界突发 |
| **滑动窗口** | ZSet 时间戳分数 | 精确，内存较大 |
| **漏桶** | 固定速率流出 | 平滑，无法应对突发 |
| **令牌桶** | 固定速率放入令牌 | 平滑 + 允许突发 |

**进阶**：
- 多层限流：全局 → IP → 用户（三级过滤）
- Lua 原子版：集群环境下的滑动窗口/固定窗口/多层限流
- 动态配置：限流参数存 Redis，运行时读取/更新/重置

**代码体现**：`redis/` 包 5 个文件，`RedisController` 约 30 个接口。

---

## 四、设计模式（6 种）

### 4.1 策略模式 — 支付方式

```
PayStrategy（接口） ← AlipayStrategy / WxPayStrategy / BankPayStrategy
PayContext（上下文）← List<PayStrategy> 注入 → Map<channel, strategy> → executePay()
```

**Spring 最佳实践**：`List<T>` 自动注入 + `Collectors.toMap()` 构建策略 Map，新增支付方式只需加 `@Component`。

### 4.2 工厂模式 — 订单创建

```
Order（sealed interface）← NormalOrder / FlashSaleOrder / GroupOrder（record）
OrderCreator（接口）← NormalOrderCreator / FlashSaleOrderCreator / GroupOrderCreator
OrderFactory ← List<OrderCreator> → Map<type, creator> → create()
```

**Java 17 特性**：`sealed interface` 限制继承 + `record` 不可变 DTO。

### 4.3 观察者模式 — 事件驱动

```
OrderEvent（sealed interface）← OrderCreated / OrderPaid / OrderCancelled（record）
OrderEventPublisher → ApplicationEventPublisher.publishEvent()
OrderEventListener → @EventListener + @Async
```

**Spring Event 三要素**：事件定义（sealed record）、事件发布（ApplicationEventPublisher）、事件监听（@EventListener）。

### 4.4 模板方法 — 数据导出

```
ExportService<T>（抽象类）
  ├── final export()          ← 模板方法（不可重写）
  ├── abstract queryData()    ← 抽象步骤
  ├── abstract convertData()  ← 抽象步骤
  ├── hook needUpload()       ← 钩子（可选重写）
  └── ExcelExportService / CsvExportService / JsonExportService
```

**模板方法 vs 策略模式**：模板方法 = 流程固定、步骤可变（继承）；策略 = 算法整体替换（组合）。

### 4.5 责任链 — 订单校验

```
OrderFilter（接口）← filter() + getOrder()（优先级）
OrderFilterChain ← List<OrderFilter> 自动注入 → 按 order 排序 → 顺序执行 → 短路失败
```

**4 个过滤器**：参数校验(10) → 库存检查(20) → 风控检查(30) → 优惠券校验(40)

### 4.6 装饰器 — 服务增强

```
UserService（接口）← RealUserService / CacheDecorator / LogDecorator
使用：new LogDecorator(new CacheDecorator(realService))
```

**装饰器 vs 代理**：装饰器关注"增强功能"（可多层嵌套），代理关注"控制访问"。

### 模式对比速查

| 模式 | 解决什么 | Spring 实现 | 关键词 |
|------|---------|------------|--------|
| 策略 | 算法替换 | List 注入 + Map | 多选一 |
| 工厂 | 对象创建 | List 注入 + Map | 按类型创建 |
| 观察者 | 事件驱动 | Spring Event | 发布-订阅 |
| 模板方法 | 流程骨架 | 抽象类 + 继承 | 固定流程 |
| 责任链 | 多级处理 | List 注入 + 排序 | 链式过滤 |
| 装饰器 | 动态增强 | 接口 + 组合 | 层层包裹 |

---

## 五、分布式核心

### 5.1 分布式锁 — Redisson

| 用法 | 方法 | 场景 |
|------|------|------|
| 基本锁 | `lock()` / `unlock()` | 通用互斥 |
| 超时尝试 | `tryLock(wait, lease, unit)` | 非阻塞 |
| 看门狗 | 默认每 10s 续期 | 防止业务未完成锁过期 |
| 公平锁 | `getFairLock()` | 按请求顺序，防饥饿 |
| 读写锁 | `getReadWriteLock()` | 读共享、写独占 |

**SETNX vs Redisson vs ZooKeeper**：

| 方案 | 复杂度 | 可靠性 | 续期 | 推荐 |
|------|--------|--------|------|------|
| SETNX | 低 | 低（需手动处理） | 无 | 简单场景 |
| Redisson | 中 | 高（看门狗） | 自动 | **推荐** |
| ZooKeeper | 高 | 高（临时节点） | 自动 | 强一致场景 |

### 5.2 分布式 ID — 雪花算法

```
64 位结构：1 符号 + 41 时间戳 + 5 数据中心 + 5 机器 + 12 序列
每毫秒可生成 4096 个 ID，理论寿命 69 年
```

**三种实现**：
- `UuidIdGenerator` — UUID，无序，不适合做主键
- `SnowflakeIdGenerator` — 有序，高性能，需处理时钟回拨
- `IncrementIdGenerator` — AtomicLong，模拟 Redis INCR

### 5.3 分布式事务 — 三种方案

| 方案 | 原理 | 适用场景 | 复杂度 |
|------|------|---------|--------|
| **TCC** | Try 预留 → Confirm 确认 → Cancel 回滚 | 资金类（强一致） | 高 |
| **Saga** | 正向操作 + 补偿操作 | 长流程（最终一致） | 中 |
| **本地消息表** | 业务+消息同事务，轮询发送 | 异步解耦 | 低 |

**TCC 流程**：Try 冻结资源 → Confirm 扣减冻结 → Cancel 解冻
**Saga 流程**：正向 T1→T2→T3，补偿 C3→C2→C1
**本地消息表**：业务操作 + 写消息（同一事务）→ 轮询发送 → 消费确认

---

## 六、DDD 领域驱动设计

### 6.1 核心概念映射

| DDD 概念 | 代码实现 | 特征 |
|---------|---------|------|
| **值对象** | `Money`（record） | 无 ID、不可变、值相等 |
| **实体** | `OrderItem` | 有 ID、有业务逻辑 |
| **聚合根** | `OrderAggregate` | 维护不变量、控制内部访问 |
| **领域事件** | sealed interface Created/Paid/Cancelled | 状态变更通知 |
| **领域服务** | `PriceDomainService` | 跨聚合业务逻辑 |
| **应用服务** | `CreateOrderAppService` | 编排领域对象 |

### 6.2 聚合根 — 状态机

```
DRAFT → CONFIRMED → PAID → SHIPPED
                  ↘ CANCELLED
```

每个状态转换都有业务规则校验（不变量保护），违反则抛异常。

### 6.3 贫血模型 vs 充血模型

| 模型 | 特点 | 问题 |
|------|------|------|
| 贫血 | Entity 只有 getter/setter，逻辑在 Service | 业务逻辑分散 |
| 充血 | Entity 包含业务逻辑，Service 只做编排 | **DDD 推荐** |

---

## 七、JVM 内存模型、执行原理、垃圾回收（新增）

### 7.1 JVM 内存区域

```
┌─────────────────────────────────────────────────────────┐
│                      JVM 内存                            │
├──────────┬──────────┬──────────┬──────────┬─────────────┤
│ 程序计数器 │ 虚拟机栈  │ 本地方法栈 │    堆    │  方法区      │
│ (PC)     │ (Stack)  │ (Native) │ (Heap)   │ (Metaspace) │
├──────────┴──────────┴──────────┼──────────┼─────────────┤
│         线程私有                 │  线程共享  │  线程共享     │
└────────────────────────────────┴──────────┴─────────────┘
```

| 区域 | 线程 | 存储内容 | 异常 |
|------|------|---------|------|
| 程序计数器 | 私有 | 当前字节码行号 | 唯一不会 OOM |
| 虚拟机栈 | 私有 | 栈帧（局部变量表+操作数栈+动态链接+返回地址） | StackOverflowError / OOM |
| 本地方法栈 | 私有 | native 方法 | StackOverflowError / OOM |
| 堆 | 共享 | 对象实例、数组 | OOM |
| 方法区 | 共享 | 类信息、常量、静态变量、JIT 代码 | OOM: Metaspace |

**代码体现**：`MemoryStructureDemo` — 内存区域结构、堆分代、OOM 演示。

### 7.2 栈 vs 堆分配

| 对比项 | 虚拟机栈 | 堆 |
|--------|---------|-----|
| 存储内容 | 基本类型、引用地址 | 对象实例、数组 |
| 分配速度 | 极快（指针移动） | 较慢（需 GC 配合） |
| 生命周期 | 方法结束即释放 | GC 回收时释放 |
| 大小限制 | -Xss（默认 1MB） | -Xmx |

**逃逸分析**：JIT 编译器分析对象是否逃出方法作用域 → 不逃逸可栈上分配或标量替换。

**代码体现**：`StackHeapDemo` — 栈帧结构、堆分配、逃逸分析、StackOverflowError。

### 7.3 字符串常量池

```
JDK 7+：常量池在堆中（StringTable）
```

| 场景 | 结果 |
|------|------|
| `String s1 = "abc"; String s2 = "abc";` | s1 == s2（池中同一对象） |
| `String s3 = new String("abc");` | s1 != s3（堆中新对象） |
| `String s4 = s3.intern();` | s1 == s4（返回池中引用） |
| `new String("ab") + new String("cd")` | 不在池中（运行时拼接） |
| `"ab" + "cd"` | 在池中（编译期常量折叠） |

**代码体现**：`StringPoolDemo` — 6 种场景、intern 性能、JDK 版本差异。

### 7.4 直接内存

| 对比 | 堆内存 | 直接内存 |
|------|--------|---------|
| 分配 | 快 | 慢（系统调用） |
| 读写 | 需拷贝 | 零拷贝 |
| 回收 | GC 自动 | Cleaner（虚引用） |
| 限制 | -Xmx | -XX:MaxDirectMemorySize |

**代码体现**：`DirectMemoryDemo` — 性能对比、零拷贝、MappedByteBuffer。

### 7.5 四种引用类型

| 引用类型 | GC 回收条件 | 用途 | 典型场景 |
|---------|-----------|------|---------|
| 强引用 | 永不回收 | 普通对象 | `Object o = new Object()` |
| 软引用 | 内存不足时 | 缓存 | 图片缓存 |
| 弱引用 | 下次 GC | 缓存/映射 | WeakHashMap、ThreadLocal |
| 虚引用 | 随时可能 | 跟踪回收 | Cleaner、DirectByteBuffer |

**代码体现**：`ReferenceTypeDemo` — 四种引用演示 + WeakHashMap。

### 7.6 类加载过程

```
加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载
├─── 连接阶段 ───┤
```

| 阶段 | 做什么 |
|------|--------|
| 加载 | 通过全限定名获取字节流 → 方法区 → 生成 Class 对象 |
| 验证 | 文件格式、元数据、字节码、符号引用 |
| 准备 | static 变量分配内存并赋零值（final 常量直接赋值） |
| 解析 | 符号引用 → 直接引用 |
| 初始化 | 执行 `<clinit>()`（static 赋值 + static {} 块） |

**代码体现**：`ClassLoadDemo` — 准备 vs 初始化、线程安全单例、被动引用、类卸载条件。

### 7.7 类加载器与双亲委派

```
Bootstrap ClassLoader  → java.lang.*（C++ 实现）
  └─ Extension ClassLoader → javax.*
      └─ Application ClassLoader → classpath
          └─ Custom ClassLoader → 自定义路径
```

**双亲委派**：先委托父加载器，父无法加载才自己尝试 → 保证核心类不被篡改。

**打破双亲委派**：SPI（线程上下文 ClassLoader）、Tomcat（重写 loadClass）、热部署。

**代码体现**：`ClassLoaderDemo` — 层级关系、双亲委派流程、类唯一性、SPI。

### 7.8 JIT 编译

```
字节码 → 解释执行（逐条翻译，启动快）
      → JIT 编译（热点代码→机器码，执行快）
        ├─ C1（快速编译，简单优化）
        └─ C2（深度优化，编译较慢）
```

| 优化手段 | 作用 |
|---------|------|
| 方法内联 | 消除方法调用开销 |
| 逃逸分析 | 标量替换 / 栈上分配 / 锁消除 |
| 常量折叠 | 编译期计算常量 |

**代码体现**：`JitCompileDemo` — 解释 vs JIT、方法内联、逃逸分析、分层编译。

### 7.9 GC 算法

| 算法 | 原理 | 优点 | 缺点 | 适用 |
|------|------|------|------|------|
| 标记-清除 | 标记存活，清除未标记 | 简单 | 碎片 | CMS 老年代 |
| 标记-复制 | 存活对象复制到另一块 | 无碎片 | 50% 浪费 | 新生代 |
| 标记-整理 | 存活对象向一端移动 | 无碎片 | 移动开销 | 老年代 |
| 分代收集 | 新生代复制 + 老年代整理 | 平衡 | — | **主流** |

**对象分配流程**：新对象 → Eden → Minor GC → Survivor → 年龄 ≥ 15 → Old。

**代码体现**：`GcAlgorithmDemo` — 四种算法、对象分配流程、GC 触发条件。

### 7.10 GC 收集器

| 收集器 | 类型 | 算法 | 参数 | 场景 |
|--------|------|------|------|------|
| Serial | 单线程 | 复制/标记-整理 | `+UseSerialGC` | 小堆 |
| Parallel | 多线程吞吐量 | 复制/标记-整理 | `+UseParallelGC` | JDK 8 默认 |
| CMS | 并发低延迟 | 标记-清除 | `+UseConcMarkSweepGC` | Web 服务 |
| G1 | 整堆可预测 | Region 化 | `+UseG1GC` | JDK 9+ 默认 |
| ZGC | 超低延迟 | 着色指针 | `+UseZGC` | JDK 17+ 推荐 |

**G1 核心**：堆划分为 Region，优先回收价值最大的 Region（Garbage First）。

**代码体现**：`GcCollectorDemo` — 七种收集器对比、选择建议、G1 Region 机制。

### 7.11 内存泄漏场景

| 场景 | 根因 | 解决 |
|------|------|------|
| 静态集合 | 静态变量生命周期 = JVM 生命周期 | clear() / WeakHashMap |
| 未关闭资源 | 底层资源不释放 | try-with-resources |
| ThreadLocal | 线程池中 value 强引用不回收 | remove() |
| 内部类 | 非静态内部类持有外部类引用 | 静态内部类 + 弱引用 |
| 缓存无过期 | 只进不出 | Caffeine / Redis |

**代码体现**：`MemoryLeakDemo` — 5 种泄漏场景 + 排查方法（jstat/jmap/MAT）。

### 7.12 GC 调优

**常用参数**：
```
-Xms / -Xmx          堆大小（建议相等）
-Xmn                  新生代大小
-XX:MaxGCPauseMillis  目标停顿时间（G1/ZGC）
-XX:+UseG1GC          选择收集器
-Xlog:gc*:file=gc.log GC 日志（JDK 9+）
```

**调优工具**：jstat、jmap、jcmd、Arthas、MAT、GCViewer。

**代码体现**：`GcTuningDemo` — 参数速查、日志分析、工具介绍、调优案例。

### 7.13 对象回收钩子：finalize vs Cleaner

| 方案 | 确定性 | 复活对象 | 性能 | 状态 |
|------|--------|---------|------|------|
| finalize() | 不确定 | 可以 | 差 | **已废弃** |
| PhantomReference | 确定 | 不可以 | 好 | 低级 API |
| Cleaner | 确定 | 不可以 | 好 | **推荐** |

**代码体现**：`FinalizeVsCleanerDemo` — finalize 问题、Cleaner 演示、PhantomReference。

---

## 八、并发编程模块（由浅入深）

### 8.1 线程基础

| 知识点 | 核心内容 | 代码体现 |
|--------|---------|---------|
| 线程创建 | 3 种方式：Thread/Runnable/Callable+FutureTask | `ThreadBasicDemo.threadCreation()` |
| 线程生命周期 | 6 种状态：NEW→RUNNABLE→BLOCKED/WAITING/TIMED_WAITING→TERMINATED | `ThreadBasicDemo.threadLifecycle()` |
| 线程控制 | sleep（不释放锁）、join（wait 实现）、interrupt（中断机制） | `ThreadBasicDemo.threadControl()` |
| synchronized | 对象锁/类锁、可重入性、代码块优于方法 | `SynchronizedDemo.synchronizedBasics()` |
| wait/notify | 线程通信、虚假唤醒（while 循环）、必须在 synchronized 内 | `SynchronizedDemo.waitNotify()` |
| 生产者消费者 | 有界缓冲区、wait/notify 配合、BlockingQueue 替代方案 | `SynchronizedDemo.producerConsumer()` |

### 8.2 线程池

**ThreadPoolExecutor 7 大参数（餐厅类比）**：

```
corePoolSize    = 正式员工（常驻，即使空闲也不解雇）
maximumPoolSize = 最大员工数（正式 + 临时）
keepAliveTime   = 临时员工空闲多久后解雇
workQueue       = 等候区（顾客排队）
threadFactory   = 员工招聘方式（线程命名）
handler         = 满员处理方式（拒绝策略）
```

**4 种内置线程池**：

| 类型 | 特点 | ⚠️ 隐患 |
|------|------|---------|
| FixedThreadPool | 固定线程数，LinkedBlockingQueue | 队列无界 → OOM |
| SingleThreadExecutor | 单线程，顺序执行 | 队列无界 → OOM |
| CachedThreadPool | 无核心线程，Integer.MAX_VALUE | 线程数无上限 → OOM |
| ScheduledThreadPool | 定时/周期执行 | 队列无界 → OOM |

**4 种拒绝策略**：

| 策略 | 行为 | 推荐 |
|------|------|------|
| AbortPolicy | 抛 RejectedExecutionException | ✅ 推荐（调用方感知） |
| CallerRunsPolicy | 调用者线程执行 | 降速但不丢任务 |
| DiscardPolicy | 静默丢弃 | ❌ 危险 |
| DiscardOldestPolicy | 丢弃最老任务 | ❌ 危险 |

**池大小公式**：
- CPU 密集型：N + 1（N = CPU 核心数）
- IO 密集型：N × 2 或 N / (1 - 阻塞系数)
- 最终需压测调整

**代码体现**：`ThreadPoolDemo`（7 参数/内置池/拒绝策略/监控）、`ThreadPoolPracticeDemo`（调优/关闭/编排）

### 8.3 CompletableFuture

**创建方式**：

| 方法 | 返回值 | 默认线程池 |
|------|--------|-----------|
| supplyAsync(fn) | CompletableFuture<T> | ForkJoinPool.commonPool() |
| runAsync(fn) | CompletableFuture<Void> | ForkJoinPool.commonPool() |
| supplyAsync(fn, pool) | CompletableFuture<T> | 自定义（推荐） |

**链式调用**：

| 方法 | 签名 | 类比 |
|------|------|------|
| thenApply(f) | T → R | Stream.map() |
| thenAccept(f) | T → void | Stream.forEach() |
| thenRun(f) | () → void | 不关心结果 |
| thenCompose(f) | T → CF<R> | Stream.flatMap() |

**组合编排**：

| 方法 | 场景 |
|------|------|
| thenCombine(cf, fn) | 两任务并行 → 合并结果 |
| allOf(cf...) | N 任务 → 全部完成 |
| anyOf(cf...) | N 任务 → 最先完成 |

**异常处理**：

| 方法 | 行为 | 类比 |
|------|------|------|
| exceptionally(f) | 异常降级 | catch |
| handle(f) | 成功或失败都处理 | try-catch |
| whenComplete(f) | 监听结果，不改变 | finally |
| completeOnTimeout(v, t) | 超时返回默认值 | JDK 9+ |
| orTimeout(t) | 超时抛 TimeoutException | JDK 9+ |

**实战场景**：
- **并行查询**：用户+订单+积分+推荐，allOf 聚合，耗时取最慢的（350ms → 150ms）
- **秒杀异步化**：Redis 预检(同步) → DB 扣减(同步) → 通知+统计(异步不阻塞)
- **超时重试**：completeOnTimeout + 指数退避重试

**代码体现**：`CompletableFutureDemo`（创建/链式/组合/异常）、`CompletableFuturePracticeDemo`（并行查询/超时重试/秒杀异步化）

### 8.4 并发工具

| 工具 | 本质 | 场景 | 特点 |
|------|------|------|------|
| CountDownLatch | 倒计数器 | 主线程等 N 个子任务完成 | 一次性 |
| CyclicBarrier | 循环屏障 | N 个线程互相等待后一起继续 | 可重用 |
| Semaphore | 信号量 | 控制并发数（连接池、限流） | 可动态 |

**代码体现**：`ConcurrentUtilityDemo` — 三个工具各一个完整演示。

### 8.5 锁机制

| 锁 | 特点 | 适用场景 |
|----|------|---------|
| synchronized | JVM 内置、自动释放、简单 | 通用场景（优先选择） |
| ReentrantLock | 手动释放、可中断、tryLock、公平锁 | 需要高级特性时 |
| ReadWriteLock | 读共享写独占 | 读多写少 |
| StampedLock | 乐观读 + 悲观读 + 写锁 | 读 >> 写（99:1） |

**锁选择决策树**：
```
读写比例？
├── 读 ≈ 写 → synchronized / ReentrantLock
├── 读 > 写 → ReadWriteLock
└── 读 >> 写 → StampedLock 乐观读
```

**代码体现**：`LockDemo` — ReentrantLock/ReadWriteLock/StampedLock 各一个完整演示。

### 8.6 原子类与并发集合

**原子类**：

| 类 | 特点 | 场景 |
|----|------|------|
| AtomicInteger/Long | CAS 无锁 | 计数器（低并发） |
| AtomicReference | 对象引用原子更新 | 状态机 |
| AtomicStampedReference | 版本号解决 ABA | 防 ABA |
| LongAdder | 分段累加，高并发性能好 | 计数器（高并发） |

**CAS 原理**：Compare-And-Swap，CPU 指令级支持，无锁自旋。

**ConcurrentHashMap 演进**：
- JDK 7：Segment 分段锁（默认 16 段，并发度 16）
- JDK 8：CAS + synchronized（锁单个桶，并发度 = 数组长度）

**代码体现**：`AtomicDemo` — LongAdder/ConcurrentHashMap 各有演示。

### 8.6.1 CAS 深度解析

**CAS 三要素**：

| 要素 | 含义 | 说明 |
|------|------|------|
| V (Value) | 内存中的值 | 要修改的变量 |
| A (Expected) | 预期的旧值 | 读取时的值 |
| B (New Value) | 要设置的新值 | 更新后的值 |

**CAS 执行流程**：
```
读取 V → 比较 V == A ? → 是：V = B（成功）→ 否：返回失败（重试）
```

**CAS 底层调用链**：
```
Java: AtomicInteger.incrementAndGet()
  ↓
JDK: Unsafe.getAndAddInt()
  ↓
JNI: Atomic::cmpxchg()
  ↓
CPU: lock cmpxchg 指令（原子操作）
```

**CAS vs synchronized**：

| 对比项 | CAS | synchronized |
|--------|-----|--------------|
| 类型 | 乐观锁（无锁） | 悲观锁（有锁） |
| 阻塞 | 不阻塞，自旋重试 | 阻塞线程 |
| 上下文切换 | 无 | 有 |
| 适用场景 | 竞争不激烈 | 竞争激烈 |
| CPU 开销 | 竞争大时空转 | 阻塞时让出 CPU |

**ABA 问题与解决**：

| 方案 | 原理 | 适用场景 |
|------|------|---------|
| AtomicStampedReference | 版本号（int stamp） | 需要知道改了几次 |
| AtomicMarkableReference | 布尔标记 | 只需知道有没有改过 |

**CAS 实战应用**：
- 自旋锁：`AtomicBoolean` + CAS 循环
- 无锁栈/队列：CAS 更新头节点
- 原子计数器：`AtomicInteger/Long`
- 高并发累加：`LongAdder`（分段减少竞争）

**代码体现**：`CasDemo` — CAS原理/底层实现/volatile作用/ABA问题/自旋锁。

### 8.7 并发模块文件结构

```
src/main/java/com/example/transaction/concurrent/
├── basic/                              # 线程基础（入门）
│   ├── ThreadBasicDemo.java            # 创建/生命周期/控制
│   └── SynchronizedDemo.java           # synchronized/wait-notify/生产者消费者
│
├── pool/                               # 线程池（核心）
│   ├── ThreadPoolDemo.java             # 7参数/内置池/拒绝策略/监控
│   └── ThreadPoolPracticeDemo.java     # 调优/关闭/编排
│
├── completable/                        # CompletableFuture（进阶）
│   ├── CompletableFutureDemo.java      # 创建/链式/组合/异常
│   └── CompletableFuturePracticeDemo.java # 并行查询/超时重试/秒杀异步化
│
├── utility/                            # 并发工具（拓展）
│   └── ConcurrentUtilityDemo.java      # CountDownLatch/CyclicBarrier/Semaphore
│
├── lock/                               # 锁机制（深入）
│   └── LockDemo.java                   # ReentrantLock/ReadWriteLock/StampedLock
│
├── atomic/                             # 原子类与并发集合（深入）
│   └── AtomicDemo.java                 # LongAdder/ConcurrentHashMap
│
├── cas/                                # CAS 深度（新增）
│   └── CasDemo.java                    # CAS原理/底层实现/ABA问题/自旋锁
│
└── controller/
    └── ConcurrentDemoController.java   # ~40 个 REST 接口
```

### 8.8 并发模块接口速查

| 路径 | 说明 |
|------|------|
| `/api/concurrent/overview` | 全部接口索引（学习路径） |
| `/api/concurrent/basic/creation` | 线程创建 3 种方式 |
| `/api/concurrent/basic/lifecycle` | 线程生命周期 6 种状态 |
| `/api/concurrent/basic/control` | sleep/join/interrupt |
| `/api/concurrent/sync/basics` | synchronized 基础 |
| `/api/concurrent/sync/wait-notify` | wait/notify 机制 |
| `/api/concurrent/sync/producer-consumer` | 生产者消费者 |
| `/api/concurrent/pool/parameters` | 7 大参数详解 |
| `/api/concurrent/pool/built-in` | 4 种内置线程池 |
| `/api/concurrent/pool/rejection` | 4 种拒绝策略 |
| `/api/concurrent/pool/monitoring` | 运行时监控 |
| `/api/concurrent/pool/sizing` | 池大小选择 |
| `/api/concurrent/pool/shutdown` | 优雅关闭 |
| `/api/concurrent/pool/orchestration` | 任务编排 |
| `/api/concurrent/completable/creation` | 创建方式 |
| `/api/concurrent/completable/chaining` | 链式调用 |
| `/api/concurrent/completable/combination` | 组合编排 |
| `/api/concurrent/completable/exception` | 异常处理 |
| `/api/concurrent/completable/parallel-query` | 并行查询实战 |
| `/api/concurrent/completable/timeout-retry` | 超时重试 |
| `/api/concurrent/completable/seckill-async` | 秒杀异步化 |
| `/api/concurrent/utility/countdown-latch` | CountDownLatch |
| `/api/concurrent/utility/cyclic-barrier` | CyclicBarrier |
| `/api/concurrent/utility/semaphore` | Semaphore |
| `/api/concurrent/lock/reentrant` | ReentrantLock |
| `/api/concurrent/lock/read-write` | ReadWriteLock |
| `/api/concurrent/lock/stamped` | StampedLock |
| `/api/concurrent/atomic/basics` | 原子类基础 |
| `/api/concurrent/atomic/long-adder` | LongAdder |
| `/api/concurrent/atomic/concurrent-hashmap` | ConcurrentHashMap |
| `/api/concurrent/cas/basics` | CAS 基础概念 |
| `/api/concurrent/cas/internals` | CAS 底层原理 |
| `/api/concurrent/cas/vs-synchronized` | CAS vs synchronized |
| `/api/concurrent/cas/spin` | CAS 自旋重试 |
| `/api/concurrent/cas/aba` | ABA 问题与解决 |
| `/api/concurrent/cas/practice` | CAS 实战应用 |

---

## 八之一、Spring Boot 配置要点

### 7.1 依赖栈

```xml
spring-boot-starter-web          # REST API
spring-boot-starter-data-jpa     # ORM (Hibernate)
mybatis-spring-boot-starter      # SQL 映射
mysql-connector-j                # MySQL 驱动
spring-boot-starter-data-redis   # Redis
redisson-spring-boot-starter     # 分布式锁
lombok                           # 代码简化
```

### 7.2 关键配置

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20       # 最大连接数
      minimum-idle: 5             # 最小空闲
  jpa:
    hibernate:
      ddl-auto: none              # 生产环境不用自动建表
    open-in-view: false           # 关闭 OSIV
  data:
    redis:
      host: localhost
      port: 6379
```

### 7.3 Redis 配置类

```java
RedisTemplate<String, Object>
  ├── key: StringRedisSerializer
  └── value: Jackson2JsonRedisSerializer（支持 Java 8 时间类型）
```

---

## 八、API 接口速查

### 事务接口 (`/api/tx`)

| 路径 | 说明 |
|------|------|
| `/lifecycle/declarative` | 声明式事务提交 |
| `/lifecycle/declarative-rollback` | 声明式事务回滚 |
| `/lifecycle/programmatic` | 编程式事务提交 |
| `/propagation/{type}` | 7 种传播行为 |
| `/isolation/{init\|current\|reset}` | 隔离级别测试 |
| `/fail/01` ~ `/fail/12` | 12 种失效场景 |
| `/programmatic/*` | 编程式事务 |
| `/nested/*` | 嵌套事务 |
| `/readonly` | 只读事务 |
| `/timeout/*` | 超时事务 |
| `/batch/*` | 批量事务 |
| `/big/*` | 大事务优化 |
| `/overview` | 全部接口索引 |

### 自定义 Tx 框架 (`/api/custom-tx`)

| 路径 | 说明 |
|------|------|
| `/self-invoke-tx` | Tx 解决自调用 |
| `/minimal-tx` | 最小事务范围 |
| `/readonly` / `/writable` | 只读/读写 |
| `/builder` | 链式配置 |
| `/complete-flow` | 完整业务流程 |

### Redis 接口 (`/api/redis`)

| 路径 | 说明 |
|------|------|
| `/basic/{string\|list\|hash\|set\|zset\|common}` | 5 种数据结构 |
| `/cache/{read\|write\|penetration\|bloom\|rebuild\|breakdown\|avalanche}` | 缓存策略 |
| `/lock/{simple\|ttl\|uuid\|order\|seckill\|seckill-v2}` | 分布式锁 |
| `/rate-limit/{fixed\|sliding\|token\|ip\|global\|multi\|cluster-*}` | 限流算法 |

### 架构接口 (`/api/architecture`)

| 路径 | 说明 |
|------|------|
| `/strategy/pay?channel=ALIPAY&amount=100` | 策略模式 |
| `/factory/order` (POST) | 工厂模式 |
| `/observer/order-created` | 观察者模式 |
| `/chain/validate` | 责任链模式 |
| `/template/export` | 模板方法 |
| `/decorator/info` | 装饰器模式 |
| `/distributed-id?type=snowflake` | 分布式 ID |
| `/ddd/create-order` | DDD 下单 |

### 高级 Tx 接口 (`/api/tx-advanced`)

| 路径 | 说明 |
|------|------|
| `/big/chunked` | 分片提交 |
| `/big/minimal` | 最小事务范围 |
| `/nested/requires-new` | REQUIRES_NEW |
| `/nested/nested` | NESTED |
| `/nested/combined` | 组合嵌套 |

### JVM 接口 (`/api/jvm`)

**内存模型**：

| 路径 | 说明 |
|------|------|
| `/memory/structure` | JVM 内存区域结构 |
| `/memory/heap-generations` | 堆内存分代 |
| `/memory/stack-heap` | 栈 vs 堆 + 逃逸分析 |
| `/memory/stack-overflow` | StackOverflowError |
| `/memory/string-pool` | 字符串常量池（6 种场景） |
| `/memory/string-pool/intern-perf` | intern 性能测试 |
| `/memory/string-pool/jdk-diff` | JDK 版本差异 |
| `/memory/direct` | 直接内存 + 零拷贝 |
| `/memory/reference?type=strong` | 四种引用（strong/soft/weak/phantom） |

**执行原理**：

| 路径 | 说明 |
|------|------|
| `/execution/bytecode` | 字节码指令 + 方法分派 + Lambda |
| `/execution/classload` | 类加载过程 + 被动引用 + 类卸载 |
| `/execution/classload/thread-safety` | 类加载线程安全（单例） |
| `/execution/classloader` | 类加载器 + 双亲委派 + SPI |
| `/execution/jit` | JIT 编译 + 内联 + 逃逸分析 + 分层编译 |

**垃圾回收**：

| 路径 | 说明 |
|------|------|
| `/gc/algorithm` | GC 算法 + 对象分配 + 触发条件 |
| `/gc/collector` | 七种收集器 + 选择建议 + G1 Region |
| `/gc/leak?type=static` | 内存泄漏（static/resource/threadlocal/inner/cache） |
| `/gc/tuning` | GC 调优参数 + 日志 + 工具 + 案例 |
| `/gc/finalize` | finalize vs Cleaner vs PhantomReference |

---

## 八、秒杀模块（场景驱动学习）

### 8.1 秒杀系统架构

```
用户请求 → 风控过滤 → Redis 预检 → 下单扣减 → 支付 → 超时取消
```

**当前实现状态**：✅ Redis 全链路已通 / ⚠️ RocketMQ 待接入 / ⚠️ schema.sql 缺建表

**核心挑战**：

| 问题 | 场景 | 后果 |
|------|------|------|
| 超卖 | 100件商品卖了120件 | 无法发货，赔偿 |
| 重复下单 | 同一用户下了2单 | 库存浪费 |
| 库存不一致 | 缓存和数据库不一致 | 超卖或少卖 |
| 恶意刷单 | 脚本抢购 | 正常用户买不到 |
| 系统崩溃 | 瞬时流量太大 | 全站不可用 |

### 8.2 核心流程

```
用户点击"秒杀"
    ↓
Redis Lua 原子操作（判断库存 + 判断重复 + 扣减库存）
    ↓
成功 → 数据库创建订单 + 乐观锁扣减库存
失败 → 返回"已售罄"或"已抢过"
```

**Lua 脚本（核心中的核心）**：

```lua
-- KEYS[1] = 库存 key
-- KEYS[2] = 已购用户 set key
-- ARGV[1] = userId

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
```

### 8.3 优先级与解决方案

| 优先级 | 问题 | 方案 | 学习价值 |
|--------|------|------|---------|
| **P0** | 超时未支付库存不释放 | 延迟队列（Redis ZSet） | ⭐⭐⭐⭐⭐ |
| **P0** | 无限制刷接口 | 滑动窗口限流（Redis ZSet） | ⭐⭐⭐⭐⭐ |
| **P1** | 快速点击重复请求 | 分布式锁（SETNX） | ⭐⭐⭐⭐ |
| **P1** | 乐观锁冲突 | 悲观锁（SELECT FOR UPDATE） | ⭐⭐⭐⭐ |
| **P2** | 库存不一致 | 回补机制 + 定时对账 | ⭐⭐⭐ |

### 8.4 P0-1：延迟队列（超时取消 + 库存回补）

**方案：Redis ZSet 延迟队列**

```
秒杀成功 → 订单入队 Redis ZSet（score = 过期时间戳）
    ↓
定时任务每秒扫描 ZSet → 找到已过期的订单
    ↓
取消订单 → 回补 Redis 库存 → 回补数据库库存
```

**原理**：
- ZSet 有 score（分数）特性，可以按分数排序
- 把"过期时间戳"作为 score，订单号作为 value
- 定时任务扫描 score < 当前时间戳 的元素，就是已过期的订单

**关键代码**：
```java
// 入队
long expireTime = System.currentTimeMillis() + ORDER_TIMEOUT_MS;
redisTemplate.opsForZSet().add(DELAY_QUEUE_KEY, value, expireTime);

// 扫描过期订单
Set<String> expiredOrders = redisTemplate.opsForZSet()
        .rangeByScore(DELAY_QUEUE_KEY, 0, now);
```

**生产环境替代方案**：RocketMQ 延迟消息（可靠性更高）

### 8.5 P0-2：滑动窗口限流

**方案：Redis ZSet 滑动窗口**

```
用户请求 → 检查该用户在 N 秒内是否超过 M 次
    ↓
超过 → 拒绝，返回"操作太频繁"
未超过 → 放行
```

**为什么用 ZSet 而不是 INCR + EXPIRE？**

| 方案 | 原理 | 问题 |
|------|------|------|
| **固定窗口** | INCR + EXPIRE | 窗口边界处可绕过限制 |
| **滑动窗口** | ZSet + 时间戳 | 统计"过去N秒"的精确请求数 |

**固定窗口边界问题**：
```
|--------窗口1--------|--------窗口2--------|
  1 2 3 4 5            1 2 3 4 5
  窗口末尾5次 + 窗口开头5次 = 1秒内10次，绕过限制
```

**滑动窗口原理**：
```lua
-- 1. 删除窗口外的旧记录
redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])

-- 2. 统计窗口内的请求数
local count = redis.call('zcard', KEYS[1])

-- 3. 判断是否超过限制
if count >= tonumber(ARGV[3]) then
    return 0
end

-- 4. 未超过限制，添加当前请求
local member = ARGV[2] .. ':' .. math.random(1000000)
redis.call('zadd', KEYS[1], ARGV[2], member)

return 1
```

### 8.6 P1-1：防重复提交

**方案：Redis 分布式锁（SETNX）**

```
用户快速点击3次"秒杀"按钮
    ↓
请求1 → 尝试获取锁 → 成功 → 进入秒杀流程
请求2 → 尝试获取锁 → 失败 → 拒绝："请求处理中"
请求3 → 尝试获取锁 → 失败 → 拒绝："请求处理中"
```

**关键代码**：
```java
// 尝试获取锁
Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(key, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);

// 释放锁（finally 中调用）
redisTemplate.delete(key);
```

**锁的设计**：
- key：`seckill:lock:{userId}:{productId}`
- 过期时间：5 秒（防止死锁）
- 无论成功失败，finally 中释放锁

### 8.7 P1-2：乐观锁 vs 悲观锁

| 对比项 | 乐观锁 | 悲观锁 |
|--------|--------|--------|
| 实现方式 | `WHERE version = ?` | `SELECT FOR UPDATE` |
| 锁定时机 | 更新时才检查 | 查询时就锁定 |
| 冲突处理 | 失败重试 | 排队等待 |
| 适用场景 | 低冲突 | 高冲突 |
| 并发性能 | 高 | 低 |

**乐观锁**：
```java
@Modifying
@Query("UPDATE SeckillProduct p SET p.stock = p.stock - 1, p.version = p.version + 1 " +
       "WHERE p.id = :id AND p.stock > 0 AND p.version = :version")
int deductStockOptimistic(@Param("id") Long id, @Param("version") Integer version);
```

**悲观锁**：
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM SeckillProduct p WHERE p.id = :id")
Optional<SeckillProduct> findByIdWithLock(@Param("id") Long id);
```

### 8.8 秒杀模块文件结构

```
src/main/java/com/example/transaction/seckill/
├── entity/
│   ├── SeckillProduct.java              # 秒杀商品（含乐观锁 version）
│   └── SeckillOrder.java                # 秒杀订单
├── repository/
│   ├── SeckillProductRepository.java    # 乐观锁 + 悲观锁扣减
│   └── SeckillOrderRepository.java      # 判断重复购买
├── service/
│   ├── SeckillService.java              # 核心秒杀逻辑
│   ├── SeckillDelayQueueService.java    # 延迟队列（超时取消）
│   ├── SeckillRateLimitService.java     # 滑动窗口限流
│   └── SeckillDuplicateService.java     # 防重复提交
├── consumer/
│   └── SeckillOrderCancelConsumer.java  # RocketMQ 消费者（待启用）
└── controller/
    └── SeckillController.java           # 演示接口
```

### 8.9 秒杀接口速查

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/seckill/init` | POST | 初始化测试环境（创建商品 + 预热库存） |
| `/api/seckill/buy` | POST | 秒杀下单（乐观锁版本） |
| `/api/seckill/buy-pessimistic` | POST | 秒杀下单（悲观锁版本） |
| `/api/seckill/stock/{id}` | GET | 查询剩余库存 |
| `/api/seckill/check` | GET | 查询是否已抢购 |
| `/api/seckill/rate-limit` | GET | 查询限流状态 |
| `/api/seckill/delay-queue` | GET | 查询延迟队列状态 |

### 8.10 秒杀模块知识点总结

| 知识点 | 代码体现 | 核心原理 |
|--------|----------|----------|
| Redis Lua 原子操作 | SeckillService | 多步操作原子执行，防止超卖 |
| Redis ZSet 延迟队列 | SeckillDelayQueueService | score = 过期时间戳，定时扫描 |
| Redis ZSet 滑动窗口 | SeckillRateLimitService | 统计过去N秒的精确请求数 |
| Redis SETNX 分布式锁 | SeckillDuplicateService | 防止重复提交 |
| 乐观锁（CAS） | SeckillProductRepository | WHERE version = ? |
| 悲观锁（行锁） | SeckillProductRepository | SELECT FOR UPDATE |
| 定时任务 | SeckillDelayQueueService | @Scheduled(fixedRate = 1000) |

### 8.11 秒杀模块待完善项（Gap Analysis）

| # | 问题 | 当前状态 | 修复方案 | 优先级 |
|---|------|---------|---------|--------|
| 1 | RocketMQ 未接入 | Redis ZSet 本地回退 | 注入 `RocketMQTemplate`，发送延迟消息到 `seckill-order-cancel` topic | P1 |
| 2 | Consumer 已禁用 | `@Component` 被注释 | 启用 `SeckillOrderCancelConsumer`，实现真正的 MQ 消费 | P1 |
| 3 | schema.sql 缺建表 | 无 `t_seckill_product` / `t_seckill_order` | 补充 CREATE TABLE DDL | P0 |
| 4 | `findByOrderNo()` 缺失 | `findAll().stream().filter()` 全表扫描 | Repository 增加 `findByOrderNo(String)` | P0 |
| 5 | 库存回补无乐观锁 | 直接 `setStock(stock+1)` | 改用 `@Version` 或 `WHERE stock >= 0` | P1 |
| 6 | 超时取消无分布式锁 | 并发处理可能库存漂移 | 加 Redisson `RLock` 保护回补操作 | P2 |

**下一步学习方向**（结合 java26s 技能图谱）：

| 优先级 | 模块 | 对应技能图谱章节 | 预期收益 |
|--------|------|-----------------|---------|
| P0 | 补全秒杀 schema.sql + findByOrderNo | 数据层 3.1 | 秒杀模块可独立运行 |
| P1 | 并发编程（线程池 + CompletableFuture） | Java 核心 1.3 | 异步化秒杀流程 |
| P1 | Spring Security + JWT | Spring 生态 2.3 | 接口鉴权 |
| P1 | JUnit 5 + MockMvc 测试 | 测试 七 | 秒杀接口自动化验证 |
| P2 | Spring Cloud（Nacos + Gateway） | Spring 生态 2.2 | 微服务拆分秒杀服务 |
| P2 | Docker + CI/CD | DevOps 五 | 秒杀服务容器化部署 |
| P3 | RocketMQ 完整接入 | 分布式 6.2 | 延迟消息可靠性提升 |

---

## 九、通用流程引擎（完整实现）

### 9.1 核心架构

```
流程定义（模板）          流程实例（运行）
┌─────────────┐         ┌─────────────┐
│ WfProcess   │ 1 ──→ N │ WfInstance  │
│ WfNode      │         │ WfInstanceNode │
│ WfTransition│         └─────────────┘
└─────────────┘
```

**设计思路**：
- 流程定义 = 模板（节点 + 连线 + 条件）
- 流程实例 = 某次运行（绑定业务实体，如订单#12345）
- 引擎 = 驱动实例在节点间流转的执行器

### 9.2 节点类型

| 类型 | 说明 | 行为 |
|------|------|------|
| **START** | 开始节点，每流程恰好 1 个 | 自动通过，推进到下一节点 |
| **END** | 结束节点，1 个或多个 | 标记实例完成 |
| **TASK/HUMAN** | 人工任务 | 停下等待人处理 |
| **TASK/AUTO** | 自动任务 | 调用 Spring Bean 自动执行 |
| **GATEWAY** | 网关节点 | 按 SpEL 条件选择分支 |

### 9.3 条件表达式（SpEL）

```java
// 条件存储在 t_wf_transition.condition_expr
"#amount > 1000 and #approved == true"   // 复合条件
"#vip == true"                            // 单条件
null / 空                                  // 无条件（默认路径）
```

**评估逻辑**：网关按 `sort_order` 优先级匹配，第一个命中的分支胜出。

### 9.4 执行引擎核心流程

```
startProcess(processKey, businessType, businessId, variables)
  → 创建 Instance(RUNNING)
  → START 自动完成
  → advanceToNext() 循环推进：
      TASK/AUTO  → 执行 Bean → 继续
      GATEWAY    → 评估条件 → 选择分支 → 继续
      TASK/HUMAN → 停下，等待 completeTask()
      END        → 标记 Instance 完成
```

### 9.5 数据模型（5 张表）

| 表 | 说明 | 关键字段 |
|---|------|---------|
| `t_wf_process` | 流程定义 | process_key, business_type, status |
| `t_wf_node` | 流程节点 | node_type, task_type, handler_bean |
| `t_wf_transition` | 连线 | source_node_id, target_node_id, condition_expr |
| `t_wf_instance` | 流程实例 | business_type, business_id, variables(JSON) |
| `t_wf_instance_node` | 实例节点 | status, operator, comment |

### 9.6 API 接口速查

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/wf/process` | 创建流程定义 |
| GET | `/api/wf/process/{id}` | 获取流程详情 |
| GET | `/api/wf/process` | 列出流程定义 |
| PUT | `/api/wf/process/{id}/publish` | 发布流程 |
| PUT | `/api/wf/process/{id}/stop` | 停用流程 |
| POST | `/api/wf/instance/start` | 启动流程实例 |
| POST | `/api/wf/instance/{id}/node/{nodeId}/complete` | 完成人工任务 |
| POST | `/api/wf/instance/{id}/cancel` | 取消流程实例 |
| GET | `/api/wf/instance/{id}` | 查询实例状态 |
| GET | `/api/wf/business/status` | 查询业务实体流程状态 |
| GET | `/api/wf/business/instances` | 列出业务实体的流程实例 |
| GET | `/api/wf/overview` | API 总览 |

### 9.7 示例：订单审批流程

```
START → [自动]创建订单 → [人工]主管审批 → {网关}
                                            ├── 金额>1000且已批准 → [人工]总监审批 → [自动]处理支付 → END
                                            └── 默认 → [自动]拒绝通知 → END
```

**调用序列**：
1. `POST /api/wf/process` — 创建流程定义（含节点+连线）
2. `PUT /api/wf/process/1/publish` — 发布（校验完整性）
3. `POST /api/wf/instance/start` — 启动实例（订单#12345, 金额1500）
4. `GET /api/wf/business/status?businessType=order&businessId=12345` — 查看当前在"主管审批"
5. `POST /api/wf/instance/1/node/3/complete` — 主管审批通过
6. `GET /api/wf/business/status` — 查看当前在"总监审批"（因为金额>1000）
7. `POST /api/wf/instance/1/node/5/complete` — 总监审批通过
8. `GET /api/wf/instance/1` — 查看完整历史，实例已完成

### 9.8 文件结构

```
src/main/java/com/example/transaction/workflow/
├── entity/
│   ├── WfProcess.java              # 流程定义
│   ├── WfNode.java                 # 流程节点
│   ├── WfTransition.java           # 连线
│   ├── WfInstance.java             # 流程实例
│   └── WfInstanceNode.java         # 实例节点
├── repository/
│   ├── WfProcessRepository.java
│   ├── WfNodeRepository.java
│   ├── WfTransitionRepository.java
│   ├── WfInstanceRepository.java
│   └── WfInstanceNodeRepository.java
├── engine/
│   ├── NodeType.java               # 节点类型枚举
│   ├── TaskType.java               # 任务类型枚举
│   ├── InstanceStatus.java         # 实例状态枚举
│   ├── InstanceNodeStatus.java     # 实例节点状态枚举
│   ├── WorkflowException.java      # 自定义异常
│   ├── AutoTaskHandler.java        # 自动任务接口
│   ├── ConditionEvaluator.java     # SpEL 条件评估
│   ├── WorkflowStatusDTO.java      # 状态返回 DTO
│   └── WorkflowEngine.java         # 核心引擎
├── service/
│   └── WorkflowService.java        # 业务服务
└── controller/
    └── WorkflowController.java     # REST 接口
```

### 9.9 知识点总结

| 知识点 | 代码体现 | 核心原理 |
|--------|----------|----------|
| 状态机模式 | WorkflowEngine.advanceToNext | 节点状态流转驱动引擎 |
| SpEL 表达式 | ConditionEvaluator | Spring 内置表达式引擎 |
| 策略模式 | AutoTaskHandler 接口 | 不同业务实现不同处理器 |
| 模板方法 | startProcess → advanceToNext | 固定流程 + 可变节点 |
| 责任链 | GATEWAY 条件评估 | 按优先级匹配分支 |
| JSON 存储 | WfInstance.variables | 流程变量灵活存储 |
| 业务绑定 | businessType + businessId | 通用绑定任意业务 |

---

## 十、并发编程速查（java26s 1.3 — ✅ 已完成）

> 状态：✅ 已完成 | 对应技能图谱 Java 核心 1.3 | 详细内容见上方第八章

### 9.1 线程池 ThreadPoolExecutor — 7 参数

| 参数 | 含义 | 建议值 |
|------|------|--------|
| corePoolSize | 核心线程数 | CPU 密集型 = N+1，IO 密集型 = 2N |
| maximumPoolSize | 最大线程数 | 根据压测调整 |
| keepAliveTime | 非核心线程存活时间 | 60s |
| unit | 时间单位 | TimeUnit.SECONDS |
| workQueue | 等待队列 | LinkedBlockingQueue / ArrayBlockingQueue |
| threadFactory | 线程工厂 | 自定义命名（便于排查） |
| handler | 拒绝策略 | CallerRunsPolicy（推荐） |

**四种拒绝策略**：AbortPolicy（抛异常）、CallerRunsPolicy（调用者执行）、DiscardPolicy（静默丢弃）、DiscardOldestPolicy（丢弃最旧）

### 9.2 CompletableFuture 实战

```java
// 并行查询，合并结果
CompletableFuture.allOf(
    CompletableFuture.supplyAsync(() -> queryUser(id)),
    CompletableFuture.supplyAsync(() -> queryOrders(id))
).thenApply(v -> mergeResult()).join();

// 超时控制
CompletableFuture.supplyAsync(() -> fetchData())
    .orTimeout(3, TimeUnit.SECONDS)
    .exceptionally(ex -> fallbackValue);
```

### 9.3 并发工具

| 工具 | 用途 | 场景 |
|------|------|------|
| CountDownLatch | 等待 N 个任务完成 | 批量数据加载 |
| CyclicBarrier | N 个线程互相等待 | 并行计算汇合 |
| Semaphore | 控制并发数 | 连接池限流 |
| StampedLock | 读多写少 | 高性能缓存 |

### 9.4 锁演进

```
synchronized → ReentrantLock → StampedLock（读多写少）
                ├── tryLock(wait, unit)  非阻塞
                ├── lockInterruptibly()  可中断
                └── 公平锁 new ReentrantLock(true)
```

---

## 十、Spring Cloud 微服务速查（java26s 2.2 — 待实操）

> 状态：⬜ 待学习 | 对应技能图谱 Spring 生态 2.2

### 10.1 核心组件

| 组件 | 推荐方案 | 作用 |
|------|----------|------|
| 注册中心 | Nacos 2.x | 服务注册与发现 |
| 配置中心 | Nacos Config | 动态配置管理 |
| 网关 | Spring Cloud Gateway | 路由、限流、鉴权 |
| 负载均衡 | Spring Cloud LoadBalancer | 客户端负载 |
| 熔断限流 | Sentinel / Resilience4j | 服务保护 |
| 链路追踪 | Micrometer Tracing + Zipkin | 分布式调用链 |
| 远程调用 | OpenFeign / RestClient | 声明式 HTTP |

### 10.2 服务调用

```java
@FeignClient(name = "order-service", fallbackFactory = OrderFallback.class)
public interface OrderClient {
    @GetMapping("/api/orders/{userId}")
    List<OrderDTO> getOrders(@PathVariable Long userId);
}
```

---

## 十一、Spring Security + OAuth2 速查（java26s 2.3 — 待实操）

> 状态：⬜ 待学习 | 对应技能图谱 Spring 生态 2.3

### 11.1 认证架构

```
请求 → FilterChain → AuthenticationManager → AuthenticationProvider → UserDetailsService
                                                                        ↓
JWT Token ← TokenGenerator ← Authentication（认证成功）
```

### 11.2 核心配置

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

### 11.3 RBAC 模型

```
User ← UserRole → Role ← RolePermission → Permission
用户     多对多      角色     多对多          权限
```

---

## 十二、测试速查（java26s 七 — 待实操）

> 状态：⬜ 待学习 | 对应技能图谱 测试 七

### 12.1 测试金字塔

```
        /  E2E  \         ← 少量（Playwright）
       / 集成测试 \        ← 适量（Testcontainers）
      /  单元测试   \      ← 大量（JUnit 5 + Mockito）
```

### 12.2 JUnit 5 核心注解

| 注解 | 用途 |
|------|------|
| `@Test` | 测试方法 |
| `@ParameterizedTest` + `@ValueSource` | 参数化测试 |
| `@DisplayName` | 中文测试名 |
| `@SpringBootTest` | Spring 集成测试 |
| `@AutoConfigureMockMvc` | MockMvc 自动配置 |
| `@MockBean` | 替换 Spring Bean |

### 12.3 MockMvc 实战

```java
mockMvc.perform(post("/api/users")
        .contentType(APPLICATION_JSON)
        .content("{\"name\":\"张三\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.name").value("张三"));
```

---

## 十三、前端速查（java26s 四 — 待实操）

> 状态：⬜ 待学习 | 对应技能图谱 前端 四

### 13.1 技术栈选型

| 技术 | 推荐 | 说明 |
|------|------|------|
| 框架 | Vue 3 + Vite | 国内主流，上手快 |
| 语言 | TypeScript | 类型安全 |
| 状态管理 | Pinia | Vue 3 官方推荐 |
| UI 库 | Element Plus / Ant Design Vue | 企业级组件 |
| HTTP | Axios | 拦截器、取消请求 |
| 包管理 | pnpm | 快、省空间 |

### 13.2 Vue 3 Composition API

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
const users = ref<User[]>([])
onMounted(async () => {
  const { data } = await axios.get('/api/users')
  users.value = data.data
})
</script>
```

---

## 十四、DevOps 速查（java26s 五 — 待实操）

> 状态：⬜ 待学习 | 对应技能图谱 DevOps 五

### 14.1 Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
```

### 14.2 CI/CD 流水线

```
代码提交 → SonarQube 扫描 → JUnit 测试 → Docker 构建 → 推送镜像 → K8s 部署 → 健康检查
```

### 14.3 监控体系

| 层面 | 工具 |
|------|------|
| 指标 | Prometheus + Grafana |
| 日志 | ELK / Loki |
| 链路 | SkyWalking / Jaeger |
| 告警 | AlertManager + Webhook |

---

## 十五、学习进度追踪

### 已完成 ✅

| 模块 | 知识点 | 深度 |
|------|--------|------|
| Spring 事务 | 生命周期、传播行为、隔离级别、失效场景、编程式、嵌套、只读、超时、批量 | ⭐⭐⭐⭐⭐ |
| 大事务优化 | 反模式对比、分片提交、最小范围、延迟加锁、异步化、三层超时 | ⭐⭐⭐⭐⭐ |
| 自定义 Tx 框架 | 设计原理、ApplicationContextAware、链式 API、高级联动 | ⭐⭐⭐⭐⭐ |
| JPA/Hibernate | 实体映射、Repository、JPQL、@Modifying | ⭐⭐⭐⭐ |
| MyBatis | Mapper 接口、XML 映射、动态 SQL | ⭐⭐⭐⭐ |
| Redis 基础 | 5 种数据结构、RedisTemplate 配置 | ⭐⭐⭐⭐ |
| Redis 缓存 | Cache Aside、穿透/击穿/雪崩、布隆过滤器（3 种） | ⭐⭐⭐⭐⭐ |
| Redis 分布式锁 | SETNX 演进、Redisson、可重入、Lua 原子秒杀 | ⭐⭐⭐⭐⭐ |
| Redis 限流 | 4 种算法 + Lua 原子版 + 多层限流 + 动态配置 | ⭐⭐⭐⭐⭐ |
| 设计模式 | 策略、工厂、观察者、模板方法、责任链、装饰器 | ⭐⭐⭐⭐ |
| 分布式 | Redisson 锁、雪花算法、TCC/Saga/本地消息表 | ⭐⭐⭐⭐ |
| DDD | 聚合根、值对象、领域事件、领域服务、应用服务 | ⭐⭐⭐⭐ |
| Java 17 特性 | Record、Sealed Classes（在项目中实际使用） | ⭐⭐⭐ |
| **JVM 内存模型** | 内存区域、栈堆分配、逃逸分析、字符串常量池、直接内存、四种引用 | ⭐⭐⭐⭐⭐ |
| **JVM 执行原理** | 字节码、类加载 5 阶段、双亲委派、类加载器层级、JIT 编译、分层编译 | ⭐⭐⭐⭐⭐ |
| **JVM 垃圾回收** | GC 算法、七种收集器、内存泄漏、GC 调优、finalize vs Cleaner | ⭐⭐⭐⭐⭐ |
| **秒杀系统** | Redis Lua 原子操作、延迟队列、滑动窗口限流、分布式锁、乐观锁/悲观锁 | ⭐⭐⭐⭐⭐ |
| **流程引擎** | 状态机、SpEL 条件分支、业务绑定、节点流转、自动/人工任务 | ⭐⭐⭐⭐⭐ |
| **并发编程** | 线程基础、线程池 7 参数、CompletableFuture、CountDownLatch/CyclicBarrier/Semaphore、ReentrantLock/ReadWriteLock/StampedLock、CAS原理/ABA问题/自旋锁、LongAdder/ConcurrentHashMap | ⭐⭐⭐⭐⭐ |

### 待学习 ⬜（按 java26s 技能图谱对齐）

**阶段一：补全当前模块（P0）**

| 模块 | 知识点 | 对应技能图谱 |
|------|--------|-------------|
| 秒杀补全 | schema.sql、findByOrderNo、库存回补乐观锁 | 数据层 3.1 |

**阶段二：Java 进阶 + 安全 + 测试（P1）**

| 模块 | 知识点 | 对应技能图谱 |
|------|--------|-------------|
| ~~并发编程~~ | ✅ 已完成（线程池/CompletableFuture/并发工具/锁/原子类） | Java 核心 1.3 |
| Virtual Threads | Project Loom 轻量级线程、高并发 IO | Java 核心 1.1 |
| Spring Security | JWT Token（Access+Refresh）、RBAC、OAuth2 授权服务器 | Spring 生态 2.3 |
| 测试 | JUnit 5 + Mockito + MockMvc、Testcontainers 集成测试 | 测试 七 |

**阶段三：微服务 + DevOps（P2）**

| 模块 | 知识点 | 对应技能图谱 |
|------|--------|-------------|
| Spring Cloud | Nacos 注册/配置中心、Gateway 网关、Sentinel 熔断限流 | Spring 生态 2.2 |
| 远程调用 | OpenFeign / RestClient + LoadBalancer | Spring 生态 2.2 |
| 链路追踪 | Micrometer Tracing + Zipkin / SkyWalking | Spring 生态 2.2 |
| Docker | 镜像构建、多阶段构建、Docker Compose | DevOps 5.3 |
| Kubernetes | Pod/Deployment/Service/Ingress/HPA | DevOps 5.3 |
| CI/CD | GitLab CI 流水线、SonarQube 代码扫描 | DevOps 5.2 |
| 监控 | Prometheus + Grafana、ELK/Loki、AlertManager | DevOps 5.4 |
| 秒杀进阶 | RocketMQ 延迟消息接入、库存一致性对账 | 分布式 6.2 |

**阶段四：全栈扩展（P3）**

| 模块 | 知识点 | 对应技能图谱 |
|------|--------|-------------|
| 前端核心 | TypeScript、ES6+、async/await | 前端 4.1 |
| Vue 3 | Composition API、Vite、Pinia、Element Plus | 前端 4.2 |
| 前端工程化 | pnpm、ESLint+Prettier、Axios 拦截器 | 前端 4.3 |
| 中间件 | RocketMQ 事务消息、Kafka 高吞吐、Elasticsearch 全文检索 | 分布式 6.2 |
| AI 进阶 | Spring AI 集成、RAG 企业知识库、MCP 协议 | AI 八 |

---

## 十、参考文档

| 文档 | 内容 | 行数 |
|------|------|------|
| `事务知识总结.md` | 事务全部知识（17 章） | 1400+ |
| `架构设计学习指南.md` | 设计模式 + 分布式 + DDD | 220+ |
| `项目结构.md` | 目录结构 + 模块说明 | 250+ |
| `BIG_TRANSACTION_BEST_PRACTICES.md` | 大事务最佳实践 | — |
| `CUSTOM_TX_FRAMEWORK.md` | Tx 框架设计文档 | — |
| `商业场景全景图.md` | 10 大商业场景 + 架构设计 + 技术选型 | — |
| `jvm-gc-deep-dive.md` | JVM GC 深度解析（6 大收集器） | — |
| `rocketmq/README.md` | RocketMQ 容器环境搭建 | — |
| `jg.md` | 本文件 — 知识点总结提炼 + java26s 技能图谱 | — |

---

## 十一、JVM 模块文件结构

```
src/main/java/com/example/transaction/jvm/
├── memory/                              # 内存模型（5 个 Demo）
│   ├── MemoryStructureDemo.java         # 内存区域结构 + 堆分代
│   ├── StackHeapDemo.java               # 栈 vs 堆 + 逃逸分析
│   ├── StringPoolDemo.java              # 字符串常量池（6 种场景）
│   ├── DirectMemoryDemo.java            # 直接内存 + 零拷贝
│   └── ReferenceTypeDemo.java           # 四种引用 + WeakHashMap
│
├── execution/                           # 执行原理（4 个 Demo）
│   ├── BytecodeDemo.java                # 字节码指令 + 方法分派 + Lambda
│   ├── ClassLoadDemo.java               # 类加载 5 阶段 + 线程安全单例
│   ├── ClassLoaderDemo.java             # 双亲委派 + 自定义类加载器 + SPI
│   └── JitCompileDemo.java              # JIT 编译 + 内联 + 逃逸分析
│
├── gc/                                  # 垃圾回收（5 个 Demo）
│   ├── GcAlgorithmDemo.java             # 四种 GC 算法 + 对象分配流程
│   ├── GcCollectorDemo.java             # 七种收集器 + G1 Region
│   ├── MemoryLeakDemo.java              # 5 种内存泄漏场景 + 排查方法
│   ├── GcTuningDemo.java                # GC 调优参数 + 日志 + 工具
│   └── FinalizeVsCleanerDemo.java       # finalize vs Cleaner vs PhantomRef
│
└── controller/
    └── JvmDemoController.java           # ~30 个 REST 接口
```

---

*transaction-demo | Spring Boot 3.2.5 | Java 17/21 | 130+ 个源文件 | 2026-06-10*
