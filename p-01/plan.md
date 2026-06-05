# plan.md
本文件用于**持续记录每次新增/变更需求的提炼结果**（只追加、不覆盖）。

---

## 需求记录

### 2026-04-13 初始化
- 需求 1：每次新需求需要提炼并追加记录到 `plan.md`（根目录）。
- 需求 2：严格按需求开发，不画蛇添足。
- 需求 3：每个功能完成后需补充**详细注释**与**测试用例**，方便测试验证。

### 2026-04-13 记忆功能（匿名账户）
- 背景：当前对话为单轮调用（不带历史上下文），导致“答非所问”。
- 目标：增加“记忆功能”，让模型可利用历史对话上下文进行回答。
- 范围：按**匿名账户**维度记忆（前端生成 `userId` 持久化到浏览器；后端按 `userId` 读写记忆）。
- 策略：**长期记忆 + 摘要压缩**（summary）+ 最近 K 轮原文窗口（lastK），控制上下文长度与成本。
- 约束：必须严格按需求实现；完成后补充**详细注释**与**测试用例**（单测/集成测），便于验证。

### 2026-04-13 记忆功能（多会话决策）
- 会话形态：选择 **A2 多会话**（支持新建/切换会话），为后续接入登录体系做铺垫。
- 识别键：`userId`（匿名） + `conversationId`（会话）。

### 2026-04-13 环境准备（本地 MySQL）
- 前置条件：本地尚未安装 MySQL。
- 要求：在本地安装并初始化 MySQL，用于保存会话与消息等“记忆”数据（纳入计划的早期准备步骤）。

### 2026-04-13 环境准备（本地 MySQL 已完成）
- 安装方式：使用 `winget` 安装 MySQL 8.4（并补齐 VC++ 运行库依赖）。
- 初始化：使用 `C:\\mysql\\my.ini` 与数据目录 `C:\\mysql-data` 初始化实例并启动（当前以控制台进程方式运行，非 Windows Service）。
- 初始化结果：已创建数据库 `aichat`，并创建本地账号 `aichat_user@localhost`（具备 `aichat.*` 全权限）。

### 2026-04-13 环境信息（MySQL 连接参数）
- host：`localhost`
- port：`3306`
- database：`aichat`
- user：`aichat_user`
- password：`aichat123!`（仅限本机测试库；禁止提交到远端/禁止外发包含该信息的文件）
- username：root
- password：root123!

### 2026-04-13 后端配置调整（properties -> yml）
- 需求：Spring Boot 后端配置文件改用 `application.yml`，替换 `application.properties`，避免双配置冲突。

### 2026-04-13 稳定性优化（DeepSeek/Python 500 透传）
- 现象：前端偶发看到 `500 INTERNAL SERVER ERROR`，内容为 DeepSeek 调用失败后的 Python 500。
- 目标：后端捕获 Python 5xx 异常并解析其 JSON 错误体，向前端返回可读的 `error` 字段，便于排查（例如限流/超时/配额）。

### 2026-04-13 对话流式输出（真流式）
- 背景：当前链路为一次性返回（前端 `await response.json()`；后端 `RestTemplate.postForObject`；Python 也为一次性请求），用户需要等待完整响应。
- 目标：实现“边生成边显示”的真流式输出，提高首 token 速度与交互体验。
- 方案：采用 SSE（`text/event-stream`）事件协议，前端用 `fetch` POST 读取 `ReadableStream`（A1），保留 `X-User-Id` header。
- 接口形态：
  - 新增 Java：`POST /api/chat/stream`（事件：`token`/`done`/`error`，`done` 携带 `conversationId`）。
  - 新增 Python：`POST /api/chat/stream`，对 DeepSeek 启用 `stream: true` 并转成统一 SSE 事件。
- 兼容：保留原有 `POST /api/chat` 一次性接口作为回退与兼容入口。

### 2026-04-20 登录体系（微信扫码登录 -> 使用 AI 聊天）
- 背景：项目已启动但目前无登录系统；希望通过微信扫码登录，登录成功后才能使用 AI 聊天功能。
- 总目标：引入“可落地的最小登录闭环”，实现 **扫码 -> 回调 -> 建立登录态 -> 前端拿到当前用户信息 -> 进入聊天**，并与现有“记忆/多会话”能力打通。
- 关键决策（与现有规划的兼容方式）：
  - 现有匿名方案用 `userId`（前端持久化）作为主键；新增登录后改为 **server-side 的用户标识**（例如 `accountId` / `wxOpenId` 映射出的内部用户 id）。
  - 兼容迁移：若用户从匿名升级到登录，支持将匿名 `userId` 下的会话归并到登录账户（可选开关，优先保证最小闭环可用）。
  - 聊天接口鉴权：后续逐步从 `X-User-Id` 过渡到 **Cookie Session 或 JWT**（以更贴近扫码登录体系），并为 SSE/普通接口都保持一致。
- 微信登录落地路径（两阶段，避免一口吃成胖子）：
  - 阶段 A（开发/本地可用）：使用“模拟扫码/模拟回调”的方式先把登录态、权限控制、前后端联调跑通（不依赖真实微信审核/域名备案/证书）。
  - 阶段 B（生产/真实微信）：接入微信开放平台的网页扫码登录（PC 网站场景）或公众号/H5 OAuth（移动场景），完成真实扫码与回调签名校验。
- 场景与产品行为：
  - 未登录访问：进入登录页/弹出登录框；聊天页不可用或只读（明确提示“请微信扫码登录后使用”）。
  - 登录成功：跳转聊天页；展示用户头像/昵称（来自微信资料或本地用户表）。
  - 登出：清理登录态；回到未登录状态。
- 后端接口草案（先定形，便于前端对接；具体实现后续再写代码）：
  - `GET /api/auth/wechat/qr`：获取二维码/登录链接（返回 `qrUrl` 或 `redirectUrl` + `state`）。
  - `GET /api/auth/wechat/callback?code=...&state=...`：微信回调入口（校验 `state`，换取 token/openid/unionid，创建或绑定本地用户，建立登录态，重定向到前端）。
  - `GET /api/auth/me`：返回当前登录用户信息（用于前端启动时判定是否已登录）。
  - `POST /api/auth/logout`：登出。
  - （可选）`GET /api/auth/status?state=...`：轮询扫码状态（若采用“前端轮询 + 扫码确认”的交互模型）。
- 鉴权与会话方案（先确定原则）：
  - 优先方案：**后端 Session + HttpOnly Cookie**（对扫码登录与浏览器场景更自然；也便于 SSE 复用同一 cookie）。
  - 备选方案：JWT（需要考虑 token 存储与 XSS 风险；SSE/流式也要统一带 token）。
  - CSRF：若使用 Cookie Session，需要 CSRF 防护策略（同站策略 + CSRF token 或限定同源）。
- 数据库与表设计（新增，最小集合）：
  - `users`：内部用户表（id、created_at、last_login_at、status）。
  - `wechat_accounts`：微信账号绑定表（user_id、open_id、union_id、nickname、avatar、scope、raw_profile、updated_at）。
  - `auth_sessions`（可选）：若不完全依赖容器 session，可记录 session（session_id、user_id、expires_at、revoked）。
  - 现有“会话/消息/记忆”表：需要新增 `user_id` 外键或替换原先的匿名标识字段，确保按登录用户隔离数据。
- 安全与风控要点（明确纳入验收标准）：
  - `state` 防重放：强随机、短时有效、一次性使用。
  - 回调签名/参数校验：严格校验 `state`，禁止开放重定向（redirect whitelist）。
  - 账号绑定策略：同一个微信 openid/unionid 只能绑定一个内部用户；重复登录可幂等。
  - 日志：登录关键链路打点（成功/失败原因），但不得泄露敏感信息。
- 前端改造点（不写代码，先定范围）：
  - 新增登录页/弹窗组件：展示二维码，提供刷新/过期提示。
  - App 启动时调用 `/api/auth/me` 获取用户态；路由守卫保护聊天页。
  - 聊天请求：携带 cookie（或 token）；删除/弱化 `X-User-Id` 依赖（保留兼容期）。
- 与“记忆功能/多会话”的整合点：
  - `conversationId` 归属到登录用户；同一用户可多会话（保留 A2）。
  - 匿名到登录迁移（可选）：在登录回调完成后，如果检测到本地存在匿名 `userId`，可触发一次“会话归并”接口（例如 `POST /api/auth/merge-anon`）。
- 环境与部署约束（现实可行性清单）：
  - 真实微信扫码登录通常要求公网可访问的回调域名、HTTPS 证书、以及在平台配置白名单。
  - 本地开发先走阶段 A，保证项目不被外部依赖阻塞；待有域名/证书后切换到阶段 B。

### 2026-04-20 登录体系（阶段 B：真实微信网页扫码登录 QRConnect）
- 选择场景：PC 网站“微信扫码登录”（微信开放平台 `qrconnect`）。
- 关键链路（后端为主）：
  - 生成二维码链接：`https://open.weixin.qq.com/connect/qrconnect?...&state=...#wechat_redirect`
  - 微信回调：`GET /api/auth/wechat/callback?code=...&state=...`
  - 后端用 `code` 换取：`access_token`/`openid`/`unionid`（`sns/oauth2/access_token`）
  - （可选但常用）拉取用户资料：`sns/userinfo`（得到 `nickname`/`headimgurl` 等）
  - 写入绑定表 `wechat_accounts`（openid/unionid -> user_id），建立 Session，跳回前端。
- 配置项（不写死，全部走环境变量）：
  - `AICHAT_WECHAT_APP_ID`
  - `AICHAT_WECHAT_APP_SECRET`
  - `AICHAT_WECHAT_CALLBACK_URL`（必须与开放平台配置一致，且公网 HTTPS）
  - `AICHAT_WECHAT_POST_LOGIN_REDIRECT_URL`（登录成功后跳回的前端地址）
- 安全约束补充：
  - `state` 必须一次性使用 + 过期；回调时强校验，否则拒绝。
  - 跳转地址必须白名单/固定配置，禁止用户可控的开放重定向。
