package com.example.voicereader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.example.voicereader.MainActivity
import com.example.voicereader.R

/**
 * 悬浮按钮服务
 * 负责显示和管理全局悬浮按钮
 */
class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "FloatingButtonService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_button_channel"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var iconView: ImageView? = null

    // 按钮状态（从 SharedPreferences 读取）
    private var isServiceEnabled = true

    // 拖动相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "悬浮按钮服务创建")

        // 读取保存的状态
        isServiceEnabled = getSharedPreferences("voice_reader", Context.MODE_PRIVATE)
            .getBoolean("service_enabled", true)

        // 创建通知渠道
        createNotificationChannel()

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())

        // 初始化悬浮按钮
        initFloatingButton()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "朗读服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "全局朗读助手运行中"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("全局朗读助手")
            .setContentText("服务运行中，点击文字即可朗读")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun initFloatingButton() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 创建悬浮按钮视图
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        iconView = floatingView?.findViewById(R.id.iv_floating_icon)

        // 设置悬浮窗口参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // 设置触摸事件（支持拖动和点击）
        setupTouchListener(params)

        // 添加到窗口
        windowManager?.addView(floatingView, params)

        // 更新图标状态
        updateIcon()
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    // 如果移动超过一定距离，认为是拖动
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                    }

                    if (isDragging) {
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 点击事件
                        onFloatingButtonClick()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun onFloatingButtonClick() {
        isServiceEnabled = !isServiceEnabled

        // 保存状态到 SharedPreferences
        getSharedPreferences("voice_reader", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_enabled", isServiceEnabled)
            .apply()

        // 通知无障碍服务切换状态
        val intent = Intent("com.example.voicereader.TOGGLE").apply {
            putExtra("enabled", isServiceEnabled)
        }
        sendBroadcast(intent)

        // 更新图标
        updateIcon()

        // 显示提示
        val message = if (isServiceEnabled) "朗读服务已开启" else "朗读服务已关闭"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        Log.d(TAG, "服务状态切换: $isServiceEnabled")
    }

    private fun updateIcon() {
        iconView?.setImageResource(
            if (isServiceEnabled) {
                R.drawable.ic_mic
            } else {
                R.drawable.ic_mic_off
            }
        )

        // 设置透明度
        iconView?.alpha = if (isServiceEnabled) 1.0f else 0.5f
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "悬浮按钮服务销毁")

        try {
            windowManager?.removeView(floatingView)
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮按钮失败", e)
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
