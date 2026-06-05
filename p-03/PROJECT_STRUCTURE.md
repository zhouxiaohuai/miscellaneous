# 项目结构说明

## 目录结构

```
p-03/
├── requirements.md                 # 需求文档
├── README.md                      # 项目说明文档
├── PROJECT_STRUCTURE.md           # 项目结构说明（本文件）
│
├── build.gradle.kts               # 项目级构建脚本
├── settings.gradle.kts            # 项目设置
├── gradle.properties              # Gradle 配置属性
│
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties  # Gradle Wrapper 配置
│
└── app/                           # 应用模块
    ├── build.gradle.kts           # 模块级构建脚本
    ├── proguard-rules.pro         # ProGuard 混淆规则
    │
    └── src/
        └── main/
            ├── AndroidManifest.xml    # 应用清单文件
            │
            ├── java/com/example/voicereader/
            │   ├── MainActivity.kt           # 主页面（权限引导）
            │   │
            │   ├── service/
            │   │   ├── MyAccessibilityService.kt  # 无障碍服务（核心）
            │   │   └── FloatingButtonService.kt   # 悬浮按钮服务
            │   │
            │   ├── tts/
            │   │   └── TtsManager.kt         # TTS 语音合成管理
            │   │
            │   ├── utils/
            │   │   ├── AccessibilityUtils.kt  # 无障碍服务工具
            │   │   └── HuaweiUtils.kt        # 华为设备兼容工具
            │   │
            │   └── receiver/
            │       └── BootReceiver.kt       # 开机自启广播接收器
            │
            └── res/
                ├── layout/
                │   ├── activity_main.xml     # 主页面布局
                │   └── floating_button.xml   # 悬浮按钮布局
                │
                ├── drawable/
                │   ├── ic_mic.xml           # 麦克风图标（开启状态）
                │   ├── ic_mic_off.xml       # 麦克风图标（关闭状态）
                │   └── circle_background.xml # 圆形背景
                │
                ├── xml/
                │   └── accessibility_config.xml  # 无障碍服务配置
                │
                └── values/
                    ├── colors.xml           # 颜色资源
                    ├── strings.xml          # 字符串资源
                    └── themes.xml           # 主题样式
```

## 核心文件说明

### 1. AndroidManifest.xml
应用清单文件，声明：
- 应用权限（悬浮窗、前台服务等）
- Activity 和 Service 组件
- 无障碍服务配置
- 开机自启广播接收器

### 2. MainActivity.kt
主页面，负责：
- 显示权限状态
- 引导用户开启权限
- 启动悬浮按钮服务
- 华为设备特殊引导

### 3. MyAccessibilityService.kt
无障碍服务（核心），负责：
- 监听屏幕点击事件
- 获取点击位置的文字内容
- 调用 TTS 朗读文字
- 支持识别段落和句子

### 4. FloatingButtonService.kt
悬浮按钮服务，负责：
- 显示全局悬浮按钮
- 处理按钮点击和拖动
- 开启/关闭朗读服务
- 前台服务保活

### 5. TtsManager.kt
TTS 管理器，负责：
- 初始化 TTS 引擎
- 文字转语音朗读
- 语速和音调控制
- 朗读状态监听

### 6. AccessibilityUtils.kt
无障碍服务工具，负责：
- 检查无障碍服务是否开启
- 获取服务状态

### 7. HuaweiUtils.kt
华为设备工具，负责：
- 检测华为设备
- 检测 HarmonyOS
- 后台保护状态管理
- 打开华为设置页面

### 8. BootReceiver.kt
开机自启接收器，负责：
- 监听开机广播
- 自动启动悬浮按钮服务

## 资源文件说明

### 布局文件
- `activity_main.xml`：主页面布局，包含权限状态卡片和操作按钮
- `floating_button.xml`：悬浮按钮布局，圆形背景 + 麦克风图标

### Drawable 资源
- `ic_mic.xml`：麦克风图标（开启状态，白色填充）
- `ic_mic_off.xml`：麦克风图标（关闭状态，带斜线）
- `circle_background.xml`：圆形背景（主题色填充）

### XML 配置
- `accessibility_config.xml`：无障碍服务配置，声明监听的事件类型和能力

### Values 资源
- `colors.xml`：颜色定义（主题色、文字色、状态色等）
- `strings.xml`：字符串资源（中英文支持）
- `themes.xml`：主题样式（Material Design）

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    用户界面层                            │
│  ┌─────────────────────────────────────────────────┐   │
│  │              MainActivity                        │   │
│  │         （权限引导和状态显示）                      │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    服务层                               │
│  ┌─────────────────┐    ┌─────────────────────────┐   │
│  │ FloatingButton  │    │ MyAccessibilityService  │   │
│  │    Service      │◄──►│      （核心服务）        │   │
│  │  （悬浮按钮）    │    │   （文字识别和朗读）     │   │
│  └─────────────────┘    └─────────────────────────┘   │
│                           │                           │
│                           ▼                           │
│  ┌─────────────────────────────────────────────────┐   │
│  │              TtsManager                          │   │
│  │           （语音合成引擎）                        │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    工具层                               │
│  ┌─────────────────┐    ┌─────────────────────────┐   │
│  │ Accessibility   │    │     HuaweiUtils         │   │
│  │    Utils        │    │   （华为设备兼容）       │   │
│  └─────────────────┘    └─────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## 数据流

```
用户点击屏幕文字
        │
        ▼
AccessibilityEvent 触发
        │
        ▼
MyAccessibilityService 接收事件
        │
        ▼
提取 AccessibilityNodeInfo
        │
        ▼
递归遍历获取文字内容
        │
        ▼
调用 TtsManager.speak(text)
        │
        ▼
TextToSpeech 引擎朗读
        │
        ▼
用户听到语音
```

## 权限流程

```
应用启动
    │
    ▼
检查悬浮窗权限 ──── 未授权 ──── 引导用户开启
    │
    ▼ 已授权
检查无障碍服务 ──── 未开启 ──── 引导用户开启
    │
    ▼ 已开启
检查华为后台保护 ── 未设置 ──── 引导用户设置（仅华为设备）
    │
    ▼ 已设置
所有权限就绪
    │
    ▼
启动悬浮按钮服务
    │
    ▼
用户可开始使用
```

## 编译和运行

### 前置条件
1. 安装 Android Studio Hedgehog (2023.1.1) 或更高版本
2. 安装 JDK 17
3. 安装 Android SDK 34

### 编译步骤
1. 用 Android Studio 打开项目根目录
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 按钮或按 Shift+F10

### 生成 APK
1. 菜单：Build → Build Bundle(s) / APK(s) → Build APK(s)
2. 等待编译完成
3. APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

## 测试建议

### 模拟器测试
- 使用 API 30+ 的模拟器
- 测试基本功能和 UI

### 真机测试
- 使用华为畅享90 Pro Max 测试
- 验证无障碍服务功能
- 测试后台运行稳定性
- 验证 TTS 语音输出

## 注意事项

1. **无障碍服务权限**：需要用户手动开启，无法自动获取
2. **华为后台保护**：华为设备需要手动设置后台保护，否则服务会被杀掉
3. **TTS 引擎**：不同设备的 TTS 引擎可能不同，需要测试兼容性
4. **系统版本**：部分功能需要 Android 7.0+，建议 targetSdk 设为 34

## 后续优化

1. 添加语速和音量调节
2. 支持多语言朗读
3. 添加朗读历史记录
4. 优化文字识别准确率
5. 添加收藏功能
6. 支持自定义悬浮按钮样式
