# 题库目录 / 接入位说明

## 当前随源码提供的题库

| 文件 | 范围 | 题数 |
|---|---|---|
| `primary/grade2/shang/math.json` | 小学 · 二年级 · 上册 · 数学 | 10586（选择题 10564） |

题目主体为按课标知识点穷举生成的算式组合，不含教材原题。
来源与版权说明见根目录 README 与 [README.md](README.md)。

## 目录约定（四级 Catalog）

```
app/src/main/assets/banks/<stage>/<grade_id>/<term>/<subject>.json
```

| 段 | 取值 | 例子 |
|---|---|---|
| `stage` | `primary` / `junior` / `senior` | `primary` |
| `grade_id` | `grade1` ~ `grade12` | `grade3` |
| `term` | `shang` / `xia` / `all` | `xia` |
| `subject` | `math` / `chinese` / `english` / 自定义 | `math` |

同年级同册同科目的多个文件会被合并，注意 `id` 不要重复。
`meta` 建议写在文件开头 4096 字符内，至少包含 `grade_label` 与 `subject`（自定义科目另需 `subject_label`）。

## 后续接入位（放文件即可，零改代码）

任何年级 / 科目只要把 JSON 放进上述路径，App 启动时自动扫描加载，家长台的四级联动会自行识别，
无需改动 Kotlin 代码。不加载题库时 App 会用代码内置兜底题运行，界面不会白屏。

## 怎么拿到其它年级的题库

**本仓库不分发其它题库。** 请按自己的年级资料用 AI 生成：
[用 AI 扩展题库：按自己的年级资料自建](../../../../HOW_TO_EXPAND_QUESTION_BANK.md)。
完整字段表、组卷规则、兼容旧布局见 [QUESTION_BANK_FORMAT.md](../../../../QUESTION_BANK_FORMAT.md)。

## 题库来源与版权说明

- 公开的题库仅 `primary/grade2/shang/math.json` 一份：10586 题中 10506 题为按运算规则穷举得到的算式组合（通用数表 / 公式级事实数据），不含教材或教辅原题、题干文字、插图与章节编排。
- 章节名、年级 / 册别、教材版本字段仅作知识点索引与难度分级，不表示与任何出版社存在关联或配套关系。
- 自行生成并导入的题库，其数据来源与授权由导入者自行负责。
