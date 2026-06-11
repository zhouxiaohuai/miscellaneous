# 阶段性学习总结

> 项目学习笔记，每到一个阶段手动总结
> 最后更新：2026-06-11

---

## 一、已完成模块

### 1. 事务研究（已完成）
- 事务生命周期全过程
- 7 种传播行为、4 种隔离级别
- 12 种事务失效场景
- 编程式事务、嵌套事务、Savepoint
- JPA vs MyBatis 事务对比

### 2. Redis 实战（已完成）
- 基础操作、缓存策略（Cache-Aside）
- 分布式锁（Redisson）
- 限流（滑动窗口）
- 延迟队列

### 3. 秒杀系统（已完成）
- Redis Lua 原子扣减库存
- RocketMQ 异步下单
- 库存回补机制

### 4. 设计模式（已完成）
- 策略模式、工厂模式、观察者模式
- 模板方法、责任链、装饰器

### 5. 分布式核心（已完成）
- 分布式锁、分布式 ID、分布式事务
- DDD 领域驱动设计

### 6. 工作流引擎（已完成）
- 流程定义、节点、连线
- 流程实例、状态流转

### 7. JVM 深入（已完成）
- 内存模型、类加载、字节码
- GC 算法与收集器、调优

---

## 二、权限管理系统（2026-06-11）

> 契合生产环境的 RBAC 权限系统，基于 Spring Security 6 + JWT

### 2.1 技术架构

```
客户端 → JwtAuthenticationFilter → SecurityContext → @PreAuthorize → 业务逻辑
                                                                        ↓
                                                              DataScope AOP → MyBatis 拦截器
```

### 2.2 核心组件

| 组件 | 作用 | 关键文件 |
|------|------|---------|
| SecurityConfig | Security 核心配置 | `security/config/SecurityConfig.java` |
| JwtTokenProvider | JWT 生成/解析/验证 | `security/jwt/JwtTokenProvider.java` |
| JwtAuthenticationFilter | 请求拦截认证 | `security/jwt/JwtAuthenticationFilter.java` |
| AuthService | 登录/登出/刷新Token | `security/service/AuthService.java` |
| @PreAuthorize | 接口级权限校验 | Spring Security 内置 |
| @DataScope | 数据级权限过滤 | `security/annotation/DataScope.java` |
| @Log | 操作日志记录 | `security/annotation/Log.java` |

### 2.3 RBAC 模型

```
User ←── UserRole ──→ Role ←── RolePermission ──→ Permission
用户      多对多        角色       多对多              权限
                                              ┌── 目录(1)
                                              ├── 菜单(2)
                                              ├── 按钮(3)
                                              └── 接口(4)
```

### 2.4 四级权限控制

| 级别 | 实现方式 | 示例 |
|------|---------|------|
| 菜单权限 | 前端路由守卫 + 后端接口 | 用户管理页面是否可见 |
| 按钮权限 | `hasAuthority('sys:user:add')` | 新增按钮是否可点击 |
| 接口权限 | `@PreAuthorize` 注解 | API 是否可调用 |
| 数据权限 | `@DataScope` + MyBatis 拦截器 | 看全部/本部门/仅本人数据 |

### 2.5 数据范围枚举

| 范围 | code | 说明 |
|------|------|------|
| ALL | 1 | 全部数据（超级管理员） |
| DEPT | 2 | 本部门数据 |
| DEPT_AND_CHILDREN | 3 | 本部门及下级部门数据 |
| SELF | 4 | 仅本人数据 |

### 2.6 安全机制

| 机制 | 实现 |
|------|------|
| 密码加密 | BCrypt（不可逆） |
| Token 无状态 | JWT（Access 30min + Refresh 7d） |
| 登录限制 | Redis 计数，5 次锁定 30 分钟 |
| Token 黑名单 | Redis 存储登出 Token |
| 操作审计 | @Log 注解 + AOP 自动记录 |
| 跨域 | CORS 配置 |

### 2.7 API 接口清单

**认证接口（公开）：**
- `POST /api/auth/login` — 登录
- `POST /api/auth/refresh` — 刷新 Token

**认证接口（需认证）：**
- `POST /api/auth/logout` — 登出
- `GET /api/auth/me` — 当前用户信息

**用户管理（需权限）：**
- `GET /api/sys/users` — 分页查询（sys:user:list）
- `POST /api/sys/users` — 新增（sys:user:add）
- `PUT /api/sys/users` — 修改（sys:user:edit）
- `DELETE /api/sys/users/{id}` — 删除（sys:user:delete）
- `PUT /api/sys/users/{id}/reset-password` — 重置密码

**角色管理（需权限）：**
- `GET /api/sys/roles` — 分页查询
- `POST /api/sys/roles` — 新增
- `PUT /api/sys/roles/{id}/permissions` — 分配权限

**权限管理（需权限）：**
- `GET /api/sys/permissions/tree` — 权限树

### 2.8 数据库表

| 表名 | 说明 |
|------|------|
| sys_user | 系统用户表 |
| sys_role | 角色表 |
| sys_permission | 权限表（目录/菜单/按钮/接口） |
| sys_user_role | 用户角色关联表 |
| sys_role_permission | 角色权限关联表 |
| sys_dept | 部门表（数据权限用） |
| sys_oper_log | 操作日志表 |

### 2.9 测试账号

| 用户名 | 密码 | 角色 | 数据范围 |
|--------|------|------|---------|
| admin | 123456 | 超级管理员 | 全部数据 |
| zhangsan | 123456 | 运营管理员 | 本部门数据 |
| lisi | 123456 | 普通用户 | 仅本人数据 |

### 2.10 核心知识点

1. **Spring Security 6 过滤器链**：FilterChain → AuthenticationManager → UserDetailsService
2. **JWT 无状态认证**：Token 自包含用户信息，服务端不存 Session
3. **@PreAuthorize**：SpEL 表达式实现方法级权限校验
4. **数据权限 AOP**：ThreadLocal + MyBatis 拦截器实现行级数据隔离
5. **BCrypt**：加盐哈希，每次加密结果不同，验证时自动比对
6. **Token 黑名单**：登出时 Token 加入 Redis，请求时检查是否在黑名单

### 2.11 请求全流程（核心重点）

#### 2.11.1 登录流程

```
POST /api/auth/login
Body: {"username":"admin","password":"123456"}
```

```
客户端
  │
  ▼
SecurityFilterChain
  │  requestMatchers("/api/auth/login").permitAll()  ◄── 白名单放行
  ▼
AuthController.login()
  │
  ▼
AuthService.login()
  │
  ├─① checkLoginFailCount()
  │    Redis GET "login:fail:admin" → 不存在或 < 5 → 继续
  │
  ├─② userRepository.findByUsername("admin")
  │    SELECT * FROM sys_user WHERE username='admin'
  │    → 找到 user{id=1, password="$2a$10$oxQR..."}
  │
  ├─③ user.getStatus() == 1 ? → 正常
  │
  ├─④ passwordEncoder.matches("123456", "$2a$10$oxQR...")
  │    BCrypt 验证通过
  │
  ├─⑤ clearLoginFail() → Redis DEL "login:fail:admin"
  │
  ├─⑥ roleRepository.findRolesByUserId(1)
  │    → [ROLE_SUPER_ADMIN]
  │
  ├─⑦ permissionRepository.findByRoleIds([1])
  │    → [sys:user:list, sys:user:add, ... 共26个]
  │
  ├─⑧ jwtTokenProvider.generateAccessToken(1, "admin", [ROLE_SUPER_ADMIN])
  │    Jwts.builder().subject("1").claim("roles",[...]).signWith(key)
  │    → "eyJhbGciOiJIUzI1NiJ9..."
  │
  └─⑨ 记录登录IP和时间 → 返回 LoginResponse
```

**返回数据：**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "userId": 1,
  "username": "admin",
  "nickname": "超级管理员",
  "roles": ["ROLE_SUPER_ADMIN"],
  "permissions": ["sys:user:list", "sys:user:add", "sys:user:edit", "..."]
}
```

#### 2.11.2 携带 Token 访问受保护接口

```
GET /api/sys/users?page=0&size=10
Header: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

```
客户端
  │
  ▼
SecurityFilterChain
  │
  ├─① JwtAuthenticationFilter.doFilterInternal()
  │    │
  │    ├ extractToken() → "Bearer eyJhbGci..." → 去前缀 → "eyJhbGci..."
  │    ├ validateToken() → 解签名 + 检查过期 → 有效
  │    ├ getUserId()   → 1
  │    ├ getUsername() → "admin"
  │    ├ getRoles()    → ["ROLE_SUPER_ADMIN"]
  │    │
  │    └ 构建 Authentication 对象:
  │       new UsernamePasswordAuthenticationToken(
  │           principal   = 1L,              ◄── userId
  │           credentials = "eyJhbGci...",   ◄── token
  │           authorities = [ROLE_SUPER_ADMIN]
  │       )
  │       SecurityContextHolder.getContext().setAuthentication(auth)
  │
  ├─② authorizeHttpRequests() 路由匹配
  │    /api/sys/users 不在白名单 → .authenticated() → SecurityContext 有值 → 通过
  │
  ├─③ @PreAuthorize("hasAuthority('sys:user:list')")
  │    从 authorities = [ROLE_SUPER_ADMIN] 检查
  │    → 超级管理员拥有所有权限 → 通过
  │
  ▼
SysUserController.findUsers()
  │
  ▼
SysUserService.findUsers(null, null, null, PageRequest.of(0,10))
  │  SELECT * FROM sys_user LIMIT 10 OFFSET 0
  │  遍历结果查角色 → toDTO()
  ▼
返回 Page<UserDTO>
```

#### 2.11.3 无权限用户访问（403 拦截）

```
GET /api/sys/users
Header: Authorization: Bearer <lisi的token>
```

```
JwtAuthenticationFilter
  → 解析 token → userId=3, roles=[ROLE_USER]
  → SecurityContext 设置完成

SecurityFilterChain
  → .authenticated() → 通过（有认证信息）

@PreAuthorize("hasAuthority('sys:user:list')")
  → authorities = [ROLE_USER]
  → "sys:user:list" 不在 [ROLE_USER] 中
  → 抛出 AccessDeniedException
  → 返回 403 Forbidden
```

#### 2.11.4 过滤器链完整顺序

```
请求进入 Tomcat
  │
  ▼
① JwtAuthenticationFilter         ← 提取 Token → 解析 → 设置 SecurityContext
  │
  ▼
② UsernamePasswordAuthenticationFilter  ← Spring Security 默认（本项目未用）
  │
  ▼
③ ExceptionTranslationFilter      ← 捕获异常，返回 401/403 JSON
  │
  ▼
④ FilterSecurityInterceptor       ← 调用 @PreAuthorize 做权限校验
  │
  ▼
⑤ Controller 方法执行
  │
  ▼
⑥ @DataScope AOP                  ← 如有注解，拼接数据范围条件到 ThreadLocal
  │
  ▼
⑦ Service → Repository → MyBatis  ← 拦截器从 ThreadLocal 读取条件拼接 SQL
  │
  ▼
⑧ @Log AOP                        ← 如有注解，记录操作日志到 sys_oper_log
  │
  ▼
返回响应
```

#### 2.11.5 Token 刷新流程

```
POST /api/auth/refresh
Body: {"refreshToken": "eyJhbGci..."}

AuthService.refreshToken()
  │
  ├─① validateToken(refreshToken) → 有效
  ├─② Redis 检查黑名单 → 不在黑名单中
  ├─③ 解析 userId + username + roles
  ├─④ 查最新权限（角色可能已变更）
  └─⑤ 生成新 accessToken → 返回

特点：refreshToken 不变，只换 accessToken
```

#### 2.11.6 登出流程

```
POST /api/auth/logout
Header: Authorization: Bearer eyJhbGci...

AuthService.logout()
  │
  ├─① 从 SecurityContext 取出当前 token
  ├─② 计算 Token 剩余过期时间
  └─③ Redis SET "token:blacklist:eyJhbGci..." = "1" TTL=剩余秒数

后续请求携带该 Token 时：
  → JwtAuthenticationFilter 验签通过
  → 但业务层可扩展检查黑名单（当前实现中未加，可增强）
```

#### 2.11.7 登录失败限制流程

```
第1次输错密码 → Redis INCR "login:fail:admin" = 1, EXPIRE 30分钟
第2次输错密码 → Redis INCR "login:fail:admin" = 2
第3次输错密码 → Redis INCR "login:fail:admin" = 3
第4次输错密码 → Redis INCR "login:fail:admin" = 4
第5次输错密码 → Redis INCR "login:fail:admin" = 5
第6次尝试登录 → checkLoginFailCount() 读到 count=5
                → 抛出 "登录失败次数过多，账号已锁定30分钟"
30分钟后      → Redis Key 自动过期，可重新登录
```

#### 2.11.8 数据流向总结

```
【登录时】
DB(sys_user) → BCrypt验证 → DB(sys_role) → DB(sys_permission)
     ↓
JWT Token（内含 userId + username + roles）
     ↓
返回客户端，客户端存储到 localStorage

【请求时】
客户端 Header "Bearer xxx"
     ↓
JwtAuthenticationFilter 解析
     ↓
SecurityContext（userId=1, roles=[ROLE_SUPER_ADMIN]）
     ↓
@PreAuthorize 检查角色/权限
     ↓
Controller → Service
     ↓
@DataScope → ThreadLocal（数据范围条件）
     ↓
MyBatis 拼接 SQL → 返回不同范围数据
     ↓
@Log → 记录操作日志
```

---

## 三、待完成模块

- [ ] 权限系统：单元测试 + 集成测试
- [ ] 电商交易链路（购物车→下单→支付→履约）
- [ ] 内容平台（ES 搜索 + Feed 流）
- [ ] IM 即时通讯（Netty + WebSocket）
- [ ] AI 集成（Spring AI + RAG）
