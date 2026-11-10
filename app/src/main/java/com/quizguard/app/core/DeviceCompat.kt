package com.quizguard.app.core

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * v1.0.14 机型适配层。
 *
 * 第三方侧载 App 无法绕过厂商的保活 / 后台限制，所以「适配」= 能力检测 + 一键跳转 + 尽量自动降级：
 *
 *  - **通用层**（所有 Android 12~15 都要）：
 *    · 电池优化豁免——不开会被 Doze / 省电模式冻结；
 *    · 精确闹钟——Android 14 对 targetSdk 33+ 默认拒绝 SCHEDULE_EXACT_ALARM；
 *    · 全屏通知——Android 14 对非闹钟/通话类 App 默认不再自动授予 USE_FULL_SCREEN_INTENT；
 *    · 通知权限——关掉后答题提醒不出现。
 *  - **厂商层**（决定进程能不能活）：自启动 / 后台运行 / 后台弹出界面。各 ROM 无统一 API，
 *    这里按品牌逐个尝试公开的 ComponentName，全部失败则退回「应用详情」页，由家长手工开启。
 *
 * 这一层只做「检测 + 引导」，不改守护逻辑本身；家长台据此逐项显示 ✓ / ✗ / ○。
 */
object DeviceCompat {

    enum class Brand(val label: String) {
        XIAOMI("小米 / 红米 / POCO"),
        BLACKSHARK("黑鲨"),
        NUBIA("努比亚 / 红魔"),
        OPPO("OPPO / 一加 / realme"),
        VIVO("vivo / iQOO"),
        HUAWEI("华为 / 荣耀"),
        SAMSUNG("三星"),
        OTHER("其它安卓机型")
    }

    enum class Status { OK, MISSING, UNKNOWN }

    /** 一条适配项：标题 + 说明 + 三态 + 跳转按钮 */
    data class CompatItem(
        val id: String,
        val title: String,
        val detail: String,
        val status: Status,
        val action: String,
        val onOpen: (Context) -> Unit
    )

    fun brand(): Brand {
        val s = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            s.contains("blackshark") || s.contains("black shark") -> Brand.BLACKSHARK
            s.contains("xiaomi") || s.contains("redmi") || s.contains("poco") -> Brand.XIAOMI
            s.contains("nubia") || s.contains("redmagic") || s.contains("zte") -> Brand.NUBIA
            s.contains("oppo") || s.contains("realme") || s.contains("oneplus") -> Brand.OPPO
            s.contains("vivo") || s.contains("iqoo") -> Brand.VIVO
            s.contains("huawei") || s.contains("honor") || s.contains("hihonor") -> Brand.HUAWEI
            s.contains("samsung") -> Brand.SAMSUNG
            else -> Brand.OTHER
        }
    }

    /** 家长台标题用，例如「小米 / 红米 / POCO · Android 14」 */
    fun romLabel(): String = "${brand().label} · Android ${Build.VERSION.RELEASE}"

    // ------------------------------------------------------------------
    // 能力检测
    // ------------------------------------------------------------------

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun canScheduleExactAlarms(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun canUseFullScreenIntent(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    fun notificationsEnabled(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }

    // ------------------------------------------------------------------
    // v1.0.16 屏幕固定（Lock Task）检测 —— 决定「答题时能不能切后台杀进程」
    // ------------------------------------------------------------------

    /**
     * 系统「屏幕固定」总开关。
     *
     * 读 `Settings.Secure` 的隐藏键 `lock_to_app_enabled`（普通应用可读、不可写）。
     * 部分 ROM（尤其 MIUI 某些版本）不暴露该键，此时返回 null，UI 显示「○ 请确认」。
     */
    fun screenPinningEnabled(ctx: Context): Boolean? = try {
        Settings.Secure.getInt(ctx.contentResolver, "lock_to_app_enabled") == 1
    } catch (_: Exception) {
        null
    }

    /** 「取消固定」是否需要解锁（对应系统里的「固定时要求解锁」）。读不到返回 null。 */
    fun screenPinningExitLocked(ctx: Context): Boolean? = try {
        Settings.Secure.getInt(ctx.contentResolver, "lock_to_app_exit_locked") == 1
    } catch (_: Exception) {
        null
    }

    /** 本 App 是否已被设为设备所有者（Device Owner）——最强锁死，且无法被孩子取消固定 */
    fun isDeviceOwner(ctx: Context): Boolean = try {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        dpm.isDeviceOwnerApp(ctx.packageName)
    } catch (_: Exception) {
        false
    }

    /** 当前是否真的处于 Lock Task 锁定中（答题锁定是否生效的实时判据） */
    fun isLockTaskActive(ctx: Context): Boolean = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    } catch (_: Exception) {
        false
    }

    /**
     * 打开系统安全设置（屏幕固定开关所在页面）。
     * Android 没有公开的「屏幕固定」专属 Intent，只能进安全设置再搜索；
     * 个别 ROM 没有该页面时退回设置首页。
     */
    fun openSecuritySettings(ctx: Context) {
        if (start(ctx, Intent(Settings.ACTION_SECURITY_SETTINGS))) return
        if (start(ctx, Intent(Settings.ACTION_SETTINGS))) return
        openAppDetails(ctx)
    }

    /** 各品牌「屏幕固定」的位置提示（不同 ROM 差别很大，统一提示搜索最可靠） */
    fun screenPinningHint(brand: Brand = brand()): String = when (brand) {
        Brand.XIAOMI, Brand.BLACKSHARK ->
            "设置里搜「屏幕固定」→ 开启，并打开「固定时要求解锁」；顺手关掉「固定前询问」可免每轮弹窗"
        Brand.OPPO ->
            "设置 → 安全 → 屏幕固定（或在设置里搜「固定」）→ 开启并选择「取消前需验证锁屏密码」"
        Brand.VIVO ->
            "设置 → 安全 → 屏幕固定（设置里搜「固定」）→ 开启，退出时要求验证锁屏密码"
        Brand.HUAWEI ->
            "设置 → 安全 → 更多安全设置 → 屏幕固定 → 开启，并勾选「取消前验证锁屏密码」"
        Brand.NUBIA ->
            "设置里搜「屏幕固定」→ 开启；若机型无此功能，请改用电脑设为设备所有者"
        Brand.SAMSUNG ->
            "设置 → 安全性 → 其它安全设置 → 固定窗口 → 开启，并开启「取消前要求解锁」"
        Brand.OTHER ->
            "在设置里搜索「屏幕固定 / 固定窗口」（部分机型叫 Screen pinning）→ 开启，并让取消固定需要锁屏密码"
    }

    // ------------------------------------------------------------------
    // 跳转
    // ------------------------------------------------------------------

    private fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    private fun openAppDetails(ctx: Context) {
        start(
            ctx,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${ctx.packageName}")
            )
        )
    }

    /** 申请电池优化豁免；部分 ROM 只提供「电池优化列表」，逐级降级 */
    fun openBatteryOptimization(ctx: Context) {
        val direct = start(
            ctx,
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${ctx.packageName}")
            )
        )
        if (direct) return
        if (start(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        openAppDetails(ctx)
    }

    /** 精确闹钟授权页（Android 12+） */
    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (start(
                    ctx,
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${ctx.packageName}")
                    )
                )
            ) return
        }
        openAppDetails(ctx)
    }

    /** 全屏通知授权页（Android 14+） */
    fun openFullScreenIntentSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (start(
                    ctx,
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:${ctx.packageName}")
                    )
                )
            ) return
        }
        openAppDetails(ctx)
    }

    fun openNotificationSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        if (!start(ctx, i)) openAppDetails(ctx)
    }

    // 各品牌公开的自启动管理页（不同 ROM 版本可能改名，失败自动尝试下一个 / 退回应用详情）
    private val MIUI_AUTOSTART =
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    private val OPPO_AUTOSTART_1 =
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
    private val OPPO_AUTOSTART_2 =
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
    private val OPPO_AUTOSTART_3 =
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
    private val ONEPLUS_AUTOSTART =
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
    private val VIVO_AUTOSTART =
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
    private val IQOO_AUTOSTART =
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
    private val HUAWEI_AUTOSTART =
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
    private val HONOR_AUTOSTART =
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
    private val NUBIA_AUTOSTART =
        ComponentName("cn.nubia.security2", "cn.nubia.security2.ui.AutoStartActivity")
    private val ZTE_AUTOSTART =
        ComponentName("com.zte.heartyservice", "com.zte.heartyservice.setting.ClearAppSettingsActivity")
    private val SAMSUNG_BATTERY =
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")

    private fun candidates(brand: Brand): List<ComponentName> = when (brand) {
        Brand.XIAOMI, Brand.BLACKSHARK -> listOf(MIUI_AUTOSTART)
        Brand.OPPO -> listOf(OPPO_AUTOSTART_1, OPPO_AUTOSTART_2, OPPO_AUTOSTART_3, ONEPLUS_AUTOSTART)
        Brand.VIVO -> listOf(VIVO_AUTOSTART, IQOO_AUTOSTART)
        Brand.HUAWEI -> listOf(HUAWEI_AUTOSTART, HONOR_AUTOSTART)
        Brand.NUBIA -> listOf(NUBIA_AUTOSTART, ZTE_AUTOSTART)
        Brand.SAMSUNG -> listOf(SAMSUNG_BATTERY)
        Brand.OTHER -> emptyList()
    }

    /** 打开本品牌的自启动 / 后台管理页；无匹配组件时退回应用详情 */
    fun openAutoStart(ctx: Context) {
        for (c in candidates(brand())) {
            if (start(ctx, Intent().apply { component = c })) return
        }
        openAppDetails(ctx)
    }

    /** 各品牌需要手工确认的项（说明文字） */
    fun autoStartHint(brand: Brand = brand()): String = when (brand) {
        Brand.XIAOMI, Brand.BLACKSHARK ->
            "开「自启动」，省电策略选「无限制」，并允许「后台弹出界面」；最近任务里下拉给本应用加锁"
        Brand.OPPO ->
            "开「自启动」「关联启动」，耗电保护选「允许后台运行」；并允许「悬浮窗」"
        Brand.VIVO ->
            "开「自启动」，电池里把「后台高耗电」设为允许；i管家→权限管理→允许「后台弹出界面」"
        Brand.NUBIA ->
            "开「自启动」并把本应用加入「后台运行」白名单；红魔还需关闭游戏空间对它的限制"
        Brand.HUAWEI ->
            "应用启动管理改为「手动管理」，自启动 / 关联启动 / 后台活动全部打开"
        Brand.SAMSUNG ->
            "电池→后台使用限制，把本应用加入「从不休眠的应用」"
        Brand.OTHER ->
            "在系统设置中允许本应用「自启动 / 后台运行」，并把电池优化设为不限制"
    }

    // ------------------------------------------------------------------
    // 汇总（家长台逐项显示）
    // ------------------------------------------------------------------

    fun checklist(ctx: Context): List<CompatItem> {
        val b = brand()
        return listOf(
            CompatItem(
                id = "battery",
                title = "电池优化豁免",
                detail = "不开启会被 Doze / 省电模式冻结，倒计时与复活都会失效",
                status = if (isIgnoringBatteryOptimizations(ctx)) Status.OK else Status.MISSING,
                action = "去设置",
                onOpen = { openBatteryOptimization(it) }
            ),
            CompatItem(
                id = "exact",
                title = "精确闹钟",
                detail = "Android 14 起默认拒绝；不开启时到点提醒可能延迟几分钟",
                status = if (canScheduleExactAlarms(ctx)) Status.OK else Status.MISSING,
                action = "去设置",
                onOpen = { openExactAlarmSettings(it) }
            ),
            CompatItem(
                id = "fsi",
                title = "全屏通知",
                detail = "Android 14 起需手动授予；不开启只会弹横幅，不会自动全屏出题",
                status = if (canUseFullScreenIntent(ctx)) Status.OK else Status.MISSING,
                action = "去设置",
                onOpen = { openFullScreenIntentSettings(it) }
            ),
            CompatItem(
                id = "notif",
                title = "通知权限",
                detail = "关闭后「该答题啦」提醒不会出现，守护仍在后台运行",
                status = if (notificationsEnabled(ctx)) Status.OK else Status.MISSING,
                action = "去设置",
                onOpen = { openNotificationSettings(it) }
            ),
            CompatItem(
                id = "autostart",
                title = "自启动 / 后台运行",
                detail = autoStartHint(b),
                // 各 ROM 无统一查询 API，只能请家长人工确认
                status = Status.UNKNOWN,
                action = "去设置",
                onOpen = { openAutoStart(it) }
            )
        )
    }
}
