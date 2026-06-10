# Java 全栈程序员技能图谱（2026 版）

> 涵盖后端、前端、数据库、DevOps、架构五大板块，聚焦当前主流技术栈与实战要点。
> 项目的学习内容在不断的增加、每到一个阶段、手动总结内容到JG.md
> 接下来的任何软件的安装，安装到D盘

---

## 一、Java 核心与新特性

### 1.1 语言基础（Java 17/21 LTS）

| 特性 | 版本 | 说明 |
|------|------|------|
| Record | 16 | 不可变数据载体，替代简单 POJO |
| Sealed Classes | 17 | 限制继承层级，配合模式匹配 |
| Pattern Matching for switch | 21 | `switch` 中直接类型匹配与解构 |
| Virtual Threads (Project Loom) | 21 | 轻量级线程，大幅降低并发编程门槛 |
| String Templates | 21 | `STR."Hello \{name}"` 模板语法 |
| Sequenced Collections | 21 | `getFirst()`/`getLast()` 统一访问首尾元素 |

```java
// Record — 不可变 DTO
public record UserDTO(String name, String email, int age) {}

// Sealed Classes — 限制支付方式
public sealed interface Payment permits Alipay, WechatPay, BankTransfer {}

// Virtual Threads — 高并发 IO
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, 10_000).forEach(i ->
        executor.submit(() -> fetchFromRemote(i))
    );
}
```

### 1.2 JVM 与性能调优

- **内存模型**：堆（Young/Old）、栈、方法区、直接内存
- **GC 选择**：G1（默认）、ZGC（超低延迟）、Shenandoah
- **调优工具**：`jps`、`jstat`、`jmap`、`jstack`、`jcmd`、Arthas、async-profiler
- **关键参数**：`-Xmx`、`-Xms`、`-XX:MaxGCPauseMillis`、`-XX:+UseZGC`
- **诊断命令**：
  ```bash
  # Arthas 在线诊断
  java -jar arthas-boot.jar
  dashboard                    # 实时面板
  thread -n 3                  # CPU 最高的 3 个线程
  trace com.xxx.Service method # 方法调用链路耗时
  ```

### 1.3 并发编程

- **线程池**：`ThreadPoolExecutor` 7 参数（核心线程、最大线程、存活时间、队列、拒绝策略）
- **并发工具**：`CompletableFuture`、`CountDownLatch`、`CyclicBarrier`、`Semaphore`
- **锁**：`synchronized` → `ReentrantLock` → `StampedLock`（读多写少）
- **无锁**：`AtomicInteger`、`LongAdder`、`ConcurrentHashMap`
- **CompletableFuture 实战**：
  ```java
  CompletableFuture.allOf(
      CompletableFuture.supplyAsync(() -> queryUser(id)),
      CompletableFuture.supplyAsync(() -> queryOrders(id))
  ).thenApply(v -> mergeResult()).join();
  ```

---

## 二、Spring 生态（核心）

### 2.1 Spring Boot 3.x

| 模块 | 用途 |
|------|------|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-data-jpa` | ORM（Hibernate） |
| `spring-boot-starter-data-redis` | 缓存/会话 |
| `spring-boot-starter-security` | 认证授权 |
| `spring-boot-starter-validation` | 参数校验 |
| `spring-boot-starter-actuator` | 监控端点 |
| `spring-boot-starter-cache` | 缓存抽象 |

**核心配置**：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db?useSSL=false&serverTimezone=Asia/Shanghai
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false  # 生产环境关闭
  data:
    redis:
      host: localhost
      port: 6379
```

### 2.2 Spring Cloud 微服务

| 组件 | 推荐方案 |
|------|----------|
| 注册中心 | Nacos 2.x |
| 配置中心 | Nacos Config |
| 网关 | Spring Cloud Gateway |
| 负载均衡 | Spring Cloud LoadBalancer |
| 熔断限流 | Sentinel / Resilience4j |
| 链路追踪 | Micrometer Tracing + Zipkin / SkyWalking |
| 远程调用 | OpenFeign / RestClient |

**服务调用示例**：
```java
@FeignClient(name = "order-service", fallbackFactory = OrderFallback.class)
public interface OrderClient {
    @GetMapping("/api/orders/{userId}")
    List<OrderDTO> getOrders(@PathVariable Long userId);
}
```

### 2.3 Spring Security + OAuth2

- **认证**：JWT Token（Access + Refresh）
- **授权**：RBAC（用户 → 角色 → 权限）
- **OAuth2**：`spring-authorization-server` 实现授权服务器
- **实战要点**：
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

---

## 三、数据层

### 3.1 数据库

| 数据库 | 场景 |
|--------|------|
| MySQL 8.x | 主力关系型数据库 |
| PostgreSQL | 需要 JSON/地理信息时 |
| Redis 7.x | 缓存、分布式锁、会话 |
| MongoDB | 文档型、日志存储 |
| Elasticsearch | 全文检索、日志分析 |

**MySQL 优化要点**：
- 索引：覆盖索引、联合索引最左前缀、索引下推（ICP）
- 慢查询：`slow_query_log` + `EXPLAIN ANALYZE`
- 分库分表：ShardingSphere-JDBC 5.x
- 读写分离：主从复制 + ProxySQL / ShardingSphere

### 3.2 ORM 框架选型

| 框架 | 特点 |
|------|------|
| JPA/Hibernate | 自动建表、JPQL、适合标准 CRUD |
| MyBatis | SQL 灵活、适合复杂查询 |
| MyBatis-Plus | 增强 CRUD、代码生成器 |

**JPA 实战**：
```java
@Entity
@Table(name = "t_order")
@Data
public class Order {
    @Id @GeneratedValue(strategy =.IDENTITY)
    private Long id;
    private String orderNo;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
}

// Repository — 自定义查询
public interface OrderRepository extends JpaRepository<Order, Long>,
        JpaSpecificationExecutor<Order> {
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.status = :status")
    List<Order> findByUserAndStatus(@Param("userId") Long userId,
                                     @Param("status") OrderStatus status);
}
```

### 3.3 缓存策略

```java
@Cacheable(value = "user", key = "#id", unless = "#result == null")
public User getUserById(Long id) { ... }

@CachePut(value = "user", key = "#user.id")
public User updateUser(User user) { ... }

@CacheEvict(value = "user", key = "#id")
public void deleteUser(Long id) { ... }
```

**缓存模式**：Cache-Aside（旁路缓存）、Read-Through、Write-Behind
**防穿透**：布隆过滤器、空值缓存
**防雪崩**：随机 TTL、热点预加载
**防击穿**：分布式锁（Redisson `RLock`）

---

## 四、前端技能（Java 全栈必备）

### 4.1 核心三件套

| 技术 | 掌握程度 |
|------|----------|
| HTML/CSS | 语义化标签、Flex/Grid 布局、响应式 |
| JavaScript/ES6+ | Promise、async/await、解构、模块化 |
| TypeScript | 类型系统、泛型、接口、枚举 |

### 4.2 框架（选一个深入）

| 框架 | 特点 |
|------|------|
| Vue 3 + Vite | 上手快、生态丰富、国内主流 |
| React 18 + Next.js | 灵活度高、SSR/SSG 支持好 |

**Vue 3 Composition API 示例**：
```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

interface User { id: number; name: string; email: string }

const users = ref<User[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  const { data } = await axios.get('/api/users')
  users.value = data.data
  loading.value = false
})
</script>
```

### 4.3 工程化

- **构建**：Vite 5（开发快）、Webpack 5（兼容性）
- **包管理**：pnpm（推荐）> npm > yarn
- **UI 组件库**：Ant Design Vue / Element Plus（Vue）、Ant Design / shadcn/ui（React）
- **状态管理**：Pinia（Vue）、Zustand / Jotai（React）
- **HTTP**：Axios（拦截器、取消请求、重试）
- **代码规范**：ESLint + Prettier + Husky + lint-staged

---

## 五、DevOps 与工具链

### 5.1 构建与版本管理

| 工具 | 用途 |
|------|------|
| Maven / Gradle | Java 构建（Maven 稳定，Gradle 快） |
| Git | 版本控制（分支策略：Git Flow / Trunk-Based） |
| GitLab / GitHub | 代码托管 + CI/CD |

### 5.2 CI/CD 流水线

```
代码提交 → GitLab CI / Jenkins
  → 代码扫描（SonarQube）
  → 单元测试（JUnit 5）
  → 构建镜像（Docker）
  → 推送镜像（Harbor / ACR）
  → 部署（Kubernetes / Docker Compose）
  → 健康检查 + 回滚
```

**Dockerfile 示例**：
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
```

### 5.3 容器与编排

- **Docker**：镜像构建、多阶段构建、网络、数据卷
- **Kubernetes**：Pod、Deployment、Service、Ingress、ConfigMap、HPA
- **Helm**：K8s 包管理器，模板化部署

### 5.4 监控与可观测性

| 层面 | 工具 |
|------|------|
| 指标 | Prometheus + Grafana |
| 日志 | ELK（Elasticsearch + Logstash + Kibana）/ Loki |
| 链路 | SkyWalking / Jaeger / Micrometer Tracing |
| 告警 | AlertManager / 钉钉/飞书 Webhook |

---

## 六、架构设计

### 6.1 设计模式（高频）

| 模式 | 场景 |
|------|------|
| 策略模式 | 多种支付方式、多种导出格式 |
| 工厂模式 | 根据类型创建不同处理器 |
| 观察者模式 | 事件驱动、消息通知 |
| 模板方法 | 流程固定但步骤可变 |
| 责任链 | 过滤器、审批流程 |
| 装饰器 | 增强功能、AOP 思想 |

### 6.2 分布式核心

| 问题 | 方案 |
|------|------|
| 分布式事务 | Seata（AT/TCC）、RocketMQ 事务消息、Saga |
| 分布式锁 | Redisson（看门狗续期）、ZooKeeper |
| 分布式 ID | Snowflake、Leaf、UUID v7 |
| 消息队列 | RocketMQ（事务）、Kafka（高吞吐）、RabbitMQ（灵活） |
| 限流 | Sentinel、Redis + Lua 滑动窗口 |

### 6.3 系统设计原则

- **SOLID**：单一职责、开闭原则、里氏替换、接口隔离、依赖倒置
- **DDD**：领域驱动设计，聚合根、值对象、领域事件
- **CAP**：一致性、可用性、分区容忍（三选二）
- **BASE**：基本可用、软状态、最终一致性

---

## 七、测试

### 7.1 测试金字塔

```
        /  E2E  \         ← 少量（Selenium / Playwright）
       / 集成测试 \        ← 适量（Testcontainers）
      /  单元测试   \      ← 大量（JUnit 5 + Mockito）
```

### 7.2 JUnit 5 实战

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserService userService;

    @Test
    @DisplayName("创建用户 - 正常流程")
    void createUser_success() throws Exception {
        when(userService.create(any())).thenReturn(new UserDTO(1L, "张三", "z@e.com"));

        mockMvc.perform(post("/api/users")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"张三\",\"email\":\"z@e.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("张三"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "a".repeat(101)})
    void createUser_invalidName(String name) throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isBadRequest());
    }
}
```

---

## 八、AI 辅助开发（2026 趋势）

| 工具/方向 | 用途 |
|-----------|------|
| Claude Code | CLI AI 编程助手，代码生成/审查/重构 |
| GitHub Copilot | IDE 内代码补全 |
| Cursor / Windsurf | AI-native IDE |
| Spring AI | Spring 生态 AI 集成框架 |
| RAG 架构 | 企业知识库 + LLM |
| MCP 协议 | AI 工具调用标准协议 |

---

## 九、学习路线建议

```
初级（0-2年）                中级（2-5年）               高级（5年+）
─────────────              ──────────────              ─────────────
Java 基础 + 集合            JVM 调优 + 并发             架构设计 + DDD
Spring Boot CRUD           Spring Cloud 微服务          分布式系统设计
MySQL 基础                  性能优化 + 索引              分库分表 + 中间件
Git + Maven                Docker + K8s                技术选型 + 带队
HTML/CSS/JS 基础            Vue/React 项目实战           全栈架构 + 技术管理
JUnit 单元测试              集成测试 + CI/CD             可观测性 + 稳定性
```

---

## 十、额外文件
```
事务知识总结.md
架构设计学习指南.md
项目结构.md
jg.md
```


*最后更新：2026-05-28 | 基于 Spring Boot 3.2+ / Java 17/21 LTS*
