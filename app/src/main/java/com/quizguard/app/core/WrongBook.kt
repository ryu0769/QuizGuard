package com.quizguard.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 错题本（v1.0.6）—— 按「艾宾浩斯遗忘曲线」安排复习。
 *
 * ## 复习节点（经典 9 档）
 * 5 分钟 → 30 分钟 → 12 小时 → 1 天 → 2 天 → 4 天 → 7 天 → 15 天 → 30 天
 *
 * - 孩子答错一道**新题**：立刻收进错题本，档位 0，5 分钟后（通常就是下一轮答题）到期；
 * - 到期错题会**优先混入**每轮题目（家长可设每轮 0~3 道，默认 2 道）；
 * - 复习题**答对**：档位 +1，按下一档间隔安排下次复习；连续 9 档全对 → 标记「已掌握」，不再出现；
 * - 复习题**答错**：档位归零、错误次数 +1，5 分钟后重新刷 → 错得越多刷得越勤；
 * - 同一道题本轮内只结算一次，避免本轮「错题重做」把复习曲线打乱。
 *
 * 数据存在独立 SharedPreferences（JSON 快照），不依赖网络、不引入 Room/Gson；
 * 题目内容整份存下来，所以家长切换年级/科目后，旧错题依然能被复习到。
 */
data class WrongEntry(
    val key: String,
    val question: Question,
    /** 累计答错次数 */
    val wrongCount: Int,
    /** 累计复习答对次数 */
    val reviewCount: Int,
    /** 当前档位 0..INTERVALS.size；等于 size 表示已掌握 */
    val stage: Int,
    /** 下次复习时间戳（毫秒）；已掌握为 Long.MAX_VALUE */
    val dueAt: Long,
    /** 最近一次结算时间 */
    val lastAt: Long,
    val mastered: Boolean
)

object WrongBook {

    private const val SP_NAME = "quizguard_wrongbook"
    private const val KEY_DATA = "entries_json"

    /** 防止长期使用后错题本无限膨胀（超出时优先淘汰已掌握的旧题） */
    private const val MAX_ENTRIES = 500

    /** 艾宾浩斯复习间隔，单位毫秒 */
    val INTERVALS: List<Long> = listOf(5L, 30L, 720L, 1440L, 2880L, 5760L, 10080L, 21600L, 43200L)
        .map { it * 60_000L }

    /** 给家长看的间隔说明（与 [INTERVALS] 一一对应） */
    val INTERVAL_LABELS: List<String> = listOf(
        "5 分钟", "30 分钟", "12 小时", "1 天", "2 天", "4 天", "7 天", "15 天", "30 天"
    )

    /** 数据变更计数：Compose 读取它即可在错题变化后刷新家长台统计 */
    var version by mutableStateOf(0)
        private set

    private lateinit var sp: SharedPreferences
    private val entries = LinkedHashMap<String, WrongEntry>()

    fun init(context: Context) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun now(): Long = System.currentTimeMillis()

    /** 题目指纹：科目 + 题干 + 选项做 SHA-256，跨题库文件、跨年级稳定命中同一道题 */
    fun keyOf(q: Question): String {
        val raw = q.subjectLabel + "|" + q.text + "|" + q.options.joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(20)
    }

    /** 答错一道非复习题：新建或重置条目，回到第 1 档（5 分钟后重刷） */
    fun add(q: Question) {
        val key = keyOf(q)
        val old = entries[key]
        val t = now()
        entries[key] = WrongEntry(
            key = key,
            question = snapshot(q),
            wrongCount = (old?.wrongCount ?: 0) + 1,
            reviewCount = 0,
            stage = 0,
            dueAt = t + INTERVALS[0],
            lastAt = t,
            mastered = false
        )
        trim()
        save()
    }

    /** 复习题结算：答对推进一档，答错归零重刷 */
    fun onResult(key: String, correct: Boolean) {
        val e = entries[key] ?: return
        val t = now()
        entries[key] = if (correct) {
            val next = e.stage + 1
            if (next >= INTERVALS.size) {
                e.copy(
                    stage = next,
                    reviewCount = e.reviewCount + 1,
                    dueAt = Long.MAX_VALUE,
                    lastAt = t,
                    mastered = true
                )
            } else {
                e.copy(
                    stage = next,
                    reviewCount = e.reviewCount + 1,
                    dueAt = t + INTERVALS[next],
                    lastAt = t
                )
            }
        } else {
            e.copy(
                wrongCount = e.wrongCount + 1,
                stage = 0,
                dueAt = t + INTERVALS[0],
                lastAt = t,
                mastered = false
            )
        }
        save()
    }

    /**
     * 取「已到期」的错题，最多 limit 道。
     * 排序：越早到期越优先；同时间则错得越多的越优先（这就是「多刷」）。
     */
    fun due(limit: Int, now: Long = now()): List<Question> {
        if (limit <= 0) return emptyList()
        return entries.values.asSequence()
            .filter { !it.mastered && it.dueAt <= now }
            .sortedWith(compareBy<WrongEntry> { it.dueAt }.thenByDescending { it.wrongCount })
            .take(limit)
            .map { it.question.copy(reviewKey = it.key, reviewStage = it.stage) }
            .toList()
    }

    data class Stats(val total: Int, val due: Int, val mastered: Int, val nextDueAt: Long?)

    fun stats(now: Long = now()): Stats = Stats(
        total = entries.size,
        due = entries.values.count { !it.mastered && it.dueAt <= now },
        mastered = entries.values.count { it.mastered },
        nextDueAt = entries.values.filter { !it.mastered }.minOfOrNull { it.dueAt }
    )

    /** 家长台列表：未掌握的按到期时间在前，已掌握的沉底 */
    fun list(): List<WrongEntry> = entries.values.sortedWith(
        compareBy<WrongEntry> { it.mastered }.thenBy { it.dueAt }
    )

    fun clear() {
        entries.clear()
        save()
    }

    fun remove(key: String) {
        if (entries.remove(key) != null) save()
    }

    /** 答题页反馈里显示的「下次复习」提示 */
    fun nextHint(key: String): String? {
        val e = entries[key] ?: return null
        if (e.mastered) return "🎉 连续 ${INTERVALS.size} 次答对，已掌握，不再出现"
        val delta = e.dueAt - now()
        return if (delta <= 0) "⏰ 已到复习时间，下一轮答题会出现"
        else "下次复习：约 ${formatDelta(delta)}后（第 ${e.stage + 1}/${INTERVALS.size} 档）"
    }

    fun formatDelta(ms: Long): String {
        val m = ms.coerceAtLeast(0) / 60_000L
        return when {
            m < 1 -> "不到 1 分钟"
            m < 60 -> "$m 分钟"
            m < 1440 -> "${m / 60} 小时"
            else -> "${m / 1440} 天"
        }
    }

    fun intervalLabel(stage: Int): String = INTERVAL_LABELS.getOrElse(stage) { "已掌握" }

    /** 去掉运行时字段，只留题目快照，避免把本轮复习标记写进错题本 */
    private fun snapshot(q: Question): Question = q.copy(reviewKey = null, reviewStage = -1)

    private fun trim() {
        if (entries.size <= MAX_ENTRIES) return
        val drop = entries.values.sortedWith(
            compareBy<WrongEntry> { !it.mastered }.thenBy { it.lastAt }
        ).firstOrNull()
        if (drop != null) entries.remove(drop.key)
    }

    // ------------------------------------------------------------------
    // 本地持久化（org.json，Android 内置，无需额外依赖）
    // ------------------------------------------------------------------

    private fun load() {
        entries.clear()
        val raw = sp.getString(KEY_DATA, null) ?: return
        try {
            val arr = JSONObject(raw).optJSONArray("entries") ?: return
            for (i in 0 until arr.length()) {
                val e = fromJson(arr.getJSONObject(i)) ?: continue
                entries[e.key] = e
            }
        } catch (_: Exception) {
            // 数据损坏时宁可空本，也不让 App 起不来
        }
    }

    private fun save() {
        val arr = JSONArray()
        entries.values.forEach { arr.put(toJson(it)) }
        val raw = JSONObject().put("v", 1).put("entries", arr).toString()
        sp.edit().putString(KEY_DATA, raw).apply()
        version++
    }

    private fun toJson(e: WrongEntry): JSONObject = JSONObject().apply {
        put("key", e.key)
        put("question", JSONObject().apply {
            put("id", e.question.id)
            put("subject", e.question.subject.id)
            put("subjectLabel", e.question.subjectLabel)
            put("text", e.question.text)
            val opts = JSONArray()
            e.question.options.forEach { opts.put(it) }
            put("options", opts)
            put("answerIndex", e.question.answerIndex)
            put("explanation", e.question.explanation)
        })
        put("wrongCount", e.wrongCount)
        put("reviewCount", e.reviewCount)
        put("stage", e.stage)
        put("dueAt", e.dueAt)
        put("lastAt", e.lastAt)
        put("mastered", e.mastered)
    }

    private fun fromJson(o: JSONObject): WrongEntry? = try {
        val qo = o.getJSONObject("question")
        val optArr = qo.getJSONArray("options")
        val options = List(optArr.length()) { optArr.getString(it) }
        val subject = Subject.from(qo.optString("subject"))
        val q = Question(
            id = qo.optString("id"),
            subject = subject,
            text = qo.optString("text"),
            options = options,
            answerIndex = qo.optInt("answerIndex", 0),
            explanation = qo.optString("explanation", ""),
            subjectLabel = qo.optString("subjectLabel", subject.label)
        )
        WrongEntry(
            key = o.getString("key"),
            question = q,
            wrongCount = o.optInt("wrongCount", 1),
            reviewCount = o.optInt("reviewCount", 0),
            stage = o.optInt("stage", 0),
            dueAt = o.optLong("dueAt", 0L),
            lastAt = o.optLong("lastAt", 0L),
            mastered = o.optBoolean("mastered", false)
        )
    } catch (_: Exception) {
        null
    }
}
