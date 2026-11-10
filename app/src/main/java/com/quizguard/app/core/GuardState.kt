package com.quizguard.app.core

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quizguard.app.MainActivity
import com.quizguard.app.core.ParentPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全局守护状态机（单例）。
 *
 * 与旧版「开机即钉屏、完全锁死」不同，本版本采用：
 *  - 守护在【前台服务 GuardService】中保活，子机可正常使用、自由切换后台；
 *  - 倒计时（间隔、题数、出题范围「学段/年级/册别/科目」均可由家长设置）归零，
 *    自动把答题界面拉到前台并「钉屏」强制答题；
 *  - 本组题全部答对后解除钉屏、退到后台，儿童继续玩本轮时长，循环往复。
 *
 * 所有 UI 状态用 [mutableStateOf] 暴露，Compose 可直接观察并重组。
 * 计时协程跑在单例作用域内，只要进程存活（被前台服务保活）即可在后台持续倒计时。
 */
object GuardState {

    /** 每轮允许玩耍的时长（毫秒），默认 10 分钟，可由家长在控制台修改。
     *  用 mutableStateOf 暴露，家长面板切换选项时 UI 才会重组（否则高亮/文字不刷新）。 */
    var cycleMs by mutableStateOf(10 * 60 * 1000L)
        private set

    /** 每次答题的题目数量（家长可设 1~10，默认 3） */
    var quizCount by mutableStateOf(3)
        private set

    /** 当前出题范围：学段 → 年级 → 册别 → 科目（科目 "all" = 多科混合随机出题） */
    var selection by mutableStateOf(BankSelection())
        private set

    /** 本轮答题是否显示「系统锁定说明」提示条：进程内仅第一次进入答题显示，关闭进程重开才复位 */
    var showIntro by mutableStateOf(true)
        private set

    /** 倒计时刷新步长（毫秒） */
    private const val TICK_MS = 250L

    /**
     * v1.0.13 防逃逸：在 v1.0.11（250ms 轮询/600ms 宽限/700ms 拉回）基础上再次收紧，
     * 配合 [GuardService.onTaskRemoved] 的「上滑清任务秒恢复」，做到孩子切走立刻被拽回。
     *  - 轮询 120ms：更快发现界面离开前台；
     *  - 启动宽限 300ms：刚弹出答题界面时留一点渲染时间，避免误判自拉回；
     *  - 拉回节流 350ms：切走后每 0.35 秒把界面顶回一次，几乎无停留窗口；
     *  - 通知节流 3 秒：全屏「请回来答题」提醒比直接拉回更打扰，单独限频。
     */
    private const val ESCAPE_POLL_MS = 120L
    private const val ESCAPE_GRACE_MS = 300L
    private const val PULLBACK_THROTTLE_MS = 350L
    /** 全屏通知比直接拉回更打扰，单独用 3 秒节流，避免频繁响铃刷屏 */
    private const val PULLBACK_NOTIFY_MS = 3_000L

    /** 守护是否已开启（控制 UI 显示开关页 / 守护页） */
    var running by mutableStateOf(false)
        private set

    /** 当前阶段：守护中 / 答题中 */
    var phase by mutableStateOf(Phase.IDLE)
        private set

    /** 本轮剩余毫秒 */
    var remainingMs by mutableStateOf(cycleMs)
        private set

    /** 当前这一组题目 */
    var questions by mutableStateOf<List<Question>>(emptyList())
        private set

    /** 当前题序号 */
    var current by mutableStateOf(0)
        private set

    /**
     * v1.0.16：每轮答题的递增序号（[fireQuiz] 时 +1）。
     *
     * 用于让 Activity 判断「本轮是否已经向系统申请过屏幕固定」。
     * v1.0.3~v1.0.15 用 [ProcessOnce] 做了「每进程只申请一次」的闸门，
     * 但每轮答完都会 stopLockTask，于是**从第 2 轮起系统锁定再没生效过**，
     * 只剩 0.35 秒的防逃逸回拉——手快的孩子就能在这窗口里切走并清进程。
     * 现在改为「每轮答题重新申请一次」，屏幕固定正确开启时每轮都真正锁死。
     */
    var quizSerial by mutableStateOf(0)
        private set

    /** 当前选中的选项下标 */
    var selected by mutableStateOf(-1)
        private set

    /** 答题反馈 */
    var feedback by mutableStateOf(Feedback.NONE)
        private set

    /** v1.0.5 家长设置：答错后的处理策略（持久化，下一轮答题生效） */
    var wrongPolicy by mutableStateOf(WrongPolicy.RETRY_WRONG)
        private set

    /** v1.0.6：每轮最多插入几道到期错题（0~3，0 = 关闭错题复习） */
    var reviewPerRound by mutableStateOf(2)
        private set

    /** v1.0.6：错题「额外加练」——true 时不占用本轮题数，叠加在 quizCount 之上 */
    var reviewExtra by mutableStateOf(false)
        private set

    /** 本轮已结算过错题本的题目标识：避免本轮重做把艾宾浩斯档位反复推进 / 回退 */
    private val recordedThisRound = mutableSetOf<String>()

    /** 答题页顶部的轮次提示（进入「错题重做轮」时显示） */
    var roundLabel by mutableStateOf<String?>(null)
        private set

    /** 待重做的错题；本组走完后排队重做，直到全部答对才放行 */
    private var wrongQueue: MutableList<Question> = mutableListOf()

    /** 本轮答题实际采用的策略快照：家长中途改设置不影响当前这组题 */
    private var roundPolicy: WrongPolicy = WrongPolicy.RETRY_WRONG

    /** 答对后自动跳题的短暂停留，给一点正反馈但不打断节奏 */
    private const val CORRECT_DELAY_MS = 600L

    /** 临时提示（答对解锁 / 答错重做） */
    var notice by mutableStateOf<String?>(null)
        private set

    /** 家长已通过密码验证，进入控制台（此时暂停计时、不钉屏） */
    var parentMode by mutableStateOf(false)
        private set

    /** 系统屏幕固定（Lock Task）是否【真正生效】。普通安装未开系统「屏幕固定」时为 false */
    var lockActive by mutableStateOf(false)
        private set

    /** 锁定状态说明（未生效时给家长看的原因与开启指引） */
    var lockHint by mutableStateOf<String?>(null)
        private set

    /** 答题界面当前是否处于前台（由 MainActivity 的 onResume/onPause 维护，跨线程读） */
    @Volatile
    var activityInFront = false

    /** 由 Activity 回写真实钉屏状态 */
    fun setLockActive(active: Boolean, hint: String? = null) {
        lockActive = active
        lockHint = hint
    }

    /**
     * 答题全部答对后的回调：由 Activity 注册，执行
     * 「解除钉屏 + 退到后台」，让儿童回到刚才使用的 App。
     */
    var onUnlocked: (() -> Unit)? = null

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    /** 答题期防逃逸守护协程；切到其它 App 时把答题界面拉回来 */
    private var escapeJob: Job? = null
    private var lastPullBackMs = 0L
    private var lastPullBackNotifyMs = 0L
    private var quizStartedMs = 0L

    /**
     * 由 Application 注入全局 Context（用于后台启动 Activity），恢复家长设置。
     * 必须在 [QuestionBank.load] 之后调用：这里要用 Catalog 把持久化的选择修正为有效组合。
     */
    fun attach(context: Context) {
        appContext = context.applicationContext
        cycleMs = ParentPrefs.getCycleMs()
        quizCount = ParentPrefs.getQuizCount()
        wrongPolicy = ParentPrefs.getWrongPolicy()
        reviewPerRound = ParentPrefs.getReviewPerRound()
        reviewExtra = ParentPrefs.getReviewExtra()
        val normalized = QuestionBank.normalize(ParentPrefs.getSelection())
        selection = normalized
        ParentPrefs.saveSelection(normalized)
        QuestionBank.prepare(normalized)
    }

    /** 用户开启守护：读取家长设置 + 预加载题库 + 启动计时（前台保活由 GuardService 负责） */
    fun startGuard() {
        if (running) return
        running = true
        cycleMs = ParentPrefs.getCycleMs()
        quizCount = ParentPrefs.getQuizCount()
        wrongPolicy = ParentPrefs.getWrongPolicy()
        reviewPerRound = ParentPrefs.getReviewPerRound()
        reviewExtra = ParentPrefs.getReviewExtra()
        selection = QuestionBank.normalize(ParentPrefs.getSelection())
        QuestionBank.prepare(selection)
        // v1.0.11：持久化守护状态并挂上看门狗，进程被最近任务清掉后可自动复活
        ParentPrefs.setGuardEnabled(true)
        GuardWatchdog.schedule(appContext)
        Notify.showRunning(appContext, (cycleMs / 60000).toInt())
        startCycle()
    }

    /**
     * v1.0.11 看门狗恢复入口：进程曾因「最近任务 → 清除」被杀掉。
     * 若被杀时正处于答题阶段，立刻重新出题，而不是重新送一整轮玩耍时长；
     * 否则按正常流程重新计时。两种情况下都不会让孩子靠杀进程白赚时间。
     */
    fun resumeAfterKill() {
        if (running) return
        running = true
        cycleMs = ParentPrefs.getCycleMs()
        quizCount = ParentPrefs.getQuizCount()
        wrongPolicy = ParentPrefs.getWrongPolicy()
        reviewPerRound = ParentPrefs.getReviewPerRound()
        reviewExtra = ParentPrefs.getReviewExtra()
        selection = QuestionBank.normalize(ParentPrefs.getSelection())
        QuestionBank.prepare(selection)
        ParentPrefs.setGuardEnabled(true)
        GuardWatchdog.schedule(appContext)
        if (ParentPrefs.isQuizActive()) fireQuiz() else startCycle()
    }

    /** 用户停止守护：取消计时、清空状态、移除通知 */
    fun stopGuard() {
        ticker?.cancel()
        ticker = null
        stopEscapeGuard()
        // 家长主动停止后看门狗必须彻底停掉，否则会出现「关不掉」的复活
        ParentPrefs.setGuardEnabled(false)
        ParentPrefs.setQuizActive(false)
        GuardWatchdog.cancel(appContext)
        Notify.cancelPullBack(appContext)
        running = false
        phase = Phase.IDLE
        remainingMs = cycleMs
        questions = emptyList()
        current = 0
        selected = -1
        feedback = Feedback.NONE
        notice = null
        wrongQueue.clear()
        roundLabel = null
        parentMode = false
        Notify.cancel(appContext)
    }

    /** 开始新一轮守护倒计时（时长取当前 cycleMs，可由家长修改） */
    fun startCycle() {
        ticker?.cancel()
        stopEscapeGuard()
        // 回到「守护中」：清掉答题中标记，进程若被杀，重启后重新开始计时即可
        ParentPrefs.setQuizActive(false)
        Notify.cancelPullBack(appContext)
        phase = Phase.IDLE
        remainingMs = cycleMs
        questions = emptyList()
        current = 0
        selected = -1
        feedback = Feedback.NONE
        notice = null
        wrongQueue.clear()
        roundLabel = null
        ticker = scope.launch {
            while (remainingMs > 0) {
                delay(TICK_MS)
                remainingMs = (remainingMs - TICK_MS).coerceAtLeast(0)
            }
            triggerQuiz()
        }
    }

    /** 时间到：抽 3 道混合题并自动弹出答题界面 */
    private fun triggerQuiz() {
        fireQuiz()
    }

    /**
     * 真正弹出答题界面：抽题 + 标记 QUIZ 阶段 + 拉起 Activity + 发全屏通知。
     * 全屏 intent 通知（闹钟式 heads-up）是核心兜底：即使 MIUI 禁止「后台弹出界面」，
     * 到点也会以高优先级把答题 Activity 顶到最前，避免「锁屏却不弹题」的死锁。
     */
    private fun fireQuiz() {
        // v1.0.6 组卷：先按艾宾浩斯曲线取「已到期」的错题，再用新题补满本轮题数。
        // 错题额外加练关闭时最多占 quizCount 道（默认 2）；开启时叠加在 quizCount 之上。
        val maxReview = if (reviewExtra) reviewPerRound else minOf(reviewPerRound, quizCount)
        val reviews = WrongBook.due(maxReview)
        val reviewKeys = reviews.mapNotNull { it.reviewKey }.toSet()
        val freshCount = if (reviewExtra) quizCount else (quizCount - reviews.size).coerceAtLeast(0)
        val fresh = if (freshCount > 0) {
            QuestionBank.randomN(freshCount, selection)
                .filter { WrongBook.keyOf(it) !in reviewKeys }
        } else emptyList()
        questions = (fresh + reviews).shuffled().ifEmpty { QuestionBank.randomN(1, selection) }

        // 答题页的「系统锁定说明」提示条 / 系统确认框：每次启动 App 只出现一次，
        // 只有关闭进程后重新打开才会再显示（ProcessOnce 为纯内存标志，不持久化）。
        showIntro = ProcessOnce.once("quiz_intro")
        wrongQueue.clear()
        recordedThisRound.clear()
        roundLabel = null
        roundPolicy = wrongPolicy
        current = 0
        selected = -1
        feedback = Feedback.NONE
        notice = null
        phase = Phase.QUIZ
        // v1.0.16：本轮序号 +1，Activity 据此重新申请一次系统屏幕固定
        quizSerial++
        quizStartedMs = System.currentTimeMillis()
        // 答题中被杀进程 → 重启后必须立刻再弹题，不能白送一轮玩耍时间
        ParentPrefs.setQuizActive(true)

        // 1) 尝试直接拉起（有前台服务在跑，Android 允许后台启动）
        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        try {
            appContext.startActivity(intent)
        } catch (_: Exception) {
            // 个别 ROM 限制后台启动 Activity，靠下面的全屏通知兜底
        }
        // 2) 全屏 intent 通知（heads-up），点击 / 自动展开即进入答题界面（双保险）
        Notify.showQuiz(appContext, questions.size)
        // 3) 防逃逸守护：答题期间一旦被切到其它 App，自动把答题界面拉回来
        startEscapeGuard()
    }

    /** 把答题界面重新拉回前台（切走时调用） */
    private fun pullBack(notify: Boolean) {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        try {
            appContext.startActivity(intent)
        } catch (_: Exception) {
        }
        // 独立通知 ID + 全屏 Intent，避免覆盖常驻通知，且能再次触发 heads-up。
        // 直接拉回每 0.7 秒一次即可，全屏通知单独 5 秒节流，避免反复响铃刷屏。
        if (notify) Notify.showPullBack(appContext, questions.size.coerceAtLeast(1))
    }

    /**
     * 答题期防逃逸守护：只要处于答题阶段且非家长模式，就周期性检查界面是否还在前台。
     * 一旦被切到后台（按 Home / 多任务切到别的 App），立刻把答题界面拉回来。
     * 这是不依赖系统「屏幕固定」也能做到的事实锁定：孩子切走后马上被拽回来，无法使用其它程序。
     */
    private fun startEscapeGuard() {
        escapeJob?.cancel()
        lastPullBackMs = 0L
        lastPullBackNotifyMs = 0L
        escapeJob = scope.launch {
            while (phase == Phase.QUIZ && !parentMode) {
                delay(ESCAPE_POLL_MS)
                if (phase != Phase.QUIZ || parentMode) break
                // 给 Activity 一点启动时间，避免刚弹出就误判（旧版 2 秒太长）
                if (System.currentTimeMillis() - quizStartedMs < ESCAPE_GRACE_MS) continue
                if (!activityInFront) {
                    val now = System.currentTimeMillis()
                    if (now - lastPullBackMs >= PULLBACK_THROTTLE_MS) {
                        lastPullBackMs = now
                        val needNotify = now - lastPullBackNotifyMs >= PULLBACK_NOTIFY_MS
                        if (needNotify) lastPullBackNotifyMs = now
                        pullBack(needNotify)
                    }
                }
            }
        }
    }

    private fun stopEscapeGuard() {
        escapeJob?.cancel()
        escapeJob = null
    }

    /**
     * 家长手动触发答题（用于测试，不必等满 10 分钟）。
     * 仅当守护已开启、且当前不在答题阶段时有效。
     * 若在家长面板内触发，先退出家长模式，否则答题界面会被面板盖住、永远出不来。
     */
    fun testFire() {
        if (!running || phase == Phase.QUIZ) return
        parentMode = false
        fireQuiz()
    }

    /** 仅记录选中项（兼容旧调用）；v1.0.5 起点选项即判定，无需再点「提交」 */
    fun select(option: Int) {
        if (phase != Phase.QUIZ || parentMode || feedback != Feedback.NONE) return
        selected = option
    }

    /**
     * v1.0.5 核心交互：点选项即作答。
     * - 答对 → 给一点绿色反馈后【自动跳过】到下一题，最后一道直接放行；
     * - 答错 → 停在原题，展示正确答案与解题思路，必须手动点 [continueAfterWrong] 才继续。
     * 目的：正确题不打断节奏，错误题留足时间理解解题思路。
     */
    fun answer(index: Int) {
        if (phase != Phase.QUIZ || parentMode || feedback != Feedback.NONE) return
        val q = questions.getOrNull(current) ?: return
        if (index < 0 || index >= q.options.size) return
        selected = index
        val correct = index == q.answerIndex

        // v1.0.6 错题本结算：本轮同一道题只记一次，避免本轮「错题重做」把复习曲线反复推进/回退。
        // - 复习题：答对推进到下一档、答错归零重刷；
        // - 普通新题答错：收进错题本，从第 1 档（5 分钟后）开始刷。
        val reviewKey = q.reviewKey
        if (reviewKey != null || !correct) {
            val bookKey = reviewKey ?: WrongBook.keyOf(q)
            if (recordedThisRound.add(bookKey)) {
                if (reviewKey != null) WrongBook.onResult(reviewKey, correct)
                else WrongBook.add(q)
            }
        }

        if (correct) {
            feedback = Feedback.CORRECT
            scope.launch {
                delay(CORRECT_DELAY_MS)
                // 停留期间可能已被家长接管 / 停止守护，再校验一次避免误跳
                if (phase != Phase.QUIZ || parentMode || feedback != Feedback.CORRECT) return@launch
                advance()
            }
        } else {
            feedback = Feedback.WRONG
            // 「错题重做」把错题排到队尾；「整组重来」由 continueAfterWrong 直接重抽一整组
            if (roundPolicy == WrongPolicy.RETRY_WRONG) wrongQueue.add(q)
        }
    }

    /** 兼容旧入口：等价于对当前选中项点一下 */
    fun submit() {
        val idx = selected
        if (idx >= 0) answer(idx)
    }

    /**
     * 答错后由孩子【手动】触发的下一步。这里不存在任何自动跳转，
     * 解析页会一直停留到孩子自己看懂并点击按钮为止。
     */
    fun continueAfterWrong() {
        if (phase != Phase.QUIZ || parentMode || feedback != Feedback.WRONG) return
        if (roundPolicy == WrongPolicy.RESTART_GROUP) {
            // 旧行为：整组重新抽题，从第 1 题重做
            questions = QuestionBank.randomN(quizCount, selection)
            wrongQueue.clear()
            recordedThisRound.clear()
            roundLabel = "有答错的，本组重新作答"
            current = 0
            selected = -1
            feedback = Feedback.NONE
            return
        }
        advance()
    }

    /** 前进一题；本组走完还有错题 → 进入「错题重做轮」，否则视为全部答对并放行 */
    private fun advance() {
        val next = current + 1
        if (next < questions.size) {
            current = next
            selected = -1
            feedback = Feedback.NONE
            return
        }
        if (wrongQueue.isNotEmpty()) {
            questions = wrongQueue.shuffled()
            wrongQueue = mutableListOf()
            current = 0
            selected = -1
            feedback = Feedback.NONE
            roundLabel = "错题重做：把刚才答错的题再答对一次，就能继续玩"
            return
        }
        // 全部答对 → 解除钉屏、退到后台，重新计时（startCycle 会清空本轮状态）
        onUnlocked?.invoke()
        startCycle()
        // 注意：必须放在 startCycle() 之后，否则提示会被 startCycle 里的 notice = null 清掉
        notice = "全部答对！可以继续玩 ${cycleMs / 60000} 分钟"
        // 把通知从「该答题啦」还原为「守护中」，避免答题后一直停在提醒通知
        Notify.showRunning(appContext, (cycleMs / 60000).toInt())
    }

    /** UI 层读取倒计时文本用 */
    fun formatRemaining(): String {
        val totalSec = (remainingMs / 1000).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    /** 家长密码验证通过，进入控制台（暂停计时、停止防逃逸、解锁） */
    fun enterParentMode() {
        ticker?.cancel()
        stopEscapeGuard()
        Notify.cancelPullBack(appContext)
        parentMode = true
    }

    /** 家长离开控制台，恢复守护（重新计时） */
    fun exitParentMode() {
        parentMode = false
        startCycle()
    }

    /**
     * 家长修改每轮分钟数：持久化保存。
     * - 若当前正守护中、且不在答题阶段、且不在家长面板内：立即按新时长重启本轮倒计时；
     * - 若在家长面板内改时长：仅记录，等家长退出面板（exitParentMode）时再按新时长计时，
     *   避免「面板里计时仍在走、到点把答题界面藏到面板后」的混乱。
     */
    fun setCycle(minutes: Int) {
        val m = minutes.coerceAtLeast(1)
        cycleMs = m * 60_000L
        ParentPrefs.setCycleMin(m)
        if (running) Notify.showRunning(appContext, m)
        if (running && phase == Phase.IDLE && !parentMode) {
            startCycle()
        }
    }

    // ------------------------------------------------------------------
    // 题库范围四级联动（学段 → 年级 → 册别 → 科目）
    // 每次切换都经 normalize 修正下级，确保组合始终有题可出；随后持久化 + 后台预加载。
    // ------------------------------------------------------------------

    fun setStage(stage: Stage) = updateSelection(selection.copy(stage = stage))

    fun setGrade(id: String) = updateSelection(selection.copy(gradeId = id))

    fun setTerm(term: Term) = updateSelection(selection.copy(term = term))

    fun setSubject(id: String) = updateSelection(selection.copy(subjectId = id))

    private fun updateSelection(next: BankSelection) {
        val fixed = QuestionBank.normalize(next)
        selection = fixed
        ParentPrefs.saveSelection(fixed)
        QuestionBank.prepare(fixed)
        // 家长台里改设置不重启计时；只在真正下一轮出题时读取最新 selection
        if (running) {
            Notify.showRunning(appContext, (cycleMs / 60000).toInt())
        }
    }

    /** 家长设置每次答题数量：持久化保存，下一轮答题生效（不改动当前这组题，避免题数突变）
     *  注意：不能叫 setQuizCount —— 会与 quizCount 的私有 JVM setter 签名冲突。 */
    fun changeQuizCount(n: Int) {
        val v = n.coerceIn(1, 10)
        quizCount = v
        ParentPrefs.setQuizCount(v)
    }

    /**
     * 家长设置答错处理策略：持久化保存，【下一轮答题】生效。
     * 当前这组题使用 fireQuiz() 时的 roundPolicy 快照，中途改设置不会打乱正在进行的一轮。
     */
    fun changeWrongPolicy(policy: WrongPolicy) {
        wrongPolicy = policy
        ParentPrefs.setWrongPolicy(policy)
    }

    /** v1.0.6：每轮插入几道到期错题；0 = 暂停错题复习（错题仍会继续收集） */
    fun changeReviewPerRound(n: Int) {
        val v = n.coerceIn(0, 3)
        reviewPerRound = v
        ParentPrefs.setReviewPerRound(v)
    }

    /** v1.0.6：错题是否额外加练（不占用本轮题数） */
    fun changeReviewExtra(extra: Boolean) {
        reviewExtra = extra
        ParentPrefs.setReviewExtra(extra)
    }
}
