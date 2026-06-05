package com.example.voicereader.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.voicereader.tts.TtsManager

/**
 * 无障碍服务 - 核心服务
 * 负责监听屏幕点击事件，获取文字内容，调用 TTS 朗读
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val ACTION_TOGGLE = "com.example.voicereader.TOGGLE"
        private const val ACTION_SPEAK = "com.example.voicereader.SPEAK"

        // 服务是否运行
        var isRunning = false
            private set

        // 朗读是否启用
        var isEnabled = true
    }

    private lateinit var ttsManager: TtsManager
    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE -> {
                    isEnabled = intent.getBooleanExtra("enabled", true)
                    Log.d(TAG, "朗读服务 ${if (isEnabled) "已启用" else "已禁用"}")
                }
                ACTION_SPEAK -> {
                    val text = intent.getStringExtra("text")
                    if (!text.isNullOrEmpty()) {
                        speak(text)
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")

        isRunning = true
        ttsManager = TtsManager(this)

        // 从 SharedPreferences 读取保存的状态
        isEnabled = getSharedPreferences("voice_reader", Context.MODE_PRIVATE)
            .getBoolean("service_enabled", true)

        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE)
            addAction(ACTION_SPEAK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(toggleReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isEnabled) return

        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleClickEvent(event)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                handleClickEvent(event)
            }
        }
    }

    private fun handleClickEvent(event: AccessibilityEvent) {
        try {
            // 获取事件源节点
            val source = event.source ?: return

            // 提取文字内容
            val text = extractTextFromNode(source)

            if (text.isNotEmpty()) {
                Log.d(TAG, "识别到文字: $text")
                speak(text)
            }

            source.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "处理点击事件失败", e)
        }
    }

    /**
     * 从节点提取文字内容
     * 会递归遍历子节点
     */
    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()

        // 获取当前节点文字
        val nodeText = node.text?.toString()
        if (!nodeText.isNullOrEmpty()) {
            sb.append(nodeText)
        }

        // 获取内容描述（对于图片等）
        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrEmpty() && contentDesc != nodeText) {
            if (sb.isNotEmpty()) sb.append("，")
            sb.append(contentDesc)
        }

        // 遍历子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = extractTextFromNode(child)
            if (childText.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("，")
                sb.append(childText)
            }
            child.recycle()
        }

        return sb.toString().trim()
    }

    /**
     * 根据坐标获取节点（备用方案）
     */
    private fun getNodeAtCoordinate(x: Float, y: Float): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeAtCoordinate(rootNode, x, y)
    }

    private fun findNodeAtCoordinate(
        node: AccessibilityNodeInfo,
        x: Float,
        y: Float
    ): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        if (rect.contains(x.toInt(), y.toInt())) {
            // 优先检查子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findNodeAtCoordinate(child, x, y)
                if (result != null) {
                    return result
                }
            }
            return node
        }

        return null
    }

    private fun speak(text: String) {
        ttsManager.speak(text)
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
        ttsManager.stop()
    }

    override fun onDestroy() {
        Log.d(TAG, "无障碍服务已销毁")
        isRunning = false

        try {
            unregisterReceiver(toggleReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销广播接收器失败", e)
        }

        ttsManager.shutdown()
        super.onDestroy()
    }

    /**
     * 执行手势（点击指定坐标）
     */
    fun performClick(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(x, y)
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }
}
