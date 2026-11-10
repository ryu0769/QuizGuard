# 题库目录 / 接入位说明

本文件只作说明，App 只扫描本目录下所有 `*.json`，`.md` 会被忽略。

## 当前已有的题库

| 路径 | 学段 | 年级 | 册别 | 科目 | 教材 | 题数 |
|---|---|---|---|---|---|---|
| `primary/grade2/shang/math.json` | 小学 | 二年级 | 上册 | 数学 | 教材版本 | 10586（选择题 10564） |
| `primary/grade1/shang/english.json` | 小学 | 一年级 | 上册 | 英语 | 一年级起点体系 | 160 |
| `primary/grade1/xia/english.json` | 小学 | 一年级 | 下册 | 英语 | 一年级起点体系 | 100 |
| `primary/grade2/shang/english.json` | 小学 | 二年级 | 上册 | 英语 | 一年级起点体系 | 60 |
| `primary/grade2/xia/english.json` | 小学 | 二年级 | 下册 | 英语 | 一年级起点体系 | 69 |
| `primary/grade3/shang/english.json` | 小学 | 三年级 | 上册 | 英语 | 一年级起点体系 | 69 |
| `primary/grade3/xia/english.json` | 小学 | 三年级 | 下册 | 英语 | 一年级起点体系 | 60 |
| `primary/grade4/shang/english.json` | 小学 | 四年级 | 上册 | 英语 | 一年级起点体系 | 90 |
| `primary/grade4/xia/english.json` | 小学 | 四年级 | 下册 | 英语 | 一年级起点体系 | 55 |
| `primary/grade5/shang/english.json` | 小学 | 五年级 | 上册 | 英语 | 一年级起点体系 | 147 |
| `primary/grade5/xia/english.json` | 小学 | 五年级 | 下册 | 英语 | 一年级起点体系 | 86 |
| `primary/grade6/shang/english.json` | 小学 | 六年级 | 上册 | 英语 | 一年级起点体系 | 95 |
| `primary/grade6/xia/english.json` | 小学 | 六年级 | 下册 | 英语 | 一年级起点体系 | 101 |
| `primary/grade1~grade6/{shang,xia}/chinese.json` | 小学 | 一~六年级 | 上下册 | 语文 | 统编体系 | 3023（12 册，详见下表） |
| `primary/shared/all/chinese.json` | 小学 | 综合专项（`shared`，并入跨年级池） | 全册 | 语文 | 统编体系 | 51 |
| `primary/shared/all/english.json` | 小学 | 综合专项（`shared`，并入跨年级池） | 全册 | 英语 | 一年级起点体系 | 40 |
| `primary/grade{1,3,4,5,6}/shang/math.json` | 小学 | 一 / 三 / 四 / 五 / 六年级 | 上册 | 数学 | 通用（课标知识点） | 6234（5 册） |
| `junior/grade7/shang/*.json` | 初中 | 七年级 | 上册 | 语文 / 数学 / 英语 / 道法 / 历史 / 地理 / 生物 | 教材版本 | 170（7 科） |

> v1.0.15 起移除了 `primary/sample/` 示例年级（三科各 8 题）：它与代码内硬编码的
> 24 道兜底题（`QuestionBank.kt` 内的 `builtin` 列表）内容重复，删掉后年级列表不再出现「示例年级」，
> 题库为空时仍会自动回退这 24 道题，功能不受影响。

> 英语库共 **1132 题**（一至六年级 12 册 1092 题 + 综合 40 题），语文库共 **3074 题**
> （12 册 3023 题 + 综合 51 题），全部为 4 选 1，可用率 100%。
> **v1.0.12 起 `shared` 不再是「只有两个文件的独立年级」**，而是代码固定的**全学段聚合年级**：
> 年级列表在有 ≥2 个真实年级时自动追加「综合（跨年级）」，选中后 `resolve()` 跳过年级过滤，
> 把该学段所有年级的题库与上表两个综合专项文件合并。因此**数学也能跨年级混合出题**
> （小学册别选「全部」时约：数学 16798 题 + 语文 3074 题 + 英语 1132 题），
> 不需要也不应该补一个 `shared/all/math.json`；显示名由代码给定，两个文件的 `grade_label` 无需再保持一致。
> v1.0.11 及以前这里只有语文 / 英语两个综合专项文件，数学被置灰，那就是「小学数学不能选跨年级」的原因。
> 小学数学一至六年级上册现已齐全（原二年级 10586 题 + v1.0.11 新增 5 册 6234 题）；
> **初中七年级上册 7 科**为标准化试卷格式，只接入客观选择题，详见下方说明。

## 英语题库说明（v1.0.7 接入，v1.0.11 替换为校订版）

> **v1.0.11 已整体替换**：新版与旧版同为 1132 题、meta 一致，差异是 **52 道选择题的干扰项经过校订**
> （题干、正确答案、解析不变）。旧源备份为 `sources/english_grade1-6_source_v1.json`，
> 新源为 `sources/english_grade1-6_source.json`，重跑 `tools/import_english_bank.py` 即得到当前 13 个文件。

来源：资料库「教材版本一至六年级英语题库.json」，`meta.edition = 一年级起点体系`，
按 `grade` + `volume` 拆成 12 个「年级 / 册别」文件，「综合 · 词汇分类」40 题单列为一个年级。

原文件含 `choice` 810 题与 `fill`（词义填空）322 题；App 只加载选择题，因此导入时把
322 道填空题**转成 4 选 1**：正确答案为原 `answer[0]`，干扰项取自同一年级同册其它填空题的
中文词义（排除相同 / 互相包含的字符串，不足再从全库补），固定随机种子保证可复现。
这类题带 `origin: "fill_to_choice"` 标记，原 `explanation` 保留，答错时照常展示解题思路。

二年级上册现在同时有语文、数学与英语题库：家长台把科目切到「全部」即**三科混合出题**。

## 语文题库说明（v1.0.10 接入）

来源：资料库「教材版本一至六年级语文题库.json」，`meta.edition = 统编体系`，
按 `grade` + `volume` 拆成 12 个「年级 / 册别」文件，「综合」51 题归入 `shared/all`。

| 年级 | 上册 | 下册 | 合计 |
|---|---|---|---|
| 一年级 | 334 | 266 | 600 |
| 二年级 | 244 | 216 | 460 |
| 三年级 | 243 | 235 | 478 |
| 四年级 | 246 | 228 | 474 |
| 五年级 | 252 | 253 | 505 |
| 六年级 | 235 | 271 | 506 |
| 综合（跨年级） | — | — | 51 |

原文件含 `choice` 1599 题与 `fill` 1475 题；App 只加载选择题，因此导入时把填空题**转成 4 选 1**：
正确答案为原 `answer[0]`，干扰项按「同题型 + 同答案类型」分层挑选（拼音音节 / 汉字词语 / 诗句 / 数字 / 字母互不混用），
优先取同年级同册，排除相同 / 互相包含的字符串，固定随机种子保证可复现。
这类题带 `origin: "fill_to_choice"` 标记，原 `explanation` 保留，答错时照常展示解题思路。
转换脚本为 `tools/import_chinese_bank.py`，原始文件为 `sources/chinese_grade1-6_source.json`。

> 源库 `meta.note` 说明古诗篇目、词语表与知识点按统编版常见进度整理为**基础版**，
> 必背 / 选背划分请对照实际教材核验。

## 小学数学 5 册 + 初中七上 7 科（v1.0.11 接入）

**小学数学上册 5 册（6234 题）**：一年级 206、三年级 2189、四年级 1451、五年级 1359、六年级 1029。
格式与原有二年级数学库一致，几乎全是 4 选 1；一年级 6 道填空已转为选择题。
`meta.edition = 通用（课标知识点）`，题目为知识点整理，请对照实际教材核验。

**初中七年级上册 7 科（170 题可用）**：语文 26 / 数学 21 / 英语 29 / 道德与法治 23 /
历史 22 / 地理 25 / 生物 24。源文件采用「标准化试卷格式」（`schemaVersion`、知识点编码、
布鲁姆层级、难度、`answer` 对象、`passage` 阅读材料、占位图 `assets`），与小学题库不同，
由 `tools/import_subject_banks.py` 转换：

- 带 `options` + `answerIndex` 的 `single_choice / cloze / reading / experiment` 直接保留（132 道）；
  带 `passage` 的题把材料拼到题干前，否则如「How old is Anna?」无原文无法作答；
- `blank` 填空取 `answer.final` 为正确答案，按同答案类型从同文件选项 / 全初中填空答案分层挑干扰项（38 道）；
- `multi_choice`（多选）与 `short_answer / essay / graph / calculation / proof`（需文字、作文或图片）
  **共 48 道跳过**，原始文件保留在 `sources/`，将来 App 支持多题型时可直接再导入。

至此「初中」学段在家长台由置灰变为可选，四年级起的多学科接入位第一次真正落上数据。

## 初中 / 高中后续接入位（放文件即可，零改代码）

七年级上册 7 科已在 `junior/grade7/shang/` 落地；下面示例中的其余年级 / 册别仍为预留。

```
banks/
├── junior/                          # 初中（七年级 ~ 九年级）
│   └── grade7/
│       ├── shang/
│       │   ├── chinese.json
│       │   ├── math.json
│       │   ├── english.json
│       │   ├── biology.json          # 生物
│       │   ├── geography.json        # 地理
│       │   └── politics.json         # 道德与法治
│       └── xia/
│           └── math.json
└── senior/                          # 高中（高一 ~ 高三）
    └── grade10/
        └── shang/
            ├── math.json
            ├── physics.json          # 物理
            ├── chemistry.json        # 化学
            ├── biology.json
            ├── history.json          # 历史
            ├── geography.json
            └── politics.json
```

只要目录下出现 `.json`，家长控制台的「初中 / 高中」学段就会自动变为可选，
对应年级、册别、科目也会自动生成。学段目录名必须是 `primary` / `junior` / `senior`。

## 科目文件名对照

| 文件名 | 科目 | 文件名 | 科目 |
|---|---|---|---|
| `chinese.json` | 语文 | `physics.json` | 物理 |
| `math.json` | 数学 | `chemistry.json` | 化学 |
| `english.json` | 英语 | `biology.json` | 生物 |
| `politics.json` | 道德与法治 | `history.json` | 历史 |
| `geography.json` | 地理 | `science.json` | 科学 |
| `it.json` | 信息技术 | 其他名字 | 自定义科目（需写 `subject_label`） |

## 每个 JSON 的 meta 建议写法

```json
{
  "meta": {
    "stage": "junior",
    "grade_id": "grade7",
    "grade_label": "七年级",
    "term": "shang",
    "subject": "PHYSICS",
    "subject_label": "物理",
    "edition": "通用（课标知识点）",
    "total": 120
  },
  "questions": [ ... ]
}
```

- `stage`：`primary` / `junior` / `senior`
- `term`：`shang` 上册 / `xia` 下册 / `all` 全册（不分册或全年通用）
- meta 字段缺省时按目录名与文件名自动推断，所以最简写法甚至可以只有 `questions` 数组。
- 完整字段规范、题目格式与校验规则见工程根目录 `QUESTION_BANK_FORMAT.md`。

## 版权提醒

- 本仓库公开分发的题库仅 `primary/grade2/shang/math.json` 一份：10586 题中 10506 题为按运算规则穷举得到的算式组合（通用数表 / 公式级事实数据），不含教材或教辅原题、题干文字、插图与章节编排；详见根目录 README 与 `banks/README.md`。
- `primary/**/english.json`、`primary/**/chinese.json`、`junior/grade7/shang/*.json` 等外部题库均按 `.gitignore` 排除，**未随源码分发**；如需自行引入，请先确认授权。
- 章节名、年级 / 册别、教材版本字段仅作知识点索引与难度分级，不表示与任何出版社存在关联或配套关系。
- 不加载题库时，App 会自动使用代码内 24 道兜底题（三科各 8 题）运行，界面不会白屏。
