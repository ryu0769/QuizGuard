package com.quizguard.app.core

/**
 * 学段。小学 / 初中 / 高中 三个固定档位即为「多学段接入接口」：
 * 往 `assets/banks/junior/...`、`assets/banks/senior/...` 丢题库文件，
 * 家长控制台会自动出现对应学段，无需改任何代码。
 */
enum class Stage(val id: String, val label: String) {
    PRIMARY("primary", "小学"),
    JUNIOR("junior", "初中"),
    SENIOR("senior", "高中"),
    OTHER("other", "其它");

    companion object {
        /** 家长控制台固定展示的学段顺序（三个档位始终可见，便于看到扩展位） */
        fun ordered(): List<Stage> = listOf(PRIMARY, JUNIOR, SENIOR)

        fun from(raw: String?): Stage = fromOrNull(raw) ?: OTHER

        /** 严格解析：无法识别时返回 null，供目录层级推断用（避免把普通目录误判成学段） */
        fun fromOrNull(raw: String?): Stage? {
            val k = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull {
                it.id == k || it.label == raw?.trim() ||
                    (it == JUNIOR && (k == "middle" || k == "juniorhigh")) ||
                    (it == SENIOR && (k == "high" || k == "seniorhigh"))
            }
        }
    }
}

/**
 * 册别。目录名 `shang/` `xia/` `all/` 分别对应上册 / 下册 / 全册。
 * 家长控制台固定给出「上册 / 下册 / 全部」三项；选「全部」= 上册+下册+全册题库合并出题。
 */
enum class Term(val id: String, val label: String) {
    SHANG("shang", "上册"),
    XIA("xia", "下册"),
    ALL("all", "全部");

    companion object {
        fun ordered(): List<Term> = listOf(SHANG, XIA, ALL)

        fun from(raw: String?): Term = fromOrNull(raw) ?: ALL

        fun fromOrNull(raw: String?): Term? {
            val k = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == k || it.label == raw?.trim() }
                ?: when (k) {
                    "上", "first", "1", "a", "vol1", "book1" -> SHANG
                    "下", "second", "2", "b", "vol2", "book2" -> XIA
                    "全", "全册", "全年", "all", "full", "both" -> ALL
                    else -> null
                }
        }
    }
}

/**
 * 科目。小学语数英之外，预留初中 / 高中常见学科。
 * 未知科目会落到 [OTHER] 并用 JSON 里的 `subject_label` 作为显示名，
 * 因此开源贡献者甚至可以接入枚举里没有的科目（如「通用技术」）而无需改代码。
 */
enum class Subject(val id: String, val label: String, val aliases: Set<String>) {
    CHINESE("chinese", "语文", setOf("语文", "国语", "yuwen", "cn")),
    MATH("math", "数学", setOf("数学", "shuxue", "mathematics", "maths")),
    ENGLISH("english", "英语", setOf("英语", "yingyu", "en")),
    PHYSICS("physics", "物理", setOf("物理", "wuli")),
    CHEMISTRY("chemistry", "化学", setOf("化学", "huaxue")),
    BIOLOGY("biology", "生物", setOf("生物", "shengwu")),
    HISTORY("history", "历史", setOf("历史", "lishi")),
    GEOGRAPHY("geography", "地理", setOf("地理", "dili")),
    POLITICS("politics", "道德与法治", setOf("道德与法治", "道法", "政治", "思想品德", "品德", "politics")),
    SCIENCE("science", "科学", setOf("科学", "kexue")),
    IT("it", "信息技术", setOf("信息技术", "信息科技", "计算机", "信息", "computing", "ict")),
    OTHER("other", "其它", emptySet());

    companion object {
        /** 小学阶段家长控制台固定展示的三科 */
        fun coreTrio(): List<Subject> = listOf(CHINESE, MATH, ENGLISH)

        fun from(raw: String?): Subject {
            val k = raw?.trim()?.lowercase() ?: return OTHER
            entries.forEach { s ->
                if (s != OTHER && (k == s.id || s.aliases.any { it.lowercase() == k })) return s
            }
            // 兼容带后缀的文件名，如 physics_7a.json / math_unit1.json
            entries.forEach { s ->
                if (s != OTHER && (k.startsWith(s.id + "_") || k.startsWith(s.id + "-"))) return s
            }
            if (k == "all" || k == "全部") return OTHER
            return OTHER
        }
    }
}

/**
 * 家长在控制台选定的「出题范围」：学段 → 年级 → 册别 → 科目。
 * [subjectId] 为 `"all"` 时表示不限制科目，从该范围内所有科目随机混合出题。
 */
data class BankSelection(
    val stage: Stage = Stage.PRIMARY,
    val gradeId: String = DEFAULT_GRADE,
    val term: Term = Term.SHANG,
    val subjectId: String = Subject.MATH.id
) {
    val allSubjects: Boolean get() = subjectId == ALL
    fun subject(): Subject = Subject.from(subjectId)

    companion object {
        const val ALL = "all"
        const val DEFAULT_GRADE = "grade2"
    }
}

/** 单个题库文件（assets 相对路径 + 从目录/meta 解析出的四级归类信息） */
data class BankFile(
    val path: String,
    val stage: Stage,
    val gradeId: String,
    val gradeLabel: String,
    val term: Term,
    val subject: Subject,
    /** 选择/持久化用的科目 key：枚举科目为枚举 id，自定义科目为原文（如 "通用技术"） */
    val subjectKey: String,
    val subjectLabel: String,
    val edition: String
)

/** 家长控制台的选项（enabled=false 表示该档位当前没有题库，界面置灰并给出提示） */
data class CatalogOption(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val detail: String = ""
)

/** 单道题目 */
data class Question(
    val id: String,
    val subject: Subject,
    val text: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String = "",
    /** 显示名：自定义科目（Subject.OTHER）时取题库 meta.subject_label，否则取枚举 label */
    val subjectLabel: String = subject.label,
    /** v1.0.6：非空表示本题来自错题本复习，值为错题本条目 key（普通新题为 null） */
    val reviewKey: String? = null,
    /** v1.0.6：艾宾浩斯当前档位（0 起）；普通新题为 -1 */
    val reviewStage: Int = -1
)

/** 界面阶段：守护中 / 答题中 */
enum class Phase { IDLE, QUIZ }

/** 答题反馈 */
enum class Feedback { NONE, CORRECT, WRONG }

/**
 * 答错后的处理方式（v1.0.5 新增，家长可在控制台切换并持久化）。
 * 无论哪种策略，答错都会先停在「正确答案 + 解题思路」页面，
 * 必须由孩子手动点「看懂了，下一题」才会继续，不会自动跳过。
 */
enum class WrongPolicy(val id: String, val label: String, val hint: String) {
    /** 只把答错的题排到队尾重做，已答对的题不用重来（默认，挫败感低） */
    RETRY_WRONG("retry_wrong", "错题重做", "答错的题排到队尾再答一次，答对的不用重来"),

    /** 沿用 v1.0.4 旧行为：整组重新抽题重做，直到一组全对 */
    RESTART_GROUP("restart_group", "整组重来", "答错一道就重新抽一整组题，从第一题重做");

    companion object {
        fun from(raw: String?): WrongPolicy = entries.firstOrNull { it.id == raw } ?: RETRY_WRONG
    }
}
