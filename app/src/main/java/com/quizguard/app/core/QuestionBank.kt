package com.quizguard.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * 题库引擎 v2（1.0.4 起）：四级目录 Catalog —— 学段 → 年级 → 册别 → 科目。
 *
 * ## 推荐目录（开源贡献者直接丢文件，零改代码）
 * ```
 * assets/banks/
 * ├── primary/                       ← 学段：primary 小学 / junior 初中 / senior 高中
 * │   └── grade2/                    ← 年级 id（grade1..grade12 或自定义）
 * │       ├── shang/                 ← 册别：shang 上册 / xia 下册 / all 全册
 * │       │   ├── math.json          ← 科目文件名 = 科目 id
 * │       │   ├── chinese.json
 * │       │   └── english.json
 * │       └── xia/
 * │           └── math.json
 * ├── junior/
 * │   └── grade7/shang/{math,chinese,english,biology,geography,politics}.json
 * └── senior/
 *     └── grade10/shang/{math,physics,chemistry,biology,history,geography,politics}.json
 * ```
 *
 * ## 归类规则：「meta 优先，目录兜底」
 * 每个 JSON 头部的 `meta` 可写 `stage / grade_id / grade_label / term / subject /
 * subject_label / subject_key / edition`；缺省时按目录层级与文件名推断。
 * 因此 **v1.0.3 的 `banks/<grade>/<subject>.json` 旧布局依然可用**（自动归为「全册」）。
 *
 * ## 组卷规则
 * - 科目选「全部」：从该范围内**每个有题的科目各先抽 1 道**（科目顺序打乱），不足题数再从合并池去重补足；
 * - 科目选具体一科：只从该科目抽题；
 * - 册别选「全部」：上册 + 下册 + 全册题库合并；选具体一册：该册 + 通用全册题库；
 * - 年级选「综合（跨年级）」（id = `shared`，1.0.12 起）：**忽略年级过滤**，把该学段
 *   所有年级、所有册别的题库合并成一个池，因此数学等所有科目都能跨年级混合出题。
 *   `shared/all/` 目录下的综合专项题也一并纳入，不会丢题。
 *
 * ## 性能与兜底
 * - [load] 同步递归扫目录 + 每个文件只读头部 4KB 取 meta（毫秒级）；
 * - [prepare] 后台线程按需解析选中范围的文件，**按文件路径缓存**，重复切换不重复解析；
 * - 解析完成前用内置 24 题兜底，绝不白屏。
 *
 * 完整字段规范见工程根目录 `QUESTION_BANK_FORMAT.md`。
 */
object QuestionBank {

    private const val BANKS_DIR = "banks"
    private const val HEAD_CHARS = 4096

    /**
     * 「跨年级」虚拟年级 id。1.0.12 起不再只代表 `shared/all/` 下那几个综合专项文件，
     * 而是代表**该学段全部年级合并**：[resolve] / [filesOf] / [terms] 遇到它会跳过年级过滤。
     * 只要某学段有 2 个以上真实年级，[grades] 就会自动追加这一项，无需任何题库文件配合。
     */
    const val CROSS_GRADE_ID = "shared"
    const val CROSS_GRADE_LABEL = "综合（跨年级）"

    private fun isCrossGrade(gradeId: String) = gradeId == CROSS_GRADE_ID

    /** 选中跨年级时忽略文件所属年级；否则精确匹配 */
    private fun gradeMatches(fileGradeId: String, chosenGradeId: String): Boolean =
        isCrossGrade(chosenGradeId) || fileGradeId == chosenGradeId

    /** 题库解析完成计数；Compose 读取它即可在后台加载完成后刷新统计 */
    var version by mutableStateOf(0)
        private set

    private lateinit var appCtx: Context

    @Volatile private var catalog: List<BankFile> = emptyList()

    /** 已解析题库缓存：assets 路径 → 题目列表（跨切换复用，万题文件只解析一次） */
    private val cache = ConcurrentHashMap<String, List<Question>>()

    /** 当前选中范围聚合出的「科目 key → 题目池」；按 key 分池保证自定义科目也能独立出题 */
    private data class Pool(val label: String, val questions: List<Question>)
    @Volatile private var pools: Map<String, Pool> = emptyMap()

    @Volatile private var loadedKey: String? = null
    @Volatile private var loadingKey: String? = null

    /** 内置兜底题库（三科各 8 题；外部题库尚未加载完成时启用） */
    private val builtin: List<Question> = listOf(
        Question("c1", Subject.CHINESE, "下面哪个字的读音是 mā？", listOf("妈", "爸", "哥", "弟"), 0, "‘妈’读 mā。"),
        Question("c2", Subject.CHINESE, "‘春’字共有几画？", listOf("8画", "9画", "10画", "11画"), 1, "春共9画。"),
        Question("c3", Subject.CHINESE, "下面哪个是表示动作的词？", listOf("红色", "跑", "书", "大"), 1, "‘跑’是动作。"),
        Question("c4", Subject.CHINESE, "‘明白’的近义词是？", listOf("清楚", "黑暗", "糊涂", "大小"), 0, "明白=清楚。"),
        Question("c5", Subject.CHINESE, "下面哪个是标点符号？", listOf("水", "山", "。", "风"), 2, "‘。’是句号。"),
        Question("c6", Subject.CHINESE, "‘木’加一笔可以变成哪个字？", listOf("本", "月", "日", "口"), 0, "木加一横成‘本’。"),
        Question("c7", Subject.CHINESE, "下列哪个词语描写天气？", listOf("下雨", "吃饭", "读书", "跑步"), 0, "下雨是天气。"),
        Question("c8", Subject.CHINESE, "‘大’的反义词是？", listOf("小", "多", "高", "长"), 0, "大的反义是小。"),

        Question("m1", Subject.MATH, "3 + 5 = ?", listOf("7", "8", "9", "15"), 1, "3+5=8。"),
        Question("m2", Subject.MATH, "12 - 7 = ?", listOf("4", "5", "6", "7"), 1, "12-7=5。"),
        Question("m3", Subject.MATH, "下面哪个数最大？", listOf("19", "91", "29", "90"), 1, "91最大。"),
        Question("m4", Subject.MATH, "2 × 3 = ?", listOf("5", "6", "8", "9"), 1, "2×3=6。"),
        Question("m5", Subject.MATH, "一个三角形有几条边？", listOf("2条", "3条", "4条", "5条"), 1, "三角形有3条边。"),
        Question("m6", Subject.MATH, "哪组两个数相加等于15？", listOf("7和8", "6和7", "5和9", "4和8"), 0, "7+8=15。"),
        Question("m7", Subject.MATH, "钟面上时针指向3，分针指向12，是几点？", listOf("3点", "6点", "9点", "12点"), 0, "时针3分针12是3点。"),
        Question("m8", Subject.MATH, "下面哪个是双数？", listOf("7", "8", "9", "11"), 1, "8是双数。"),

        Question("e1", Subject.ENGLISH, "苹果用英语说是？", listOf("apple", "banana", "orange", "pear"), 0, "苹果=apple。"),
        Question("e2", Subject.ENGLISH, "‘cat’是什么意思？", listOf("猫", "狗", "猪", "鸟"), 0, "cat=猫。"),
        Question("e3", Subject.ENGLISH, "下面哪个是颜色？", listOf("red", "book", "pen", "desk"), 0, "red=红色。"),
        Question("e4", Subject.ENGLISH, "‘one, two, ___’ 下一个是？", listOf("three", "ten", "six", "nine"), 0, "1,2,3 = three。"),
        Question("e5", Subject.ENGLISH, "太阳用英语说是？", listOf("sun", "moon", "star", "sky"), 0, "太阳=sun。"),
        Question("e6", Subject.ENGLISH, "‘hello’的意思是？", listOf("你好", "再见", "谢谢", "对不起"), 0, "hello=你好。"),
        Question("e7", Subject.ENGLISH, "下面哪个是数字5？", listOf("five", "four", "six", "two"), 0, "5=five。"),
        Question("e8", Subject.ENGLISH, "‘dog’和‘cat’都是什么？", listOf("动物", "水果", "颜色", "学校"), 0, "狗和猫都是动物。")
    )

    /** 同步扫描 assets/banks 下所有 .json，构建 Catalog（只读头部，不解析整份题库） */
    fun load(context: Context) {
        appCtx = context.applicationContext
        val paths = mutableListOf<String>()
        collectJson(appCtx, BANKS_DIR, paths)
        catalog = paths.sorted().mapNotNull { path -> classify(path) }
    }

    // ------------------------------------------------------------------
    // 家长控制台查询 API
    // ------------------------------------------------------------------

    /** 三个学段固定展示；无题库的学段 enabled=false（界面置灰，提示如何接入） */
    fun stages(): List<CatalogOption> = Stage.ordered().map { s ->
        val n = catalog.count { it.stage == s }
        CatalogOption(s.id, s.label, n > 0, if (n > 0) "$n 个题库" else "暂无题库")
    }

    /**
     * 该学段下的年级（按数字自然序），末尾追加「综合（跨年级）」聚合项。
     * 只有 1 个真实年级（如当前初中仅七年级）时不追加，避免出现无意义选项。
     * `shared/all/` 目录下的综合专项文件不再单独占一个年级，而是并入聚合项。
     */
    fun grades(stage: Stage): List<CatalogOption> {
        val all = catalog.filter { it.stage == stage }
        val real = all.filter { !isCrossGrade(it.gradeId) }
            .groupBy { it.gradeId }
            .map { (id, files) ->
                CatalogOption(id, files.first().gradeLabel, true, "${files.size} 个题库")
            }
            .sortedBy { naturalKey(it.id) }
        if (real.size < 2) return real

        val sharedCount = all.count { isCrossGrade(it.gradeId) }
        val suffix = if (sharedCount > 0) "（含综合专项 $sharedCount）" else ""
        val detail = "全学段 ${all.size} 个题库混合出题$suffix"
        return real + CatalogOption(CROSS_GRADE_ID, CROSS_GRADE_LABEL, true, detail)
    }

    /** 该年级下固定的「上册 / 下册 / 全部」三项；无题库项置灰 */
    fun terms(stage: Stage, gradeId: String): List<CatalogOption> {
        val files = catalog.filter { it.stage == stage && gradeMatches(it.gradeId, gradeId) }
        return Term.ordered().map { t ->
            val matched = if (t == Term.ALL) files.size
            else files.count { it.term == t || it.term == Term.ALL }
            CatalogOption(t.id, t.label, matched > 0, if (matched > 0) "$matched 个题库" else "暂无")
        }
    }

    /**
     * 该「年级 + 册别」下可选的科目：
     * 「全部」+ 固定语数英三科 + 实际存在的其它科目（物理/化学/生物/历史/地理/道法…自动出现）。
     */
    fun subjects(stage: Stage, gradeId: String, term: Term): List<CatalogOption> {
        val files = filesOf(stage, gradeId, term)
        val out = mutableListOf(
            CatalogOption(
                BankSelection.ALL, "全部", files.isNotEmpty(),
                if (files.isNotEmpty()) "${files.size} 个题库 · 混合出题" else "暂无题库"
            )
        )
        Subject.coreTrio().forEach { s ->
            val n = files.count { it.subject == s }
            out += CatalogOption(s.id, s.label, n > 0, if (n > 0) "$n 个题库" else "暂无")
        }
        files.filter { it.subject !in Subject.coreTrio() }
            .groupBy { it.subjectKey }
            .forEach { (key, group) ->
                out += CatalogOption(key, group.first().subjectLabel, true, "${group.size} 个题库")
            }
        return out
    }

    /** 当前范围的题库统计（如「数学 10564 题」）；UI 同时读取 [version] 以在加载完成后刷新 */
    fun status(sel: BankSelection): String {
        if (loadedKey != keyOf(sel)) return "题库加载中…（解析完成前用内置题兜底）"
        val total = pools.values.sumOf { it.questions.size }
        if (total == 0) return "该范围暂无可用题，将使用内置兜底题"
        return pools.values.filter { it.questions.isNotEmpty() }
            .joinToString(" · ") { "${it.label} ${it.questions.size} 题" } + "　共 $total 题"
    }

    /** 当前范围的完整中文描述，如「小学 · 二年级 · 上册 · 数学 · 通用（课标知识点）」 */
    fun describe(sel: BankSelection): String {
        val files = resolve(sel)
        val stageLabel = sel.stage.label
        // 跨年级时 resolve 会返回多个年级的文件，不能拿第一个文件的年级名当显示名
        val gradeLabel = if (isCrossGrade(sel.gradeId)) CROSS_GRADE_LABEL
        else grades(sel.stage).firstOrNull { it.id == sel.gradeId }?.label
            ?: files.firstOrNull()?.gradeLabel
            ?: sel.gradeId
        val termLabel = sel.term.label
        val subjectLabel = if (sel.allSubjects) "全部科目"
        else files.firstOrNull()?.subjectLabel
            ?: subjects(sel.stage, sel.gradeId, sel.term)
                .firstOrNull { it.id == sel.subjectId }?.label
            ?: sel.subjectId
        val edition = files.map { it.edition }.filter { it.isNotBlank() }.distinct()
            .take(3).joinToString("/")
        return listOf(stageLabel, gradeLabel, termLabel, subjectLabel, edition)
            .filter { it.isNotBlank() }.joinToString(" · ")
    }

    /**
     * 上级选项变化后修正下级：年级不存在→取第一个；当前科目/册别无题→回落到「全部」。
     * 保证家长台永远处于一个有题可出的组合，不会出现「选中却无题」。
     */
    fun normalize(sel: BankSelection): BankSelection {
        var s = sel
        val gs = grades(s.stage)
        if (gs.none { it.id == s.gradeId }) {
            s = s.copy(gradeId = gs.firstOrNull()?.id ?: s.gradeId)
        }
        // 跨年级时某科目可能只有上册（小学数学就是这样）：优先放宽册别到「全部」
        // 来保住家长选的科目，别直接把科目踢回「全部」让人以为「数学又不能选」。
        if (isCrossGrade(s.gradeId) && !s.allSubjects && resolve(s).isEmpty() &&
            catalog.any { it.stage == s.stage && it.subjectKey == s.subjectId }
        ) {
            s = s.copy(term = Term.ALL)
        }
        if (resolve(s).isEmpty() && !s.allSubjects) {
            s = s.copy(subjectId = BankSelection.ALL)
        }
        if (resolve(s).isEmpty()) {
            val t = terms(s.stage, s.gradeId).firstOrNull { it.enabled && it.id != Term.ALL.id }
            s = s.copy(term = t?.let { Term.fromOrNull(it.id) } ?: Term.ALL)
        }
        return s
    }

    /**
     * 选中范围对应的题库文件：
     * - 年级 = 跨年级 → 忽略 gradeId，聚合该学段所有年级（含 shared 综合专项）；
     * - term=全部 → 所有册；具体册 → 该册 + 通用全册；
     * - 科目 = 全部 → 所有科目；否则只取该科目。
     */
    fun resolve(sel: BankSelection): List<BankFile> = catalog.filter { f ->
        f.stage == sel.stage &&
            gradeMatches(f.gradeId, sel.gradeId) &&
            termMatches(f.term, sel.term) &&
            (sel.allSubjects || f.subjectKey == sel.subjectId)
    }

    // ------------------------------------------------------------------
    // 加载与组卷
    // ------------------------------------------------------------------

    private fun filesOf(stage: Stage, gradeId: String, term: Term): List<BankFile> =
        catalog.filter {
            it.stage == stage && gradeMatches(it.gradeId, gradeId) && termMatches(it.term, term)
        }

    private fun termMatches(fileTerm: Term, chosen: Term): Boolean =
        chosen == Term.ALL || fileTerm == chosen || fileTerm == Term.ALL

    private fun keyOf(sel: BankSelection): String =
        "${sel.stage.id}|${sel.gradeId}|${sel.term.id}|${sel.subjectId}"

    /** 后台解析选中范围；同一范围重复调用会合并为一次 */
    fun prepare(sel: BankSelection) {
        if (!::appCtx.isInitialized) return
        val key = keyOf(sel)
        if (loadedKey == key) return
        val shouldStart = synchronized(this) {
            if (loadingKey == key) false else {
                loadingKey = key
                true
            }
        }
        if (!shouldStart) return

        val files = resolve(sel)
        thread(name = "quizguard-bank-load") {
            val acc = mutableMapOf<String, Pair<String, MutableList<Question>>>()
            for (f in files) {
                val qs = cache[f.path] ?: parseFile(f).also { cache[f.path] = it }
                if (qs.isNotEmpty()) {
                    val slot = acc.getOrPut(f.subjectKey) { Pair(f.subjectLabel, mutableListOf()) }
                    slot.second.addAll(qs)
                }
            }
            val map = acc.mapValues { (_, v) -> Pool(v.first, v.second.toList()) }
            synchronized(this) {
                pools = map
                loadedKey = key
                loadingKey = null
            }
            Handler(Looper.getMainLooper()).post { version++ }
        }
    }

    /**
     * 组卷：科目=全部时每个有题的科目各先抽 1 道（科目顺序打乱），不足再从合并池去重补足。
     * 题库未解析完成 → 内置题兜底，保证不白屏。
     */
    fun randomN(count: Int, sel: BankSelection): List<Question> {
        val n = count.coerceIn(1, 10)
        val key = keyOf(sel)
        if (loadedKey != key) {
            prepare(sel)
            if (loadedKey != key) return builtin.shuffled().take(n)
        }
        val gradePools = pools.values.map { it.questions }.filter { it.isNotEmpty() }
        if (gradePools.isEmpty()) return builtin.shuffled().take(n)

        val result = mutableListOf<Question>()
        for (p in gradePools.shuffled()) {
            if (result.size >= n) break
            result += p.random()
        }
        val all = gradePools.flatten()
        var guard = 0
        while (result.size < n && guard++ < 500) {
            val q = all.random()
            if (q !in result) result += q
        }
        return result.take(n)
    }

    // ------------------------------------------------------------------
    // 目录扫描 / meta 归类 / 题目解析
    // ------------------------------------------------------------------

    private fun collectJson(ctx: Context, dir: String, out: MutableList<String>) {
        val names = try {
            ctx.assets.list(dir)?.toList() ?: return
        } catch (_: Exception) {
            return
        }
        for (name in names) {
            val path = if (dir.isEmpty()) name else "$dir/$name"
            if (name.endsWith(".json", true)) {
                out += path
            } else {
                val children = try {
                    ctx.assets.list(path)
                } catch (_: Exception) {
                    null
                }
                if (!children.isNullOrEmpty()) collectJson(ctx, path, out)
            }
        }
    }

    /** 读文件头部，抓 meta 里的字符串字段（重复 key 保留最先出现，即 meta 段的那个） */
    private fun metaOf(path: String): Map<String, String> {
        val head = try {
            appCtx.assets.open(path).bufferedReader().use { r ->
                val buf = CharArray(HEAD_CHARS)
                val n = r.read(buf)
                if (n <= 0) "" else String(buf, 0, n)
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        val m = LinkedHashMap<String, String>()
        Regex("\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*:\\s*\"([^\"]*)\"")
            .findAll(head).forEach { m.putIfAbsent(it.groupValues[1], it.groupValues[2]) }
        return m
    }

    private fun classify(path: String): BankFile? {
        val meta = metaOf(path)
        val rel = path.removePrefix("$BANKS_DIR/")
        val parts = rel.split('/')
        val fileName = parts.last().substringBeforeLast('.')
        val dirs = parts.dropLast(1)

        // 学段：meta > 目录中的学段名 > 由年级编号推断
        val stage = meta["stage"]?.let { Stage.fromOrNull(it) }
            ?: dirs.firstNotNullOfOrNull { Stage.fromOrNull(it) }
            ?: inferStage(dirs, meta["grade"])

        // 册别：meta > 目录中的册别名 > 旧 meta.grade 文本（"二年级上册"）> 全册
        val term = meta["term"]?.let { Term.fromOrNull(it) }
            ?: dirs.firstNotNullOfOrNull { Term.fromOrNull(it) }
            ?: when {
                meta["grade"]?.contains("上册") == true -> Term.SHANG
                meta["grade"]?.contains("下册") == true -> Term.XIA
                else -> Term.ALL
            }

        // 年级 id：meta.grade_id > 目录中第一个「既非学段也非册别」的段 > default
        val gradeId = meta["grade_id"]?.takeIf { it.isNotBlank() }
            ?: dirs.firstOrNull { d -> Stage.fromOrNull(d) == null && Term.fromOrNull(d) == null }
            ?: "default"
        val gradeLabel = meta["grade_label"]?.takeIf { it.isNotBlank() }
            ?: meta["grade"]?.takeIf { it.isNotBlank() }
            ?: gradeId

        val rawSubject = meta["subject"]?.takeIf { it.isNotBlank() } ?: fileName
        val subject = Subject.from(rawSubject)
        val subjectKey = if (subject == Subject.OTHER) {
            meta["subject_key"]?.takeIf { it.isNotBlank() }
                ?: meta["subject_label"]?.takeIf { it.isNotBlank() }
                ?: rawSubject
        } else subject.id
        val subjectLabel = meta["subject_label"]?.takeIf { it.isNotBlank() }
            ?: if (subject == Subject.OTHER) rawSubject else subject.label

        return BankFile(
            path = path,
            stage = stage,
            gradeId = gradeId,
            gradeLabel = gradeLabel,
            term = term,
            subject = subject,
            subjectKey = subjectKey,
            subjectLabel = subjectLabel,
            edition = meta["edition"] ?: ""
        )
    }

    private fun inferStage(dirs: List<String>, rawGrade: String?): Stage {
        val text = (dirs.joinToString("/") + " " + (rawGrade ?: "")).lowercase(Locale.ROOT)
        val num = Regex("grade\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            num == null -> Stage.PRIMARY
            num in 1..6 -> Stage.PRIMARY
            num in 7..9 -> Stage.JUNIOR
            num >= 10 -> Stage.SENIOR
            else -> Stage.PRIMARY
        }
    }

    /** 排序用：把 grade2 / grade10 按数字排，非数字按字典序 */
    private fun naturalKey(id: String): String {
        val n = Regex("(\\d+)").find(id)?.groupValues?.get(1)?.toIntOrNull() ?: 9999
        return "%05d_%s".format(n, id)
    }

    /**
     * 解析单个题库文件。支持：
     *  1) 纯数组 `[{...}]`
     *  2) 带 meta 的对象 `{"meta":{...},"questions":[...]}`
     * 仅保留 type=="choice"、有 >=2 个选项、answerIndex 合法的题目。
     */
    private fun parseFile(bank: BankFile): List<Question> {
        return try {
            val json = appCtx.assets.open(bank.path).bufferedReader().use { it.readText() }
            val arr: JSONArray = if (json.trimStart().startsWith("[")) {
                JSONArray(json)
            } else {
                JSONObject(json).getJSONArray("questions")
            }
            val list = mutableListOf<Question>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!o.optString("type", "choice").equals("choice", ignoreCase = true)) continue
                val optsArr = o.optJSONArray("options") ?: continue
                if (optsArr.length() < 2) continue
                val ans = o.optInt("answerIndex", -1)
                if (ans < 0 || ans >= optsArr.length()) continue
                val text = o.optString("text", "")
                if (text.isBlank()) continue
                // 单题可用 subject 覆盖文件归类；仅识别已知科目，未知沿用文件归类
                val inline = o.optString("subject", "")
                val subj = if (inline.isBlank()) bank.subject else Subject.from(inline)
                val finalSubject = if (subj == Subject.OTHER) bank.subject else subj
                list.add(
                    Question(
                        id = o.optString("id", "q$i"),
                        subject = finalSubject,
                        text = text,
                        options = List(optsArr.length()) { optsArr.getString(it) },
                        answerIndex = ans,
                        explanation = o.optString("explanation", ""),
                        subjectLabel = if (finalSubject == bank.subject) bank.subjectLabel
                        else finalSubject.label
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
