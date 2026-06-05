package com.example.voicereader.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * 华为设备工具类
 * 处理华为设备的特殊兼容性问题
 */
object HuaweiUtils {

    private const val TAG = "HuaweiUtils"

    /**
     * 检测是否是华为设备
     */
    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.uppercase() ?: ""
        val brand = Build.BRAND?.uppercase() ?: ""

        return manufacturer.contains("HUAWEI") ||
                manufacturer.contains("HONOR") ||
                brand.contains("HUAWEI") ||
                brand.contains("HONOR")
    }

    /**
     * 检测是否是 HarmonyOS
     */
    fun isHarmonyOS(): Boolean {
        return try {
            val clazz = Class.forName("com.huawei.system.BuildEx")
            val method = clazz.getMethod("getOsBrand")
            val osBrand = method.invoke(null) as? String
            osBrand?.contains("harmony", ignoreCase = true) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取华为设备型号
     */
    fun getDeviceModel(): String {
        return "${Build.BRAND} ${Build.MODEL}"
    }

    /**
     * 检查后台保护是否已设置
     * 注意：这个方法只能检测部分设置，华为的后台保护设置比较复杂
     */
    fun isBackgroundProtectionEnabled(context: Context): Boolean {
        // 华为的后台保护设置没有统一的 API 来检测
        // 这里返回 false，引导用户手动设置
        // 实际应用中可以通过用户手动确认来记录状态
        return getBackgroundProtectionStatus(context)
    }

    /**
     * 获取后台保护状态（通过 SharedPreferences 记录）
     */
    private fun getBackgroundProtectionStatus(context: Context): Boolean {
        val prefs = context.getSharedPreferences("huawei_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("background_protection_set", false)
    }

    /**
     * 保存后台保护状态
     */
    fun saveBackgroundProtectionStatus(context: Context, isSet: Boolean) {
        val prefs = context.getSharedPreferences("huawei_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("background_protection_set", isSet).apply()
    }

    /**
     * 打开华为手机管家
     */
    fun openHuaweiPhoneManager(context: Context) {
        try {
            // 尝试打开华为手机管家
            val intent = Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.mainscreen.MainScreenActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开华为手机管家", e)
            // 备用方案：打开应用详情页
            openAppDetails(context)
        }
    }

    /**
     * 打开应用详情页
     */
    fun openAppDetails(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开应用详情页", e)
        }
    }

    /**
     * 打开华为启动管理页面
     */
    fun openHuaweiStartupManager(context: Context) {
        try {
            // 尝试直接打开启动管理
            val intent = Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开启动管理页面", e)
            // 备用方案
            openHuaweiPhoneManager(context)
        }
    }

    /**
     * 获取华为设备信息（用于调试）
     */
    fun getHuaweiDeviceInfo(): String {
        return """
            设备信息:
            - 品牌: ${Build.BRAND}
            - 型号: ${Build.MODEL}
            - 制造商: ${Build.MANUFACTURER}
            - 系统版本: ${Build.VERSION.RELEASE}
            - API 版本: ${Build.VERSION.SDK_INT}
            - 是否华为: ${isHuaweiDevice()}
            - 是否鸿蒙: ${isHarmonyOS()}
        """.trimIndent()
    }
}
