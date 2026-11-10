# 题库目录（开源说明）

本应用**不随源码分发任何预置教材题库**。仓库内仅保留：

- 本文件与 `CATALOG.md`（格式 / 目录约定说明）
- 根级 `app/src/main/assets/questions.json`（28 道自编示例题，仅用于让开源版本开箱即有题可出、便于演示）

## 如何自行添加题库

按 [QUESTION_BANK_FORMAT.md](../../../../QUESTION_BANK_FORMAT.md) 的规范，把题库 JSON 放到：

```
app/src/main/assets/banks/<学段>/<年级>/<册别>/<科目>.json
```

例如：

```
app/src/main/assets/banks/primary/grade2/shang/math.json
app/src/main/assets/banks/junior/grade7/shang/biology.json
```

App 启动时会自动扫描 `banks/` 下所有 `*.json` 并加载（`.md` 文件被忽略）。

> ⚠️ 版权提醒：人教版（PEP / 部编版）等教材题目本身受人民教育出版社版权保护，
> 请勿在公开仓库中分发未经授权的教材原题。请仅放入你拥有合法授权或自编的题目。
