package com.quizguard.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.quizguard.app.core.GuardState
import com.quizguard.app.core.GuardWatchdog
import com.quizguard.app.core.Notify
import com.quizguard.app.core.ParentPrefs

/**
 * 前台服务：仅负责「保活」与显示常驻通知，使进程（及 GuardState 的 10 分钟倒计时）
 * 在儿童切换后台 / 使用其他 App 时不被系统回收；并满足 Android 10+ 对
 * 「后台启动 Activity」的要求（有前台服务在跑时才允许到点自动弹出答题界面）。
 *
 * 真正的计时与状态机在 [GuardState]，本服务只是把启停指令转给它。
 */
class GuardService : Service() {

    companion object {
        const val ACTION_START = "com.quizguard.app.START"
        const val ACTION_STOP = "com.quizguard.app.STOP"
        const val ACTION_RESUME = "com.quizguard.app.RESUME"

        /** 进程内标志；进程被杀后静态变量复位为 false，看门狗据此判断是否需要恢复 */
        @Volatile
        @JvmStatic
        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ParentPrefs.init(this)
        when (intent?.action) {
            ACTION_STOP -> {
                GuardState.stopGuard()
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                // 看门狗发现进程/服务不在后拉起：按被杀前的阶段恢复
                showForeground()
                isRunning = true
                GuardState.resumeAfterKill()
            }
            ACTION_START -> {
                showForeground()
                isRunning = true
                GuardState.startGuard()
            }
            else -> {
                // START_STICKY 重启时 intent 可能为 null：只有家长确实开启过守护才恢复
                if (ParentPrefs.isGuardEnabled()) {
                    showForeground()
                    isRunning = true
                    GuardState.resumeAfterKill()
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        // v1.0.11：系统内存回收后尽量自动重启；任务栏强杀则由 GuardWatchdog 闹钟兜底
        return START_STICKY
    }

    private fun showForeground() {
        startForeground(
            Notify.NOTIF_ID,
            Notify.build(
                this,
                ParentPrefs.isQuizActive(),
                count = ParentPrefs.getQuizCount(),
                minutes = ParentPrefs.getCycleMin()
            )
        )
    }

    /**
     * v1.0.13 头条修复：孩子从「最近任务」上滑清掉 App 主任务时触发。
     *
     * 关键事实：本服务是前台服务（startForeground），它所在的进程**不会**因为主任务被移除而
     * 立即死亡——进程仍存活，[GuardState] 单例（题目、进度、quizActive 标记）全部保留。
     * 因此这是「切走秒恢复」的最佳时机：立刻把答题/守护界面重新顶回前台，
     * 把原来 20 秒看门狗空窗缩到几乎为零，孩子上滑清不掉进程。
     *
     * 若个别 ROM（如 MIUI 强杀）在 onTaskRemoved 之后真的回收了进程，则退化为
     * [GuardWatchdog] 的 8 秒心跳兜底（从已持久化的 guardEnabled/quizActive 恢复）。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!ParentPrefs.isGuardEnabled()) return

        // 1) 重新挂一次前台通知，确保常驻通知不丢
        try { showForeground() } catch (_: Exception) { }
        // 2) 续上心跳闹钟，防进程稍后真被系统回收后还能复活
        try { GuardWatchdog.schedule(this) } catch (_: Exception) { }

        // 3) 同一进程内直接把 MainActivity 重新拉回前台：
        //    GuardState 单例还在，phase=QUIZ 时 MainActivity.onResume 会自动重新进入锁屏并续上题目。
        val resume = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_FROM_KILL, true)
        }
        try {
            startActivity(resume)
        } catch (_: Exception) {
            // 少数 ROM 限制前台服务内启动 Activity：立刻补一条全屏通知把界面顶回来，
            // 不必等 8 秒看门狗心跳（v1.0.16）
            try {
                Notify.showQuiz(this, ParentPrefs.getQuizCount())
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
