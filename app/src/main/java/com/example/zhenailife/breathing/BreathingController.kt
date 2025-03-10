package com.example.zhenailife.breathing

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * 呼吸階段枚舉
 */
enum class BreathingPhase {
    INHALE,  // 吸氣
    HOLD,    // 閉氣
    EXHALE,  // 吐氣
    RELAX    // 放鬆
}

/**
 * 呼吸控制器
 * 負責管理呼吸動畫的狀態和階段轉換
 */
class BreathingController {
    // 各階段持續時間 (毫秒)
    val inhaleDuration = 4000L
    val holdDuration = 2000L
    val exhaleDuration = 4000L
    val relaxDuration = 2000L
    
    // 總呼吸週期時間
    val totalCycleDuration = inhaleDuration + holdDuration + exhaleDuration + relaxDuration
    
    // 當前呼吸階段
    val currentPhase = mutableStateOf(BreathingPhase.RELAX)
    
    // 當前階段的進度 (0.0f-1.0f)
    private val _breathingProgress = mutableFloatStateOf(0f)
    val breathingProgress: State<Float> = _breathingProgress
    
    private var breathingJob: Job? = null
    
    /**
     * 開始呼吸週期
     */
    fun startBreathingCycle() {
        // 取消之前的任務
        breathingJob?.cancel()
        
        breathingJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                // 吸氣階段
                currentPhase.value = BreathingPhase.INHALE
                animatePhaseProgress(inhaleDuration)
                
                // 閉氣階段
                currentPhase.value = BreathingPhase.HOLD
                animatePhaseProgress(holdDuration)
                
                // 吐氣階段
                currentPhase.value = BreathingPhase.EXHALE
                animatePhaseProgress(exhaleDuration)
                
                // 放鬆階段
                currentPhase.value = BreathingPhase.RELAX
                animatePhaseProgress(relaxDuration)
            }
        }
    }
    
    /**
     * 動畫階段進度
     */
    private suspend fun animatePhaseProgress(duration: Long) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + duration
        
        while (System.currentTimeMillis() < endTime) {
            val currentTime = System.currentTimeMillis()
            val progress = (currentTime - startTime).toFloat() / duration.toFloat()
            _breathingProgress.floatValue = progress.coerceIn(0f, 1f)
            delay(16) // 大約60FPS
        }
        
        _breathingProgress.floatValue = 1f
    }
    
    /**
     * 停止呼吸週期
     */
    fun stopBreathingCycle() {
        breathingJob?.cancel()
        breathingJob = null
    }
} 