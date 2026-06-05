package com.example.voicereader.utils

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.example.voicereader.service.MyAccessibilityService

/**
 * 无障碍服务工具类
 */
object AccessibilityUtils {

    /**
     * 检查无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/${MyAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (TextUtils.isEmpty(enabledServices)) {
            return false
        }

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(service, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    /**
     * 获取无障碍服务状态描述
     */
    fun getAccessibilityStatusDescription(context: Context): String {
        return if (isAccessibilityServiceEnabled(context)) {
            "无障碍服务已开启"
        } else {
            "无障碍服务未开启"
        }
    }
}
