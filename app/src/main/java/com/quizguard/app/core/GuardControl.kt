package com.quizguard.app.core

import android.content.Context
import android.content.Intent
import com.quizguard.app.GuardService

/**
 * 守护启停封装：通过启动/停止前台服务来开启或关闭守护。
 * - start：startForegroundService 拉起 GuardService（服务内部再调用 GuardState.startGuard 开始计时）
 * - stop：startService 发送 STOP，服务内停止计时并 self-stop
 */
object GuardControl {

    /** 开启守护（启动前台服务）。若持久化状态显示「答题中被杀」，走恢复分支立即重新出题 */
    fun start(context: Context) {
        val intent = Intent(context, GuardService::class.java).apply {
            action = if (ParentPrefs.isQuizActive()) GuardService.ACTION_RESUME
            else GuardService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    /** 停止守护（停止前台服务） */
    fun stop(context: Context) {
        val intent = Intent(context, GuardService::class.java).apply {
            action = GuardService.ACTION_STOP
        }
        context.startService(intent)
    }
}
