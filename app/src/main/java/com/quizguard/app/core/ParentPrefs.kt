package com.quizguard.app.core

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * 家长解锁密码管理（本地存储）。
 *
 * - 默认密码 "1234"，首次启动写入；
 * - 仅存 SHA-256 哈希，不存明文；
 * - 连续验证失败 MAX_FAIL 次后锁定 LOCK_MS 毫秒，防止儿童暴力试密码。
 */
object ParentPrefs {

    private const val NAME = "quizguard_parent"
    private const val KEY_HASH = "pwd_hash"
    private const val KEY_CYCLE = "cycle_minutes"
    private const val KEY_QUIZ_COUNT = "quiz_count"
    private const val KEY_WRONG_POLICY = "wrong_policy"
    private const val KEY_REVIEW_PER_ROUND = "review_per_round"
    private const val KEY_REVIEW_EXTRA = "review_extra"
    private const val KEY_STAGE = "bank_stage"
    private const val KEY_GRADE = "bank_grade"
    private const val KEY_TERM = "bank_term"
    private const val KEY_SUBJECT = "bank_subject"
    // v1.0.11 看门狗：进程被「最近任务 → 清除」杀掉后，靠持久化状态自动复活守护
    private const val KEY_GUARD_ENABLED = "guard_enabled"
    private const val KEY_QUIZ_ACTIVE = "quiz_active"
    private const val DEFAULT = "1234"
    private const val DEFAULT_CYCLE = 10
    private const val DEFAULT_QUIZ_COUNT = 3
    private const val MAX_FAIL = 5
    private const val LOCK_MS = 60_000L

    private lateinit var sp: SharedPreferences
    private var failCount = 0
    private var lockUntil = 0L

    fun init(context: Context) {
        sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (!sp.contains(KEY_HASH)) {
            sp.edit().putString(KEY_HASH, hash(DEFAULT)).apply()
        }
    }

    /** 每轮允许玩耍的分钟数（默认 10） */
    fun getCycleMin(): Int = sp.getInt(KEY_CYCLE, DEFAULT_CYCLE).coerceAtLeast(1)

    /** 每轮允许玩耍的毫秒数 */
    fun getCycleMs(): Long = getCycleMin() * 60_000L

    /** 设置每轮分钟数并持久化 */
    fun setCycleMin(min: Int) {
        sp.edit().putInt(KEY_CYCLE, min.coerceAtLeast(1)).apply()
    }

    /**
     * 当前题库范围（学段 → 年级 → 册别 → 科目）。
     * 默认指向「小学 / 二年级 / 上册 / 数学」，即内置的二年级上册人教版数学题库。
     */
    fun getSelection(): BankSelection = BankSelection(
        stage = Stage.from(sp.getString(KEY_STAGE, Stage.PRIMARY.id)),
        gradeId = sp.getString(KEY_GRADE, BankSelection.DEFAULT_GRADE)
            ?: BankSelection.DEFAULT_GRADE,
        term = Term.from(sp.getString(KEY_TERM, Term.SHANG.id)),
        subjectId = sp.getString(KEY_SUBJECT, Subject.MATH.id) ?: Subject.MATH.id
    )

    /** 持久化题库范围（调用方负责先经 QuestionBank.normalize 修正） */
    fun saveSelection(sel: BankSelection) {
        sp.edit()
            .putString(KEY_STAGE, sel.stage.id)
            .putString(KEY_GRADE, sel.gradeId)
            .putString(KEY_TERM, sel.term.id)
            .putString(KEY_SUBJECT, sel.subjectId)
            .apply()
    }

    /** 每次答题的题目数量（默认 3，允许 1~10） */
    fun getQuizCount(): Int = sp.getInt(KEY_QUIZ_COUNT, DEFAULT_QUIZ_COUNT).coerceIn(1, 10)

    /** 设置每次答题数量并持久化 */
    fun setQuizCount(n: Int) {
        sp.edit().putInt(KEY_QUIZ_COUNT, n.coerceIn(1, 10)).apply()
    }

    /** 答错处理策略（默认「错题重做」） */
    fun getWrongPolicy(): WrongPolicy = WrongPolicy.from(sp.getString(KEY_WRONG_POLICY, null))

    /** 设置答错处理策略并持久化；下一轮答题生效，不打断当前这组题 */
    fun setWrongPolicy(policy: WrongPolicy) {
        sp.edit().putString(KEY_WRONG_POLICY, policy.id).apply()
    }

    /** 每轮最多插入几道到期错题（0 = 关闭错题复习，默认 2，范围 0~3） */
    fun getReviewPerRound(): Int = sp.getInt(KEY_REVIEW_PER_ROUND, 2).coerceIn(0, 3)

    fun setReviewPerRound(n: Int) {
        sp.edit().putInt(KEY_REVIEW_PER_ROUND, n.coerceIn(0, 3)).apply()
    }

    /** 错题是否「额外加练」：true = 不占用本轮题数，叠加在家长设定的题数之上 */
    fun getReviewExtra(): Boolean = sp.getBoolean(KEY_REVIEW_EXTRA, false)

    fun setReviewExtra(extra: Boolean) {
        sp.edit().putBoolean(KEY_REVIEW_EXTRA, extra).apply()
    }

    /** 当前是否处于锁定冷却中 */
    fun isLocked(): Boolean = System.currentTimeMillis() < lockUntil

    /** 锁定剩余秒数（0 表示未锁定） */
    fun lockSecondsLeft(): Long =
        ((lockUntil - System.currentTimeMillis()).coerceAtLeast(0) / 1000).toLong()

    /** 守护是否处于「家长已开启」状态；看门狗只在 true 时复活，家长停止后绝不再拉起 */
    fun isGuardEnabled(): Boolean = sp.getBoolean(KEY_GUARD_ENABLED, false)

    fun setGuardEnabled(on: Boolean) {
        sp.edit().putBoolean(KEY_GUARD_ENABLED, on).apply()
    }

    /** 进程被杀时是否正处于答题阶段；true 表示复活后必须立刻重新出题 */
    fun isQuizActive(): Boolean = sp.getBoolean(KEY_QUIZ_ACTIVE, false)

    fun setQuizActive(on: Boolean) {
        sp.edit().putBoolean(KEY_QUIZ_ACTIVE, on).apply()
    }

    /** 还可尝试次数（锁定中为 0） */
    fun attemptsLeft(): Int = if (isLocked()) 0 else (MAX_FAIL - failCount).coerceAtLeast(0)

    /** 校验密码；返回 true 表示通过 */
    fun verify(input: String): Boolean {
        if (isLocked()) return false
        val stored = sp.getString(KEY_HASH, null)
        val ok = if (stored == null) input == DEFAULT else stored == hash(input)
        if (ok) {
            failCount = 0
            lockUntil = 0
        } else {
            failCount++
            if (failCount >= MAX_FAIL) lockUntil = System.currentTimeMillis() + LOCK_MS
        }
        return ok
    }

    /** 修改密码：先校验旧密码，再写入新密码哈希 */
    fun change(oldPwd: String, newPwd: String): Boolean {
        if (!verify(oldPwd)) return false
        sp.edit().putString(KEY_HASH, hash(newPwd)).apply()
        return true
    }

    private fun hash(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
