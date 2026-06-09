# RocketMQ 容器环境

基于 Podman 的 RocketMQ 开发环境，包含 NameServer、Broker 和 Dashboard。

## 目录结构

```
rocketmq/
├── podman-compose.yml   # 容器编排配置
├── conf/
│   └── broker.conf      # Broker 配置文件
├── data/
│   ├── namesrv/         # NameServer 数据
│   └── broker/          # Broker 数据
├── start.sh             # 启动脚本
└── README.md            # 本文件
```

## 快速开始

### 1. 前置条件

```bash
# 安装 podman-compose
pip install podman-compose

# 或使用 pip3
pip3 install podman-compose
```

### 2. 启动服务

```bash
# 使用脚本启动
./start.sh start

# 或手动启动
podman-compose -f podman-compose.yml up -d
```

### 3. 验证服务

```bash
# 查看服务状态
./start.sh status

# 或
podman-compose -f podman-compose.yml ps
```

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| NameServer | 9876 | 路由注册中心 |
| Broker | 10911 | 消息代理主端口 |
| Broker (VIP) | 10909 | VIP 通道端口 |
| Dashboard | 18080 | Web 管理界面 |

## 访问 Dashboard

打开浏览器访问: http://localhost:18080

## 常用命令

```bash
# 启动
./start.sh start

# 停止
./start.sh stop

# 重启
./start.sh restart

# 查看状态
./start.sh status

# 查看日志
./start.sh logs              # 所有日志
./start.sh logs namesrv      # NameServer 日志
./start.sh logs broker       # Broker 日志
./start.sh logs dashboard    # Dashboard 日志

# 清理数据
./start.sh clean
```

## Java 集成示例

### Maven 依赖

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.3.1</version>
</dependency>
```

### application.yml

```yaml
rocketmq:
  name-server: localhost:9876
  producer:
    group: my-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 2
```

### 生产者示例

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RocketMQTemplate rocketMQTemplate;

    public void createOrder(Order order) {
        // 业务逻辑...

        // 发送消息
        rocketMQTemplate.convertAndSend("order-topic", order);
    }

    // 发送延迟消息
    public void sendDelayMessage(Order order) {
        rocketMQTemplate.syncSend("order-topic", order, 3000, 3); // 延迟级别 3 = 10s
    }

    // 发送事务消息
    public void sendTransactionMessage(Order order) {
        rocketMQTemplate.sendMessageInTransaction(
            "order-topic",
            MessageBuilder.withPayload(order).build(),
            null
        );
    }
}
```

### 消费者示例

```java
@Component
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer-group"
)
public class OrderConsumer implements RocketMQListener<Order> {

    @Override
    public void onMessage(Order order) {
        System.out.println("收到订单消息: " + order);
        // 处理订单逻辑...
    }
}
```

## 延迟消息级别

| 级别 | 延迟时间 |
|------|---------|
| 1 | 1s |
| 2 | 5s |
| 3 | 10s |
| 4 | 30s |
| 5 | 1m |
| 6 | 2m |
| 7 | 3m |
| 8 | 4m |
| 9 | 5m |
| 10 | 6m |
| 11 | 7m |
| 12 | 8m |
| 13 | 9m |
| 14 | 10m |
| 15 | 20m |
| 16 | 30m |
| 17 | 1h |
| 18 | 2h |

## 故障排查

### 1. 服务无法启动

```bash
# 查看详细日志
./start.sh logs namesrv
./start.sh logs broker
```

### 2. 连接失败

```bash
# 检查端口是否监听
netstat -an | grep 9876
netstat -an | grep 10911

# 检查防火墙
# Windows: 检查 Windows Defender 防火墙
# Linux: sudo ufw status
```

### 3. 清理并重建

```bash
# 清理数据
./start.sh clean

# 重新启动
./start.sh start
```

## 配置说明

### broker.conf 关键配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| brokerName | Broker 名称 | broker-a |
| namesrvAddr | NameServer 地址 | namesrv:9876 |
| autoCreateTopicEnable | 自动创建 Topic | true |
| flushDiskType | 刷盘方式 | ASYNC_FLUSH |
| brokerRole | Broker 角色 | ASYNC_MASTER |

## 生产环境建议

1. **持久化存储**: 挂载宿主机目录存储数据
2. **资源限制**: 设置 CPU 和内存限制
3. **高可用**: 部署多 Broker 主从架构
4. **监控**: 接入 Prometheus + Grafana
5. **安全**: 配置 ACL 访问控制

## 参考文档

- [Apache RocketMQ 官方文档](https://rocketmq.apache.org/docs/)
- [RocketMQ Spring Boot Starter](https://github.com/apache/rocketmq-spring)
