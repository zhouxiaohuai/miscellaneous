# 快速开始指南

## 5 分钟快速上手

### 前置条件

1. ✅ 已安装 Android Studio
2. ✅ 已安装 JDK 17
3. ✅ 已安装 Android SDK 34

### 步骤 1：打开项目

1. 启动 Android Studio
2. 点击「Open」
3. 浏览到 `D:\javawk\project01\p-03` 目录
4. 点击「OK」
5. 等待 Gradle 同步完成（约 2-5 分钟）

### 步骤 2：连接设备

#### 使用模拟器
1. 点击工具栏的「Device Manager」图标
2. 点击「Create Virtual Device」
3. 选择「Pixel 6」或「Pixel 7」
4. 选择系统镜像：Android 14 (API 34)
5. 点击「Finish」
6. 点击启动按钮 ▶️ 启动模拟器

#### 使用真机
1. 手机开启「开发者选项」
2. 开启「USB 调试」
3. 用 USB 线连接手机到电脑
4. 手机上点击「允许 USB 调试」

### 步骤 3：运行应用

1. 在 Android Studio 工具栏选择目标设备
2. 点击绿色的「Run」按钮 ▶️（或按 Shift+F10）
3. 等待编译和安装完成
4. 应用会自动启动

### 步骤 4：设置权限

应用启动后，按照界面提示：

1. **悬浮窗权限**
   - 点击「去设置」
   - 找到「全局朗读助手」
   - 开启「显示在其他应用上层」
   - 返回应用

2. **无障碍服务**
   - 点击「去设置」
   - 找到「无障碍」或「辅助功能」
   - 找到「全局朗读助手」
   - 开启服务
   - 返回应用

3. **华为设备特别设置**（仅华为手机）
   - 按照提示打开「手机管家」
   - 设置后台保护

### 步骤 5：开始使用

1. 点击「开始使用」按钮
2. 按 Home 键回到桌面
3. 打开任意应用（微信、浏览器等）
4. 点击屏幕上的文字
5. 听到语音朗读

## 功能演示

### 悬浮按钮

```
开启状态：🎤 麦克风图标（不透明）
关闭状态：🔇 静音图标（半透明）
```

- 点击按钮：切换开启/关闭
- 长按拖动：调整按钮位置

### 朗读功能

```
点击文字 → 自动识别 → 语音朗读
```

- 支持中文朗读
- 自动识别句子和段落
- 朗读完成后可继续点击其他文字

## 常见问题速查

### Q: Gradle 同步失败怎么办？

**A**: 
1. 检查网络连接
2. 尝试使用 VPN
3. 在 `settings.gradle.kts` 中添加阿里云镜像：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven { url = uri("https://maven.aliyun.com/repository/central") }
    google()
    mavenCentral()
}
```

### Q: 应用安装失败怎么办？

**A**:
1. 检查手机存储空间
2. 确保手机已开启「允许安装未知来源应用」
3. 检查 Android 版本是否满足要求（7.0+）

### Q: 无障碍服务无法开启怎么办？

**A**:
1. 确保应用已安装并运行过一次
2. 重启手机后重试
3. 在设置中搜索「无障碍」或「辅助功能」

### Q: 悬浮按钮不显示怎么办？

**A**:
1. 检查悬浮窗权限是否已授予
2. 在设置中搜索「显示在其他应用上层」
3. 找到本应用并开启权限

### Q: 点击文字没有朗读怎么办？

**A**:
1. 检查悬浮按钮是否显示为开启状态（麦克风图标）
2. 确保无障碍服务已开启
3. 检查手机音量设置
4. 查看 Logcat 日志排查问题

### Q: 华为手机服务被杀怎么办？

**A**:
1. 打开「手机管家」
2. 进入「启动管理」
3. 找到本应用
4. 关闭「自动管理」
5. 手动开启所有后台权限

## 开发调试

### 查看日志

在 Android Studio 底部打开「Logcat」窗口：

```
过滤器：com.example.voicereader
```

关键日志标签：
- `MyAccessibilityService`：无障碍服务日志
- `FloatingButtonService`：悬浮按钮日志
- `TtsManager`：TTS 语音日志

### 调试技巧

1. **断点调试**
   - 在代码行号左侧点击添加断点
   - 以 Debug 模式运行（Shift+F9）
   - 程序会在断点处暂停

2. **查看变量**
   - 在 Debug 窗口中查看变量值
   - 使用 Evaluate Expression 计算表达式

3. **修改代码**
   - 修改代码后点击「Apply Changes」⚡
   - 无需重新安装应用

## 生成 APK

### 方法 1：使用 Android Studio

1. 菜单：Build → Build Bundle(s) / APK(s) → Build APK(s)
2. 等待编译完成
3. 点击「locate」查看 APK 文件

### 方法 2：使用命令行

```bash
# 进入项目目录
cd D:\javawk\project01\p-03

# 生成 debug APK
./gradlew assembleDebug

# APK 位置
# app/build/outputs/apk/debug/app-debug.apk
```

### 安装到手机

```bash
# 使用 adb 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或者直接将 APK 文件传到手机上安装
```

## 下一步

完成快速开始后，建议阅读：

1. **[README.md](README.md)**：项目完整说明
2. **[PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)**：详细项目结构
3. **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)**：开发指南

## 获取帮助

遇到问题？

1. 查看 [常见问题速查](#常见问题速查)
2. 阅读完整文档
3. 搜索错误信息
4. 在 Logcat 中查看详细日志

## 示例代码

### 修改悬浮按钮大小

编辑 `app/src/main/res/layout/floating_button.xml`：

```xml
<FrameLayout
    android:layout_width="80dp"    <!-- 修改宽度 -->
    android:layout_height="80dp"   <!-- 修改高度 -->
    ...>
```

### 修改 TTS 语速

编辑 `app/src/main/java/com/example/voicereader/tts/TtsManager.kt`：

```kotlin
private const val DEFAULT_SPEECH_RATE = 1.2f  # 增加语速
```

### 修改朗读语言

编辑 `app/src/main/java/com/example/voicereader/tts/TtsManager.kt`：

```kotlin
// 在 onInit 方法中
tts?.language = Locale.US  // 改为英文
```

---

*文档创建时间：2026-06-05*
*预计阅读时间：5 分钟*
