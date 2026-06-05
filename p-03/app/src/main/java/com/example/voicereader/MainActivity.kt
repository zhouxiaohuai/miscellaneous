package com.example.voicereader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.voicereader.databinding.ActivityMainBinding
import com.example.voicereader.service.FloatingButtonService
import com.example.voicereader.utils.AccessibilityUtils
import com.example.voicereader.utils.HuaweiUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val OVERLAY_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkAndRequestPermissions()
    }

    private fun setupUI() {
        // 开始使用按钮
        binding.btnStart.setOnClickListener {
            if (checkAllPermissions()) {
                startFloatingService()
                Toast.makeText(this, "服务已启动！", Toast.LENGTH_SHORT).show()
                // 最小化应用
                moveTaskToBack(true)
            } else {
                checkAndRequestPermissions()
            }
        }

        // 检查权限按钮
        binding.btnCheckPermissions.setOnClickListener {
            checkAndRequestPermissions()
        }

        // 更新权限状态
        updatePermissionStatus()
    }

    private fun checkAndRequestPermissions() {
        // 1. 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
            return
        }

        // 2. 检查无障碍服务
        if (!AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            showAccessibilityDialog()
            return
        }

        // 3. 华为设备特殊处理
        if (HuaweiUtils.isHuaweiDevice()) {
            if (!HuaweiUtils.isBackgroundProtectionEnabled(this)) {
                showHuaweiBackgroundDialog()
                return
            }
        }

        // 所有权限已授予
        updatePermissionStatus()
        binding.btnStart.isEnabled = true
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("为了在其他应用上显示朗读按钮，需要授予悬浮窗权限。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要开启无障碍服务")
            .setMessage("为了读取屏幕上的文字，需要开启无障碍服务。\n\n请在设置中找到「全局朗读助手」并开启。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHuaweiBackgroundDialog() {
        AlertDialog.Builder(this)
            .setTitle("华为手机特别设置")
            .setMessage(
                "为了保证朗读服务持续运行，请按照以下步骤设置：\n\n" +
                "1. 打开「手机管家」\n" +
                "2. 点击「启动管理」\n" +
                "3. 找到「全局朗读助手」\n" +
                "4. 关闭「自动管理」\n" +
                "5. 手动开启：\n" +
                "   ✓ 允许自启动\n" +
                "   ✓ 允许关联启动\n" +
                "   ✓ 允许后台活动"
            )
            .setPositiveButton("我知道了") { _, _ ->
                updatePermissionStatus()
            }
            .setNegativeButton("稍后设置", null)
            .show()
    }

    private fun checkAllPermissions(): Boolean {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = AccessibilityUtils.isAccessibilityServiceEnabled(this)
        val hasHuaweiProtection = if (HuaweiUtils.isHuaweiDevice()) {
            HuaweiUtils.isBackgroundProtectionEnabled(this)
        } else {
            true
        }
        return hasOverlay && hasAccessibility && hasHuaweiProtection
    }

    private fun updatePermissionStatus() {
        // 悬浮窗权限状态
        val hasOverlay = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = if (hasOverlay) "✅ 已授予" else "❌ 未授予"
        binding.tvOverlayStatus.setTextColor(
            if (hasOverlay) getColor(R.color.green) else getColor(R.color.red)
        )

        // 无障碍服务状态
        val hasAccessibility = AccessibilityUtils.isAccessibilityServiceEnabled(this)
        binding.tvAccessibilityStatus.text = if (hasAccessibility) "✅ 已开启" else "❌ 未开启"
        binding.tvAccessibilityStatus.setTextColor(
            if (hasAccessibility) getColor(R.color.green) else getColor(R.color.red)
        )

        // 华为后台保护状态
        if (HuaweiUtils.isHuaweiDevice()) {
            binding.layoutHuaweiProtection.visibility = android.view.View.VISIBLE
            val hasProtection = HuaweiUtils.isBackgroundProtectionEnabled(this)
            binding.tvHuaweiStatus.text = if (hasProtection) "✅ 已设置" else "❌ 未设置"
            binding.tvHuaweiStatus.setTextColor(
                if (hasProtection) getColor(R.color.green) else getColor(R.color.red)
            )
        } else {
            binding.layoutHuaweiProtection.visibility = android.view.View.GONE
        }

        // 更新开始按钮状态
        binding.btnStart.isEnabled = checkAllPermissions()
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            updatePermissionStatus()
            if (Settings.canDrawOverlays(this)) {
                checkAndRequestPermissions()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}
