# 刷吧!少年 / shuaba.app · 题库格式规范（开源贡献指南）

> 适用版本：**v1.0.4+** ｜ 引擎实现：`app/src/main/java/com/quizguard/app/core/QuestionBank.kt`
> 设计目标：**丢一个 JSON 文件进 `assets/banks/`，家长控制台就自动多出对应选项，零改代码。**

App 会递归扫描 `assets/banks/` 下所有 `*.json`（其它扩展名忽略，例如目录里的 `CATALOG.md`），
读取每个文件头部 4KB 的 `meta` 建立**四级目录树**：

```
学段 Stage  →  年级 Grade  →  册别 Term  →  科目 Subject
小学/初中/高中   一年级…高三    上册/下册/全部   语文/数学/英语/物理/化学/…
```

家长在控制台依次选择这四级，App 从该范围随机抽题。
**科目选「全部」= 该范围所有科目随机混合出题；册别选「全部」= 上册 + 下册 + 全册题库合并。**

---

## 一、目录约定（推荐写法）

```
app/src/main/assets/banks/
├── primary/                              ← 学段目录，必须是 primary / junior / senior
│   └── grade2/                           ← 年级 id，建议 grade1…grade12（数字用于排序）
│       ├── shang/                        ← 册别目录：shang 上册 / xia 下册 / all 全册
│       │   ├── math.json                 ← 科目文件名 = 科目 id
│       │   ├── chinese.json
│       │   └── english.json
│       └── xia/
│           └── math.json
├── junior/                               ← 初中（七年级 ~ 九年级）接入位
│   └── grade7/
│       └── shang/
│           ├── chinese.json
│           ├── math.json
│           ├── english.json
│           ├── biology.json
│           ├── geography.json
│           └── politics.json
└── senior/                               ← 高中（高一 ~ 高三）接入位
    └── grade10/
        └── shang/
            ├── math.json
            ├── physics.json
            ├── chemistry.json
            ├── biology.json
            ├── history.json
            ├── geography.json
            └── politics.json
```

**归类规则：meta 优先，目录兜底。**
meta 写了什么就按什么归类；没写则按「目录里的学段名 / 册别名 + 第一个非学段非册别的目录段作年级 id + 文件名作科目」推断。
因此目录不是硬性要求——你把 `math.json` 放在任何层级，只要 `meta` 写全，也能正确归类。

### 兼容的旧布局

| 布局 | 归类结果 |
|---|---|
| `banks/<grade>/<subject>.json`（v1.0.3） | 学段按年级编号推断（1-6 小学 / 7-9 初中 / ≥10 高中）；册别看 `meta.grade` 是否含「上册/下册」，否则 = `all` 全册 |
| `banks/<stage>/<grade>/<subject>.json` | 册别同上；无册别信息时 = `all` 全册 |
| `banks/<grade>/shang/math.json` | 学段按年级编号推断，册别取目录 `shang` |
| assets 根目录 `math.json` 等 | **不再扫描**，请移入 `banks/` |

### 当前仓库自带题库

| 路径 | 归类 | 题数 |
|---|---|---|
| `primary/grade2/shang/math.json` | 小学 · 二年级 · 上册 · 数学 · **对应教材版本** | 10586（选择题 10564） |
| `primary/grade1~grade6/{shang,xia}/english.json` | 小学 · 一~六年级 · 上下册 · 英语 · **一年级起点体系** | 1092（12 个文件） |
| `primary/grade1~grade6/{shang,xia}/chinese.json` | 小学 · 一~六年级 · 上下册 · 语文 · **统编体系** | 3023（12 个文件） |
| `primary/shared/all/{chinese,english}.json` | 小学 · 综合专项（`grade_id=shared`，v1.0.12 起并入「综合（跨年级）」聚合池） · 全册 · 语文 51 / 英语 40 | 91 |
| `primary/grade{1,3,4,5,6}/shang/math.json` | 小学 · 一/三/四/五/六年级 · 上册 · 数学 · **通用（课标知识点）** | 6234（5 册） |
| `junior/grade7/shang/*.json` | 初中 · 七年级 · 上册 · **7 科** · 对应教材版本 | 170（7 个文件） |

> v1.0.15 起移除了 `primary/sample/` 示例年级：它与代码内硬编码的 24 道兜底题
> （`QuestionBank.kt` 内的 `builtin` 列表，三科各 8 题）重复，删掉后年级列表不再出现「示例年级」。
> 英语库（v1.0.7）共 1132 题，其中 322 道由「词义填空」转换而来；语文库（v1.0.10）共 3074 题，
> 其中 1475 道由拼音 / 字词 / 诗句等填空题转换而来。两者全部为 4 选 1，转换题带
> `origin: "fill_to_choice"`，干扰项按同册 / 同题型 / 同答案类型分层生成，原始 `explanation` 完整保留。
> 两个 `shared/all` 文件共用 `grade_id = shared`。**v1.0.12 起** `shared` 由代码固定为
> **全学段聚合年级**：年级列表自动出现「综合（跨年级）」，选中即合并该学段所有年级的题库，
> 数学等所有科目都能跨年级混合；显示名由代码给定，不再要求两个文件的 `grade_label` 一致。
> 因此**不要**为了「让数学支持跨年级」去造 `shared/all/math.json`，把题库按真实年级放好即可。
> 英语库 v1.0.11 起为**校订版**（52 道干扰项修订，题数 / 答案 / 解析不变）。
> 小学数学一至六年级上册已齐全；初中七年级上册 7 科为「标准化试卷格式」，转换规则见
> `tools/import_subject_banks.py` 与 `assets/banks/CATALOG.md`。

---

## 二、顶层 JSON 结构（两种都支持）

### 方式 A：带 meta 的对象（推荐）

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
  "questions": [
    {
      "id": "p001",
      "type": "choice",
      "subject": "PHYSICS",
      "chapter": "声现象",
      "text": "声音在下列哪种介质中传播最快？",
      "options": ["真空", "空气", "水", "钢铁"],
      "answerIndex": 3,
      "explanation": "一般情况下声音在固体中最快，真空不能传声。"
    }
  ]
}
```

### 方式 B：纯数组（最简）

```json
[
  {
    "id": "m1",
    "type": "choice",
    "text": "3 + 5 = ?",
    "options": ["7", "8", "9", "15"],
    "answerIndex": 1,
    "explanation": "3+5=8"
  }
]
```

方式 B 完全依赖目录路径与文件名归类；`meta` 缺失时教材版本为空、年级显示名取目录 id。

---

## 三、字段表

### 3.1 `meta`（全部可选，但建议至少写 `grade_label` 与 `subject`）

| 字段 | 类型 | 说明 | 缺省时 |
|---|---|---|---|
| `stage` | string | `primary` 小学 / `junior` 初中 / `senior` 高中 | 目录中的学段名；再按年级编号推断 |
| `grade_id` | string | 年级唯一 id，用于持久化与排序，如 `grade7` | 路径中第一个非学段/册别的目录段 |
| `grade_label` | string | 家长台显示名，如 `七年级` | `meta.grade` → 目录 id |
| `grade` | string | **旧版字段**，如 `二年级上册`；仅作兼容，含「上册/下册」时会据此推断册别 | — |
| `term` | string | `shang` 上册 / `xia` 下册 / `all` 全册 | 目录中的册别名 → `grade` 文本推断 → `all` |
| `subject` | string | 科目 id 或中文名，见下表 | 文件名（去掉 `.json`） |
| `subject_label` | string | 显示名；**自定义科目必填**，如 `通用技术` | 枚举 label 或科目原文 |
| `subject_key` | string | 自定义科目的持久化 key（同年级下多个自定义科目不能重名） | `subject_label` |
| `edition` | string | 教材版本，如 `通用（课标知识点）`、`对应教材版本`，会显示在家长台范围描述里 | 空 |
| `total` | number | 题数备注，仅供人查阅，**引擎不校验** | — |
| 其它字段 | any | `chapter_counts` / `question_types` / `label_counts` 等统计信息可自由保留 | — |

> ⚠️ `meta` 请放在文件**最前面**（前 4096 字符内），且新字段尽量排在前面——引擎只读头部取 meta，
> 万题大文件不会全量解析两次。

### 3.2 `questions[]` 单道题

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `text` | string | ✅ | 题干，空白会被跳过 |
| `options` | string[] | ✅ | ≥ 2 个选项 |
| `answerIndex` | int | ✅ | 正确选项下标，**从 0 起**，越界会被跳过 |
| `type` | string | — | 仅 `choice` 会加载；`fill` 等其它值一律跳过（缺省视为 `choice`） |
| `id` | string | — | 题目唯一 id，缺省用 `q<下标>` |
| `subject` | string | — | 可覆盖文件归类；仅识别已知科目，未知沿用文件归类 |
| `chapter` | string | — | 章节，目前仅作数据保留 |
| `explanation` | string | — | 答错时展示的解析 |
| `label` / `difficulty` / `grade` / `volume` / `level` / `origin` | any | — | 自由扩展字段，引擎忽略；英语 / 语文库用 `origin: "fill_to_choice"` 标记由填空题转换的题 |

**会被自动过滤的题**：非选择题 / 选项少于 2 个 / `answerIndex` 越界或缺失 / 题干空白。
若整个范围没有合法题，App 会退回内置 24 道兜底题，不会白屏。

### 3.3 科目 id 对照表（初高中多科目接口）

| 文件名 / subject | 显示名 | 文件名 / subject | 显示名 |
|---|---|---|---|
| `chinese` / `CHINESE` / 语文 | 语文 | `physics` / `PHYSICS` / 物理 | 物理 |
| `math` / `MATH` / 数学 | 数学 | `chemistry` / `CHEMISTRY` / 化学 | 化学 |
| `english` / `ENGLISH` / 英语 | 英语 | `biology` / `BIOLOGY` / 生物 | 生物 |
| `politics` / 政治 / 道德与法治 | 道德与法治 | `history` / 历史 | 历史 |
| `geography` / 地理 | 地理 | `science` / 科学 | 科学 |
| `it` / 信息技术 | 信息技术 | 其它任意值 | 自定义（需写 `subject_label`） |

家长台的科目行固定显示「全部 / 语文 / 数学 / 英语」，**该范围实际存在的其它科目会自动追加**（如物理、化学），
因此初中高中题库放进去后，UI 无需任何修改。

---

## 四、组卷规则（决定每次出什么题）

设家长选择 `小学 · 二年级 · 上册 · 数学`，题数 3：

1. `resolve()` 取出所有匹配文件：学段/年级相等，册别为「上册」或「全册」，科目为 `math`。
2. 按文件路径缓存解析结果，多个文件合并成同一科目的题池。
3. 从题池随机抽 `题数` 道不重复题目。

设家长选择 `小学 · 二年级 · 全部 · 全部科目`，题数 3：

1. 取出该年级所有册别、所有科目的文件（二年级 = 上册语文 + 上册数学 + 上册英语 + 全册综合）。
2. **每个有题的科目先各抽 1 道**（科目顺序打乱）→ 语文 1 + 数学 1 + 英语 1。
3. 若题数 > 科目数，剩余从合并池随机去重补足；题数 < 科目数则随机取前几个科目。
4. 答错任意一题 → 按**当前设置**重新抽一整组，从第 1 题重做。

> 册别选「上册」时，`all/`（全册通用）题库**也会并入**；选「全部」则上册+下册+全册全并。

---

## 五、操作步骤：新增 / 扩展题库

### 加一个年级的上册数学

```bash
mkdir -p app/src/main/assets/banks/primary/grade3/shang
# 把 math.json 放进去，meta 至少写：
# {"meta":{"grade_label":"三年级","term":"shang","subject":"MATH","edition":"通用（课标知识点）"},"questions":[...]}
```

### 给某年级补下册 / 补科目

```
primary/grade2/xia/math.json          # 二年级下册数学（尚无）
primary/grade2/shang/english.json     # 二年级上册英语（v1.0.7 已有）
primary/grade2/shang/chinese.json     # 二年级上册语文（v1.0.10 已有）
```

放好新文件后家长台对应选项会自动由置灰变为可选；选「全部科目」即多科混合出题。
二年级上册现已是**语文 + 数学 + 英语三科混合**，一至六年级语文 / 英语上下册与**数学上册**全部齐备，
可直接参考它们的 `meta` 写法；二年级下册数学等仍为接入位。
初中 `junior/grade7/shang/` 已有 7 科 170 题，学段「初中」在家长台已可选；
其源文件为标准化试卷格式（题型 `single_choice / cloze / reading / blank / multi_choice / ...`），
贡献新题库时按 `tools/import_subject_banks.py` 的转换规则生成 4 选 1 即可。

### 接入初中 / 高中多科目

```
junior/grade7/shang/{chinese,math,english,biology,geography,politics}.json
senior/grade10/shang/{math,physics,chemistry,biology,history,geography,politics}.json
```

- 学段目录名必须是 `primary` / `junior` / `senior`；
- 年级 id 建议 `grade7`…`grade12`（数字用于排序，也用于无 meta 时自动推断学段）；
- 每个文件 `meta` 写全 `stage` / `grade_label` / `term` / `subject_label` / `edition`；
- 放好后家长台「初中 / 高中」自动可选，年级、册别、科目自动生成，**无需改 Kotlin 代码**；
- 若某科在枚举里没有（如「通用技术」），写 `"subject_label": "通用技术"` 即可，`subject_key` 可选。

### 加一个完全自定义学段（如大学 / 幼儿）

`Stage` 枚举目前只有小学/初中/高中三档；若需第四档，在 `core/Models.kt` 的 `Stage` 里加一项并把
`Stage.ordered()` 包含它即可，其余逻辑（Catalog / UI / 持久化）全部通用，无需改动。

---

## 六、校验清单（提交 PR 前自查）

- [ ] 文件是合法 UTF-8 JSON，`json_decode` / `jq empty` 能解析
- [ ] `meta` 在文件开头 4096 字符内，且没有重复的 key
- [ ] 每道题 `type` 为 `choice`、`options` ≥ 2、`answerIndex` 在 `[0, options.length)` 内
- [ ] 文件放在 `assets/banks/` 下，扩展名 `.json`
- [ ] 同年级同册同科目的多个文件会被合并，注意 `id` 不要重复
- [ ] 题库来源、生成方式与授权已确认（不含教材原题）

本地快速校验（本机有 PHP 时）：

```bash
php -r '$j=json_decode(file_get_contents("app/src/main/assets/banks/primary/grade2/shang/math.json"),true);
if(!$j){exit("JSON 解析失败\n");}
$ok=0;$skip=0;
foreach($j["questions"] as $q){
  if(($q["type"]??"choice")!=="choice"||count($q["options"]??[])<2){$skip++;continue;}
  $a=$q["answerIndex"]??-1;
  if($a<0||$a>=count($q["options"])){$skip++;continue;}
  $ok++;
}
echo "可用 $ok 题，跳过 $skip 题\n";'
```

---

## 七、FAQ

**Q：为什么我放了文件，家长台还是置灰？**
A：确认扩展名是 `.json`、放在 `assets/banks/` 下、JSON 合法；改完需重新编译安装（assets 打包进 APK，不热更新）。

**Q：`meta.grade` 写成「二年级上册」会怎样？**
A：这是旧字段，引擎会用它推断册别并显示年级名，但会与册别行重复显示；建议改用
`grade_label: "二年级"` + `term: "shang"`。

**Q：一个年级可以既分上下册又有全册通用题吗？**
A：可以。放 `all/` 目录（或 `term: "all"`）的文件在选上册、下册、全部时都会被并入。

**Q：题库很大（上万题）会卡启动吗？**
A：不会。启动时只读每个文件头 4KB 建索引；完整解析在后台线程按「当前选中范围」按需进行，
并按文件路径缓存，切换范围不重复解析。解析完成前用内置 24 题兜底。

**Q：题目里的 `subject` 和 meta 里的 `subject` 不一致？**
A：单题 `subject` 可覆盖文件归类（仅已知科目生效），用于混合科目文件；未知值会沿用文件归类。

---

## 八、开源与版权提醒

- 本仓库公开分发的题库仅 `primary/grade2/shang/math.json` 一份（10586 题），主体为按运算规则穷举得到的算式组合，属通用数表 / 公式级事实数据，不含任何教材或教辅中的原题、题干文字、插图或章节编排。章节名、年级 / 册别、教材版本字段仅作知识点索引与难度分级，不表示与任何出版社教材存在关联或配套关系；本项目为非官方产品。完整说明见根目录 README 与 `app/src/main/assets/banks/README.md`。
- 语文 / 英语 / 初中等外部题库及 `sources/` 原始文件均按 `.gitignore` 排除，未随源码分发；若自行引入，请先确认数据授权，并在 PR 中注明来源与授权方式。
- 贡献题库时请在 PR 中注明题目来源、生成方式、适用知识点范围与授权方式，并确认不含教材原题。
- 仓库已附带 `LICENSE`（Apache-2.0）；源码与随附数据均按该许可发布。
