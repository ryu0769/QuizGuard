package com.quizguard.app

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.annotation.RequiresApi
import com.quizguard.app.core.GuardControl
import com.quizguard.app.core.GuardState
import com.quizguard.app.core.ParentPrefs
import com.quizguard.app.core.Phase
import com.quizguard.app.ui.GuardScreen
import com.quizguard.app.ui.theme.QuizGuardTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** 由 [com.quizguard.app.GuardService.onTaskRemoved] 在「上滑清任务」后重新拉起时带入，
         *  提示本 Activity 是「被杀恢复」而非首次冷启，应立刻重进锁屏/续上题目。 */
        const val EXTRA_FROM_KILL = "com.quizguard.app.FROM_KILL"
    }

    private lateinit var adminComponent: ComponentName

    /**
     * 是否拦截返回/音量/多任务键。仅【答题阶段且非家长模式】为 true，
     * 其余阶段（守护中、家长控制台）允许正常使用与切换后台。
     */
    private var blockBack = false

    /** 当前是否处于钉屏状态（用于幂等控制，避免重复 start/stop 抛异常） */
    private var isLocked = false

    /**
     * 已申请过屏幕固定的答题轮次序号（对应 [GuardState.quizSerial]）。
     * 同一轮内只申请一次，避免 onStart/onResume/onNewIntent 多次调用导致系统确认框连环弹。
     */
    private var lockedQuizSerial = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 屏幕常亮，避免答题时息屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 题库与父母密码已在 Application 中初始化；此处再 init 一次为幂等保护
        ParentPrefs.init(this)

        // v1.0.11 看门狗：进程被「最近任务 → 清除」杀掉后，全屏通知把本界面顶回前台，
        // 此时在前台重新拉起前台服务（前台启动 FGS 不受 Android 12+ 后台限制）。
        resumeGuardIfNeeded()

        // Android 13+ 需申请通知权限，否则「该答题啦」强提醒不会弹出（弹不出答题界面的常见原因）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        adminComponent = ComponentName(this, AdminReceiver::class.java)

        // Android 13+ 预测式返回手势：注册回调，按 blockBack 决定是否消费
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerBackInvoked()
        }

        // 答题全部答对后的回调：解除钉屏并退到后台，让儿童继续玩刚才的 App
        GuardState.onUnlocked = {
            releaseLock()
            moveTaskToBack(true)
        }

        // v1.0.13：若本 Activity 是「上滑清任务后被 onTaskRemoved 重新拉起」，立即把前台标记置真
        // 并强制重进锁屏，避免恢复瞬间出现可操作的空窗。
        if (intent?.getBooleanExtra(EXTRA_FROM_KILL, false) == true) {
            GuardState.activityInFront = true
            syncLockState()
        }

        setContent {
            QuizGuardTheme {
                GuardScreen(
                    onExitApp = {
                        // 家长「退出管控」：先解除钉屏，再停状态机与前台服务、取消看门狗，
                        // 避免锁屏下 finish 无效，也避免停止后又被看门狗复活。
                        releaseLock()
                        GuardState.stopGuard()
                        GuardControl.stop(this)
                        finish()
                    }
                )
                // 阶段/家长模式变化时自动同步锁屏状态，覆盖「在家长面板内点立即出题」等
                // 不会触发生命周期事件、却需要切换锁屏的场景。
                LaunchedEffect(GuardState.phase, GuardState.parentMode) {
                    syncLockState()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        syncLockState()
    }

    override fun onResume() {
        super.onResume()
        GuardState.activityInFront = true
        // 通知只是把已有 Activity 带回前台时不会走 onCreate，这里再兜一次
        resumeGuardIfNeeded()
        syncLockState()
    }

    /** 家长曾开启守护但当前状态机/服务不在（多为进程被清掉）→ 从前台重新拉起 */
    private fun resumeGuardIfNeeded() {
        if (ParentPrefs.isGuardEnabled() && !GuardState.running) {
            try {
                GuardControl.start(this)
            } catch (_: Exception) {
                // 极少数 ROM 限制前台服务；看门狗下一次心跳还会再试
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 由后台/全屏通知拉起时，重新同步锁屏状态，确保立即进入答题锁定
        syncLockState()
    }

    override fun onPause() {
        super.onPause()
        GuardState.activityInFront = false
        // 答题阶段（非家长模式）不主动解锁：若系统钉屏生效，切走会被系统挡住；
        // 若未生效，由 GuardState 防逃逸守护把界面拉回来，避免每切一次就解一次锁造成抖动。
        // 其余阶段一律释放，杜绝「锁在空白页退不出」的死锁。
        if (!(GuardState.phase == Phase.QUIZ && !GuardState.parentMode)) {
            releaseLock()
        }
    }

    /**
     * 统一根据当前阶段决定「是否钉屏」：
     * - 仅【答题阶段 且 非家长模式】才锁定（强管控 + 禁用 Home/Recent）；
     * - 其他任何阶段（守护中 / 家长控制台 / 刚解锁）一律解除锁定，杜绝「锁在空白页退不出」的死锁。
     * 在 onStart / onResume / onNewIntent 都会调用，保证从后台/通知唤醒后状态正确。
     */
    private fun syncLockState() {
        blockBack = (GuardState.phase == Phase.QUIZ && !GuardState.parentMode)
        if (blockBack) {
            // 延后一帧再钉屏，等 Compose 把答题界面渲染好，避免钉在旧的时间界面帧上
            Handler(Looper.getMainLooper()).post { enterLockTask() }
        } else {
            releaseLock()
        }
    }

    /**
     * 进入屏幕固定（Screen Pinning / Lock Task）。
     *
     * 【v1.0.16 关键修复】v1.0.3~v1.0.15 用 `ProcessOnce` 做了「本进程只申请一次」的闸门，
     * 本意是避免系统确认框反复弹出；但每轮答完都会 [releaseLock]，
     * 于是 **从第 2 轮起 startLockTask 再没被调用过**——系统屏幕固定形同虚设，
     * 只剩 0.35 秒的防逃逸回拉，手快的孩子正好在这个窗口里切走清进程。
     *
     * 现在改为**按 [GuardState.quizSerial] 每轮申请一次**：
     *  - 家长正确开启「屏幕固定」且关闭「固定前询问」时，startLockTask 无任何弹窗，每轮静默锁死；
     *  - 若系统仍会询问，孩子最多每轮看到一次确认框，且轮内不会重复弹（同一 serial 只申请一次）；
     *  - Device Owner 模式下完全无弹窗，且孩子无法取消固定。
     */
    private fun enterLockTask() {
        if (isLocked) {
            // 已处于锁定态，只需刷新一次真实状态
            verifyLockState()
            return
        }
        // Activity 不在前台时 startLockTask 必然失败：不占用本轮的申请名额，等 onResume 再来
        if (!GuardState.activityInFront) return
        // 本轮已经申请过（可能失败）→ 不再重复弹系统确认框
        if (lockedQuizSerial == GuardState.quizSerial) return
        lockedQuizSerial = GuardState.quizSerial

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            }
            startLockTask()
        } catch (_: Exception) {
            // 普通模式未开启系统「屏幕固定」时 startLockTask 会失败；继续往下做真实检测
        }
        // startLockTask 是异步的，延后一点再读一次真实状态，避免「以为锁住了其实没锁」
        Handler(Looper.getMainLooper()).postDelayed({
            if (GuardState.phase == Phase.QUIZ && !GuardState.parentMode) {
                verifyLockState()
            }
        }, 400)
    }

    /**
     * 读取系统真实 Lock Task 状态，回写给 UI。
     * 未生效时给出「为什么没锁住 + 该去哪里开」的完整指引（与家长台「🔒 锁死向导」同源）。
     */
    private fun verifyLockState() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val active = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        isLocked = active
        if (active) {
            GuardState.setLockActive(true, null)
        } else {
            GuardState.setLockActive(
                false,
                "系统「屏幕固定」未开启，或刚才的确认框被取消——本轮没有真正锁死，" +
                    "孩子切到最近任务后仍能上滑清掉进程。" +
                    "请到家长台「🔒 锁死向导」开启屏幕固定（并把「取消固定」设为需要解锁密码）；" +
                    "或用电脑执行一次 dpm set-device-owner 设为设备所有者。" +
                    "当前兜底：切走约 0.35 秒自动拉回，上滑清任务立即恢复。"
            )
        }
    }

    /** 解除钉屏（幂等：未锁定则忽略） */
    private fun releaseLock() {
        if (isLocked) {
            try {
                stopLockTask()
            } catch (_: Exception) {
            }
        }
        isLocked = false
        GuardState.setLockActive(false, null)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun registerBackInvoked() {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) {
            // 仅答题阶段消费返回手势，禁止通过返回退出
            if (blockBack) {
                // 什么都不做：消费掉返回手势
            }
        }
    }

    /**
     * 拦截物理/虚拟按键：返回、音量、多任务（仅答题阶段）。
     * 注意：Home 键与多数 ROM 的最近任务键由 SystemUI 直接处理，**根本不派发给应用**，
     * 所以 KEYCODE_APP_SWITCH 这里只是「能挡就挡」；真正兜底有三层：
     *  - GuardState 的 120ms 防逃逸回拉（进程活着时切走立刻拽回）；
     *  - [com.quizguard.app.GuardService.onTaskRemoved] 在「上滑清任务」瞬间秒恢复（进程未死）；
     *  - GuardWatchdog 的 8 秒进程复活 + Device Owner / 系统屏幕固定下的彻底隐藏。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!blockBack) return super.dispatchKeyEvent(event)
        val blocked = setOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_APP_SWITCH
        )
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in blocked) {
            return true // 消费，不传递
        }
        return super.dispatchKeyEvent(event)
    }

    /** 兼容 Android 12 及以下的返回键（部分机型走 onKeyDown） */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (blockBack) return // 答题阶段禁用返回退出
    }
}
