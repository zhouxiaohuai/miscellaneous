# 开发指南

## 环境搭建

### 1. 安装 Android Studio

1. 访问 https://developer.android.com/studio
2. 下载最新版本的 Android Studio
3. 运行安装程序，按默认设置安装
4. 首次启动时选择「Standard」安装类型

### 2. 配置 Android SDK

1. 打开 Android Studio
2. 进入 Settings → Appearance & Behavior → System Settings → Android SDK
3. 确保安装以下组件：
   - Android 14 (API 34)
   - Android SDK Build-Tools 34
   - Android SDK Platform-Tools
   - Android Emulator

### 3. 配置 JDK

1. 进入 Settings → Build, Execution, Deployment → Build Tools → Gradle
2. 设置 Gradle JDK 为 JDK 17
3. 如果没有 JDK 17，点击「Download JDK」下载

## 项目导入

### 1. 克隆或下载项目

```bash
# 如果使用 Git
git clone <repository-url>

# 或者直接下载 ZIP 文件并解压
```

### 2. 用 Android Studio 打开项目

1. 打开 Android Studio
2. 选择「Open」
3. 浏览到项目根目录（包含 `build.gradle.kts` 的目录）
4. 点击「OK」
5. 等待 Gradle 同步完成（首次可能需要几分钟）

### 3. 解决依赖问题

如果 Gradle 同步失败：

1. 检查网络连接
2. 尝试使用 VPN 或镜像源
3. 进入 Settings → Build, Execution, Deployment → Gradle
4. 勾选「Offline work」后取消勾选，重新同步

## 项目结构

```
app/
├── src/main/
│   ├── java/com/example/voicereader/
│   │   ├── MainActivity.kt           # 主页面
│   │   ├── service/
│   │   │   ├── MyAccessibilityService.kt  # 无障碍服务
│   │   │   └── FloatingButtonService.kt   # 悬浮按钮服务
│   │   ├── tts/
│   │   │   └── TtsManager.kt         # TTS 管理
│   │   ├── utils/
│   │   │   ├── AccessibilityUtils.kt  # 无障碍工具
│   │   │   └── HuaweiUtils.kt        # 华为工具
│   │   └── receiver/
│   │       └── BootReceiver.kt       # 开机自启
│   ├── res/
│   │   ├── layout/                    # 布局文件
│   │   ├── drawable/                  # 图形资源
│   │   ├── xml/                       # XML 配置
│   │   └── values/                    # 值资源
│   └── AndroidManifest.xml            # 应用清单
```

## 核心功能实现

### 1. 无障碍服务

无障碍服务是本应用的核心，用于监听屏幕事件和获取文字内容。

**配置文件**：`res/xml/accessibility_config.xml`

```xml
<accessibility-service
    android:accessibilityEventTypes="typeViewClicked|typeViewLongClicked"
    android:accessibilityFeedbackType="feedbackSpoken"
    android:canRetrieveWindowContent="true"
    ... />
```

**服务类**：`MyAccessibilityService.kt`

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // 处理点击事件
    val source = event?.source
    val text = extractTextFromNode(source)
    speak(text)
}
```

### 2. 悬浮按钮

悬浮按钮使用 WindowManager 实现，可以显示在任何应用上层。

**权限要求**：
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

**实现方式**：
```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    PixelFormat.TRANSLUCENT
)
windowManager.addView(floatingView, params)
```

### 3. TTS 语音合成

使用 Android 原生的 TextToSpeech API。

**初始化**：
```kotlin
tts = TextToSpeech(context) { status ->
    if (status == TextToSpeech.SUCCESS) {
        tts?.language = Locale.CHINESE
    }
}
```

**朗读文字**：
```kotlin
tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
```

## 调试技巧

### 1. 查看日志

使用 Logcat 查看应用日志：

```bash
# 过滤本应用日志
adb logcat | grep "com.example.voicereader"

# 或者在 Android Studio 的 Logcat 窗口中过滤
```

### 2. 调试无障碍服务

1. 在 `MyAccessibilityService.kt` 中添加日志
2. 使用 `adb shell dumpsys accessibility` 查看无障碍服务状态
3. 在模拟器中测试点击事件

### 3. 调试悬浮按钮

1. 确保已授予悬浮窗权限
2. 在 Logcat 中查看服务启动日志
3. 检查 WindowManager 是否正确添加视图

### 4. 测试 TTS

1. 在模拟器中安装 Google TTS 引擎
2. 调用 `tts?.speak()` 后查看 Logcat 日志
3. 如果没有声音，检查设备音量设置

## 常见问题解决

### 1. Gradle 同步失败

**问题**：`Could not resolve com.android.tools.build:gradle:x.x.x`

**解决**：
1. 检查网络连接
2. 使用国内镜像源（阿里云、腾讯云等）
3. 在 `settings.gradle.kts` 中添加镜像：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven { url = uri("https://maven.aliyun.com/repository/central") }
    google()
    mavenCentral()
}
```

### 2. 无障碍服务无法开启

**问题**：设置中找不到本应用的无障碍服务

**解决**：
1. 确保应用已安装并运行过一次
2. 检查 `AndroidManifest.xml` 中的服务声明
3. 重启手机后重试

### 3. 悬浮按钮不显示

**问题**：启动服务后看不到悬浮按钮

**解决**：
1. 检查悬浮窗权限是否已授予
2. 在设置中搜索「显示在其他应用上层」
3. 找到本应用并开启权限

### 4. 华为手机服务被杀

**问题**：一段时间后服务停止运行

**解决**：
1. 打开「手机管家」
2. 进入「启动管理」
3. 找到本应用，关闭「自动管理」
4. 手动开启所有后台权限

### 5. TTS 没有声音

**问题**：点击文字后没有语音输出

**解决**：
1. 检查设备音量设置
2. 确保 TTS 引擎已安装（设置 → 无障碍 → 文字转语音）
3. 在代码中添加日志检查 TTS 是否初始化成功

## 发布应用

### 1. 生成签名 APK

1. 菜单：Build → Generate Signed Bundle / APK
2. 选择「APK」
3. 创建或选择密钥库
4. 选择「release」构建类型
5. 点击「Finish」

### 2. 使用 Gradle 命令

```bash
# 生成 debug APK
./gradlew assembleDebug

# 生成 release APK
./gradlew assembleRelease
```

### 3. APK 位置

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release.apk`

## 性能优化

### 1. 减少内存占用

- 及时回收 AccessibilityNodeInfo
- 避免在无障碍服务中创建过多对象
- 使用弱引用持有 Context

### 2. 优化文字识别

- 缓存常用节点信息
- 避免递归过深
- 限制文字长度

### 3. 优化 TTS 性能

- 复用 TTS 实例
- 避免频繁初始化/销毁
- 使用队列管理朗读任务

## 代码规范

### 1. 命名规范

- 类名：PascalCase（如 `MyAccessibilityService`）
- 方法名：camelCase（如 `extractTextFromNode`）
- 常量：UPPER_SNAKE_CASE（如 `DEFAULT_SPEECH_RATE`）
- 变量：camelCase（如 `speechRate`）

### 2. 注释规范

```kotlin
/**
 * 从节点提取文字内容
 * 会递归遍历子节点
 *
 * @param node 要提取文字的节点
 * @return 提取到的文字内容
 */
private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
    // 实现细节
}
```

### 3. 代码组织

- 使用 companion object 定义常量
- 使用 private 修饰内部方法
- 使用 data class 定义数据模型
- 使用 object 定义工具类

## 版本控制

### 1. Git 规范

```bash
# 提交规范
git commit -m "feat: 添加悬浮按钮功能"
git commit -m "fix: 修复华为设备后台被杀问题"
git commit -m "docs: 更新 README 文档"
```

### 2. 分支管理

```
main        # 主分支，稳定版本
develop     # 开发分支
feature/*   # 功能分支
bugfix/*    # 修复分支
release/*   # 发布分支
```

## 测试策略

### 1. 单元测试

```kotlin
@Test
fun testExtractTextFromNode() {
    // 测试文字提取功能
}
```

### 2. 集成测试

```kotlin
@Test
fun testAccessibilityServiceIntegration() {
    // 测试无障碍服务集成
}
```

### 3. UI 测试

```kotlin
@Test
fun testMainActivityUI() {
    // 测试主页面 UI
}
```

## 持续集成

### 1. GitHub Actions 示例

```yaml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Build with Gradle
      run: ./gradlew build
```

## 参考资源

- [Android 开发者文档](https://developer.android.com)
- [Kotlin 官方文档](https://kotlinlang.org/docs/)
- [Android AccessibilityService 文档](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [TextToSpeech 文档](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [WindowManager 文档](https://developer.android.com/reference/android/view/WindowManager)

---

*文档创建时间：2026-06-05*
*最后更新：2026-06-05*
