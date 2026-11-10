package com.quizguard.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.quizguard.app.GuardService

/**
 * v1.0.11 守护看门狗：解决「进入最近任务 → 上滑清除进程」把守护彻底关掉的问题。
 *
 * 普通 App 无法禁用最近任务键（KEYCODE_APP_SWITCH 多数 ROM 根本不派发给应用，
 * 由 SystemUI 直接处理），所以只能缩短回拉时间，并给「进程真的被杀」留一条复活路径：
 *
 *  - 家长开启守护时 [GuardState.startGuard] 持久化 guardEnabled 并调用 [schedule]；
 *    闹钟每 8 秒唤醒一次本接收器（显式 PendingIntent，静态注册可收）。
 *  - 若服务/状态机已不在，尝试重启前台服务；Android 12+ 可能禁止从广播启动前台服务，
 *    因此同时发一条全屏通知，把 MainActivity 顶到最前，由它在前台重新拉起服务。
 *  - 被杀时若正处于答题阶段（quizActive=true），恢复后立即重新出题，不白送玩耍时间。
 *  - 家长停止守护会清掉 guardEnabled 并 [cancel]，看门狗绝不会把守护「复活」成关不掉。
 *  - 开机广播同样走一遍，重启手机后守护能自动恢复。
 *
 * 注意：v1.0.13 起，「上滑清任务」这一最常见的逃逸已由 [com.quizguard.app.GuardService.onTaskRemoved]
 * 在进程存活时**秒恢复**（无需等本闹钟）；本看门狗只作为「进程被系统真正回收」后的兜底，
 * 因此 8 秒间隔足够，不必过短以免徒增耗电与 Android 12+ 精确闹钟节流风险。
 *
 * 边界：MIUI / 部分国产 ROM 若未授予「自启动」，静态广播可能收不到，
 * 此时只能靠前台服务自身的 START_STICKY；系统设置需放开自启动与电池无限制。
 */
class GuardWatchdog : BroadcastReceiver() {

    companion object {
        const val ACTION_TICK = "com.quizguard.app.WATCHDOG_TICK"
        private const val REQ_CODE = 4201
        private const val INTERVAL_MS = 8_000L

        private fun pending(ctx: Context): PendingIntent {
            val intent = Intent(ctx, GuardWatchdog::class.java).apply { action = ACTION_TICK }
            return PendingIntent.getBroadcast(
                ctx, REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 挂下一次心跳；同一 REQ_CODE + FLAG_UPDATE_CURRENT 保证只存在一个闹钟 */
        fun schedule(ctx: Context) {
            val mgr = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + INTERVAL_MS
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(ctx))
                } else {
                    mgr.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending(ctx))
                }
            } catch (_: Exception) {
                // 用户在系统设置关闭了「闹钟和提醒」或 ROM 限制精确闹钟 → 降级为非精确
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(ctx))
                } else {
                    mgr.set(AlarmManager.RTC_WAKEUP, triggerAt, pending(ctx))
                }
            }
        }

        fun cancel(ctx: Context) {
            val mgr = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr.cancel(pending(ctx))
        }
    }

    override fun onReceive(ctx: Context, intent: Intent?) {
        ParentPrefs.init(ctx)
        // 家长已停止守护：不复活、也不再续期，闹钟到此为止
        if (!ParentPrefs.isGuardEnabled()) return

        if (!GuardService.isRunning || !GuardState.running) {
            val resume = Intent(ctx, GuardService::class.java).apply {
                action = GuardService.ACTION_RESUME
            }
            try {
                ctx.startForegroundService(resume)
            } catch (_: Exception) {
                // Android 12+ 后台启动前台服务受限，交给下面的全屏通知兜底
            }
            // 全屏 Intent 把答题界面顶到最前；MainActivity.onResume 会在前台重启服务
            Notify.showResume(ctx)
        }
        schedule(ctx)
    }
}
