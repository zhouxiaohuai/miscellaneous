package com.example.voicereader.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * TTS 语音合成管理器
 * 负责文字转语音功能
 */
class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val DEFAULT_PITCH = 1.0f
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var speechRate = DEFAULT_SPEECH_RATE
    private var pitch = DEFAULT_PITCH

    // 朗读状态回调
    var onSpeakStart: (() -> Unit)? = null
    var onSpeakComplete: (() -> Unit)? = null
    var onSpeakError: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 设置语言为中文
            val result = tts?.setLanguage(Locale.CHINESE)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e(TAG, "中文语言不支持，尝试使用默认语言")
                tts?.setLanguage(Locale.getDefault())
            }

            // 设置语速和音调
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(pitch)

            // 设置朗读监听
            setupUtteranceListener()

            isInitialized = true
            Log.d(TAG, "TTS 初始化成功")
        } else {
            Log.e(TAG, "TTS 初始化失败: $status")
            onSpeakError?.invoke("TTS 初始化失败")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "开始朗读: $utteranceId")
                onSpeakStart?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "朗读完成: $utteranceId")
                onSpeakComplete?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "朗读出错: $utteranceId")
                onSpeakError?.invoke("朗读出错")
            }
        })
    }

    /**
     * 朗读文字
     */
    fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS 未初始化，等待初始化完成...")
            // 延迟重试
            android.os.Handler(context.mainLooper).postDelayed({
                speak(text)
            }, 500)
            return
        }

        if (text.isEmpty()) {
            Log.w(TAG, "文字为空，跳过朗读")
            return
        }

        // 停止当前朗读
        stop()

        // 开始朗读
        val utteranceId = "utterance_${System.currentTimeMillis()}"
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "朗读失败")
            onSpeakError?.invoke("朗读失败")
        }
    }

    /**
     * 停止朗读
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * 暂停朗读（需要 API 21+）
     */
    fun pause() {
        // TextToSpeech 没有直接的暂停方法
        // 可以通过记录当前朗读位置实现
        stop()
    }

    /**
     * 设置语速
     * @param rate 语速，1.0 为正常，0.5 为半速，2.0 为双倍速
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.1f, 3.0f)
        tts?.setSpeechRate(speechRate)
        Log.d(TAG, "语速设置为: $speechRate")
    }

    /**
     * 设置音调
     * @param pitch 音调，1.0 为正常
     */
    fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(this.pitch)
        Log.d(TAG, "音调设置为: ${this.pitch}")
    }

    /**
     * 获取当前语速
     */
    fun getSpeechRate(): Float = speechRate

    /**
     * 获取当前音调
     */
    fun getPitch(): Float = pitch

    /**
     * 检查 TTS 是否已初始化
     */
    fun isReady(): Boolean = isInitialized

    /**
     * 获取可用的语言列表
     */
    fun getAvailableLanguages(): Set<Locale>? {
        return tts?.availableLanguages
    }

    /**
     * 设置语言
     */
    fun setLanguage(locale: Locale) {
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.e(TAG, "语言不支持: $locale")
        } else {
            Log.d(TAG, "语言设置为: $locale")
        }
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "TTS 资源已释放")
    }
}
