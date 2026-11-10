# 题库目录（开源说明）

本仓库随源码只提供**一份示例题库**：`primary/grade2/shang/math.json`（二年级上册数学，10586 题）。
题目主体为按课标知识点与运算规则穷举得到的算式组合，不含教材原题。

## 如何添加自己的题库

1. 按自己的年级资料用 AI 生成题库 → [用 AI 扩展题库：按自己的年级资料自建](../../../../HOW_TO_EXPAND_QUESTION_BANK.md)
2. 字段规范见 [QUESTION_BANK_FORMAT.md](../../../../QUESTION_BANK_FORMAT.md) 与 [CATALOG.md](CATALOG.md)
3. 把生成的 JSON 放到 `app/src/main/assets/banks/<学段>/<年级>/<册别>/<科目>.json`，例如：

```
app/src/main/assets/banks/primary/grade2/shang/math.json
app/src/main/assets/banks/primary/grade3/xia/math.json
```

App 启动时自动扫描 `banks/` 下所有 `*.json` 并加载（`.md` 文件被忽略），无需改代码。

## 题库来源与版权说明

- 公开的 `math.json` 共 10586 题，其中 **10506 题为纯算式**（如 `26+29=（ ）`），由 100 以内加减法、表内乘法等运算规则穷举得到，属通用数表 / 公式级事实数据；依据《著作权法》（2020 修正）第三条、第五条（三），此类内容不构成受本法保护的作品。
- 其余 80 道文字题与解题说明为常规教学用语，独创性有限；**未使用任何教材或教辅中的原题、题干文字、插图或章节编排**。
- `meta` 中的 `edition`、章节名（如「100以内的加减法」）、年级 / 册别字段，仅用于知识点索引与难度分级，不表示与任何出版社教材存在关联、配套或授权关系；本项目为非官方产品。
- 自行生成并导入的题库，其数据来源与授权由导入者自行负责。
- 本仓库整体以 Apache-2.0 许可发布（见根目录 `LICENSE`）。
