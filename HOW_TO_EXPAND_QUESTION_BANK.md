# 用 AI 扩展题库：按自己的年级资料自建

> 本仓库**只随源码提供一份示例题库**：`app/src/main/assets/banks/primary/grade2/shang/math.json`
> （二年级上册数学，10586 题，按课标知识点穷举生成的算式组合）。
> 其它年级、其它科目**不随仓库分发**，请按本文用 AI 自行生成后放进 App。
> 完整字段规范见 [QUESTION_BANK_FORMAT.md](QUESTION_BANK_FORMAT.md)。

## 一、先划红线（生成前必读）

- **不要把教材 / 教辅的原题整段粘贴给 AI** 让它复述、改写或"扩写"。只输入**知识点名称、课标要求、运算规则、数值范围**这类事实信息。
- 算式类题目（如 100 以内加减法）可让 AI 按规则穷举组合，属通用数表 / 公式级事实数据。
- 文字题与解析让 AI 自拟常规教学用语，不要复制教材题干、插图或章节编排。
- `edition`、章节名、"年级 / 册别"字段只是知识点索引与难度分级，不要写成与任何出版社教材配套或官方产品。
- AI 会算错：**导入前必须抽检答案**，尤其是进位 / 退位、单位换算。
- 生成的数据若要再次公开分发，授权由你自行确认；本仓库 Apache-2.0 只覆盖随源码内容。

## 二、准备材料（一个年级一份清单）

按年级整理一张知识点清单，只需要这些列：

| 年级 / 册别 | 科目 | 知识点 | 允许题型 | 数值或范围 | 目标题量 |
|---|---|---|---|---|---|
| 二年级上册 | 数学 | 100 以内加减法 | 4 选 1 算式 | 两位数 ± 两位数 | 3000 |
| 三年级下册 | 数学 | 两位数乘两位数 | 4 选 1 算式 | 10~99 × 10~99 | 1000 |
| 五年级上册 | 语文 | 多音字 | 4 选 1 | 常用多音字 | 200 |

知识点清单来自你自己的资料（课标、教材目录、教学大纲都行），**清单本身不是原题**。

## 三、决定文件放哪（四级 Catalog）

```
app/src/main/assets/banks/<stage>/<grade_id>/<term>/<subject>.json
```

| 段 | 取值 | 例子 |
|---|---|---|
| `stage` | `primary` / `junior` / `senior` | `primary` |
| `grade_id` | `grade1` ~ `grade12` | `grade3` |
| `term` | `shang` / `xia` / `all` | `xia` |
| `subject` | `math` / `chinese` / `english` / 自定义 | `math` |

例：`三年级下册数学` → `app/src/main/assets/banks/primary/grade3/xia/math.json`

App 启动时自动扫描 `banks/` 下所有 `*.json`，放进去**零改代码**；不加载题库时会回退到代码内置兜底题。

## 四、JSON 长什么样（最小可用）

```json
{
  "meta": {
    "stage": "primary",
    "grade_id": "grade3",
    "grade_label": "三年级",
    "term": "xia",
    "subject": "MATH",
    "subject_label": "数学",
    "edition": "通用（课标知识点）",
    "total": 1
  },
  "questions": [
    {
      "id": "m001",
      "type": "choice",
      "chapter": "两位数乘两位数",
      "text": "12×13=（ ）",
      "options": ["146", "156", "166", "126"],
      "answerIndex": 1,
      "explanation": "12×13=12×10+12×3=120+36=156。"
    }
  ]
}
```

必填：`text`、`options`（≥2）、`answerIndex`（**从 0 起**）。
`type` 只认 `choice`；`fill`、多选、简答会被跳过。
`chapter`、`explanation`、`label` 等可省略，引擎忽略未知字段。
`meta` 全部可选，但建议至少写 `grade_label` 与 `subject`，且必须位于文件开头 4096 字符内、key 不重复。

## 五、直接复制给 AI 的提示词

把 `{...}` 换成你自己的内容，整段发给任意 AI 工具（WorkBuddy / ChatGPT / Claude / Cursor / 通义等）：

```
你是一个题库生成助手。请严格按下面的要求输出一个 JSON 文件，不要输出任何解释文字、不要用 markdown 代码块包裹。

【范围】学段 {primary} / 年级 {grade3} / 册别 {xia} / 科目 {数学}
【知识点清单】（只含知识点名称与数值范围，不含任何教材原题）
{粘贴你的知识点清单，例如：两位数乘两位数，10~99 × 10~99，目标 1000 题}
【题量】{1000} 题

【硬性规则】
1. 只输出一个合法 JSON 对象，顶层为 {"meta": {...}, "questions": [...]}。
2. meta 字段：stage / grade_id / grade_label / term / subject / subject_label / edition / total。
   edition 固定写 "通用（课标知识点）"；total 等于实际题目数。
3. 每题字段：id（唯一，如 m001）、type（固定 "choice"）、chapter（知识点名）、text、
   options（字符串数组，恰好 4 个选项）、answerIndex（正确选项下标，从 0 起）、
   explanation（一句话解析，常规教学用语）。
4. 题干与选项必须自拟，不得复制或改写任何教材、教辅中的原题文字；算式按给定数值范围穷举或随机生成。
5. 4 个选项中正确答案位置大致均匀，干扰项应是常见计算错误，不要出现明显荒谬的数值。
6. 解析中的计算结果必须自洽；生成后自行逐题复算一遍再输出。
7. id 不重复；不要出现重复的题干；JSON 必须能被标准解析器解析（无尾逗号、无注释）。

【自检】输出前逐条确认：题目数 = total、answerIndex 均在 0~3、每题 options 恰好 4 个、
无教材原文、JSON 合法。确认无误后只输出 JSON。
```

> 题量大时**按知识点分批生成**（每批 200~500 题），分别保存后合并 `questions` 数组，
> 避免 AI 上下文截断导致后半段质量下降或 JSON 不完整。

## 六、校验与导入

把生成的文件存到对应路径后，先跑一次校验（本机有 Python 即可）：

```bash
python -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); qs=d.get('questions',d) if isinstance(d,dict) else d; bad=[q for q in qs if q.get('type','choice')!='choice' or len(q.get('options',[]))<2 or not isinstance(q.get('answerIndex'),int) or not (0<=q['answerIndex']<len(q['options']))]; ids=[q.get('id') for q in qs if q.get('id')]; print('题目数',len(qs),'不合规',len(bad),'重复id',len(ids)-len(set(ids)),'题干重复',len(qs)-len({q.get('text') for q in qs}))" "app/src/main/assets/banks/primary/grade3/xia/math.json"
```

期望：`不合规 0`、`重复id 0`、`题干重复 0`。然后：

1. 把文件放到 `app/src/main/assets/banks/<stage>/<grade_id>/<term>/<subject>.json`
2. 重新构建 / 安装 APK
3. 家长台 → 出题范围选到对应年级 / 册别 / 科目，确认题数统计正确
4. 抽检 10~20 题的答案与解析

## 七、常见坑

| 现象 | 原因 |
|---|---|
| 题目没加载 | `type` 不是 `choice`；`options` 少于 2 个；`answerIndex` 越界 |
| 家长台看不到年级 | 目录名不符合 `<stage>/<grade_id>/<term>/`；或 `meta` 没写在文件开头 4096 字符内 |
| 科目显示不对 | 补 `meta.subject_label` |
| 同范围题目变少 | 同年级同册同科目多个文件的 `id` 冲突，合并时去重 |
| 答题后没有解析 | 该题缺少 `explanation` |

更完整的字段表、组卷规则、兼容旧布局见 [QUESTION_BANK_FORMAT.md](QUESTION_BANK_FORMAT.md)。

## 八、历史脚本

`tools/` 下的 `import_*.py` 是早期把外部资料库文件转成四级 Catalog 的历史脚本，引用的是当时的原始文件名。
**新流程不需要它们**；如果你手上有自己的结构化源数据，可以照它们改写成自己的转换脚本。
