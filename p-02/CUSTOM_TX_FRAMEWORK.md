# 自定义事务框架使用指南

## 框架概述

自定义事务框架解决了 Spring `@Transactional` 的三大局限：

| 局限 | @Transactional | Tx 框架 |
|------|----------------|---------|
| 自调用失效 | 同类方法调用不生效 | 静态方法调用，不依赖 AOP 代理 |
| 方法级边界 | 事务范围固定为整个方法 | 可在方法内任意位置开启/关闭 |
| 必须 public | private 方法不生效 | 不受访问修饰符限制 |

## 核心 API

### 1. 基础用法

```java
// 只读事务 - 查询场景
Tx.readOnly(() -> {
    List<User> users = userRepository.findAll();
});

// 只读事务 - 有返回值
User user = Tx.readOnly(() -> userRepository.findById(1L).orElse(null));

// 读写事务 - 写操作场景
Tx.writable(() -> {
    user.setStatus(2);
    userRepository.save(user);
});

// 读写事务 - 有返回值
User saved = Tx.writable(() -> userRepository.save(user));
```

### 2. 链式 Builder

```java
// 自定义超时 + 只读
User user = Tx.builder()
    .readOnly()
    .timeout(5)
    .execute(() -> userRepository.findById(1L).orElse(null));

// 自定义传播行为 + 读写
Tx.builder()
    .writable()
    .propagation(Propagation.REQUIRES_NEW)
    .timeout(10)
    .executeWithoutResult(() -> {
        accountRepository.updateBalance(id, amount.negate());
    });

// 动态超时：根据数据量调整
int timeout = dataSize > 10000 ? 60 : 10;
Tx.builder()
    .writable()
    .timeout(timeout)
    .executeWithoutResult(() -> {
        batchRepository.saveAll(data);
    });
```

### 3. 方法内任意位置开启事务

```java
public void businessMethod() {
    // 1. 事务外：查询和校验
    User user = userRepository.findById(userId).orElseThrow();
    if (user.getBalance().compareTo(amount) < 0) {
        throw new RuntimeException("余额不足");
    }

    // 2. 远程调用（事务外）
    String result = remoteService.call();

    // 3. 【这里才开启事务】只包裹写操作
    Tx.writable(() -> {
        user.setBalance(user.getBalance().subtract(amount));
        userRepository.save(user);
        logRepository.save(new TxLog("DEDUCT", amount));
    });

    // 4. 事务外：异步通知
    asyncService.notify(user);
}
```

## 演示接口

应用启动后访问 `http://localhost:8082/api/custom-tx/overview` 查看所有接口。

| 接口 | 说明 |
|------|------|
| `GET /api/custom-tx/self-invoke-annotation` | @Transactional 自调用失效演示 |
| `GET /api/custom-tx/self-invoke-tx` | Tx 框架解决自调用问题 |
| `GET /api/custom-tx/minimal-annotation?userId=1&amount=100` | 全方法事务（反模式） |
| `GET /api/custom-tx/minimal-tx?userId=1&amount=100` | 最小事务范围（正确） |
| `GET /api/custom-tx/readonly` | 只读事务演示 |
| `GET /api/custom-tx/writable` | 读写事务演示 |
| `GET /api/custom-tx/builder?userId=1` | 链式 Builder 演示 |
| `GET /api/custom-tx/complete-flow?userId=1&amount=100` | 完整业务流程演示 |

## 框架文件结构

```
src/main/java/com/example/transaction/
├── framework/
│   ├── Tx.java          # 门面类，提供静态方法
│   ├── TxBuilder.java   # 链式构建器
│   ├── TxContext.java   # 事务上下文，持有 TransactionManager
│   ├── TxConfig.java    # 配置类
│   └── TxException.java # 自定义异常
├── service/
│   └── CustomTxService.java    # 演示服务
└── controller/
    └── CustomTxController.java # 演示控制器
```

## 适用场景

| 场景 | 推荐方式 |
|------|----------|
| 简单的查询方法 | `@Transactional(readOnly = true)` |
| 简单的写操作方法 | `@Transactional` |
| 自调用场景 | `Tx.readOnly()` / `Tx.writable()` |
| 需要精确控制事务边界 | `Tx.writable()` 包裹写操作 |
| 动态超时/传播行为 | `Tx.builder()...execute()` |
| 分片提交 | 循环中每片一个 `Tx.writable()` |
