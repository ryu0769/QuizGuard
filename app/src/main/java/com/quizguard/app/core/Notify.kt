package com.quizguard.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.quizguard.app.MainActivity
import com.quizguard.app.R

/**
 * 通知工具：
 *  - 常驻「守护中」通知（低重要性，避免打扰，仅用于前台服务保活）；
 *  - 到点「该答题啦」通知（【高重要性独立渠道】+ 声音/震动/闪光 + 全屏 Intent），
 *    以闹钟式 heads-up 强制把答题界面顶到最前——这是解决「到时间没自动弹出」的关键。
 *
 * 注意：旧版两个通知共用一个 IMPORTANCE_LOW 渠道，导致答题通知被系统判定为低优先级、
 * 既不弹 heads-up 也不弹全屏，所以「到点弹不出来」。这里把提醒拆成独立高优先级渠道修复该问题。
 */
object Notify {

    const val NOTIF_ID = 1001
    const val PULLBACK_ID = 1002                             // 独立 ID：防逃逸「请回来答题」
    const val RESUME_ID = 1003                               // v1.0.11：看门狗复活守护
    private const val CHANNEL_ID = "shuaba_channel"           // 低优先级：常驻守护
    private const val ALERT_CHANNEL_ID = "shuaba_alert"       // 高优先级：到点提醒
    // v1.0.8 品牌更名：系统不允许修改已建渠道的显示名，只能删除旧渠道后重建
    private const val LEGACY_CHANNEL_ID = "quizguard_channel"
    private const val LEGACY_ALERT_CHANNEL_ID = "quizguard_alert"

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.deleteNotificationChannel(LEGACY_CHANNEL_ID)
                mgr.deleteNotificationChannel(LEGACY_ALERT_CHANNEL_ID)
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "刷吧!少年守护",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                    description = "守护中常驻通知"
                }
                mgr.createNotificationChannel(ch)
            }
            if (mgr.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "答题提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setShowBadge(true)
                    description = "到时间该答题的强提醒"
                    enableLights(true)
                    enableVibration(true)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    /** 进入答题界面 pendingIntent（供通知点击 / 全屏 intent） */
    private fun contentIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 构建通知（quiz=true 表示到点答题提醒；count/minutes 为家长自定义设置） */
    fun build(
        ctx: Context,
        quiz: Boolean,
        count: Int = 3,
        minutes: Int = 10
    ): android.app.Notification {
        ensureChannels(ctx)
        val pi = contentIntent(ctx)
        val builder = NotificationCompat.Builder(
            ctx,
            if (quiz) ALERT_CHANNEL_ID else CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)

        if (quiz) {
            builder
                .setContentTitle("⏰ 该答题啦！")
                .setContentText("点击进入，连做 $count 题全对才能继续玩")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
                // 全屏 intent：闹钟式 heads-up，即使锁屏 / 在其他 App 之上也会强制顶起答题界面
                .setFullScreenIntent(pi, true)
        } else {
            builder
                .setContentTitle("刷吧!少年守护中")
                .setContentText("每 $minutes 分钟答题一次，到时自动弹出")
                .setPriority(NotificationCompat.PRIORITY_LOW)
        }
        return builder.build()
    }

    /** 显示「守护中」常驻通知（低优先级渠道） */
    fun showRunning(ctx: Context, minutes: Int = 10) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, build(ctx, false, minutes = minutes))
    }

    /** 升级为「该答题啦」强提醒（高优先级渠道 + 全屏 intent） */
    fun showQuiz(ctx: Context, count: Int = 3) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, build(ctx, true, count = count))
    }

    /**
     * 防逃逸「请回来答题」提醒：用独立通知 ID，避免与常驻通知互相覆盖。
     * 同样带全屏 Intent，孩子切到其它 App 时能把答题界面重新顶到最前。
     */
    fun showPullBack(ctx: Context, count: Int = 3) {
        ensureChannels(ctx)
        val pi = contentIntent(ctx)
        val n = NotificationCompat.Builder(ctx, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ 请先完成答题")
            .setContentText("$count 道题全部答对才能继续玩手机")
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(PULLBACK_ID, n)
    }

    /**
     * v1.0.11 看门狗：进程被最近任务清掉后，用全屏通知把界面顶回前台。
     * MainActivity 在前台时再重启前台服务，绕开 Android 12+ 对「广播启动 FGS」的限制。
     */
    fun showResume(ctx: Context) {
        ensureChannels(ctx)
        val pi = contentIntent(ctx)
        val n = NotificationCompat.Builder(ctx, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("守护已恢复")
            .setContentText(
                if (ParentPrefs.isQuizActive()) "点击回到答题界面，继续完成本轮题目"
                else "守护已自动恢复，点击查看状态"
            )
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(RESUME_ID, n)
    }

    fun cancelPullBack(ctx: Context) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(PULLBACK_ID)
    }

    fun cancel(ctx: Context) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(NOTIF_ID)
        mgr.cancel(PULLBACK_ID)
        mgr.cancel(RESUME_ID)
    }
}
