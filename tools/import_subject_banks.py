# -*- coding: utf-8 -*-
"""导入 v1.0.11 新增题库：小学 1/3/4/5/6 年级上册数学 + 初中七年级上册 7 科。

可复现导入（工程根目录执行）：
    python tools/import_subject_banks.py sources app/src/main/assets/banks

源文件分两套格式：
  A) 小学「通用（课标知识点）」：type = choice / fill，字段 text/options/answerIndex/answer/explanation。
  B) 初中「标准化试卷」(schemaVersion)：type = single_choice / cloze / reading / experiment /
     blank / multi_choice / short_answer / essay / graph / calculation / proof，
     answer 是对象，部分题带 passage（阅读材料）与 assets（占位图）。

App 只支持 4 选 1 选择题，因此：
  - 带合法 options + answerIndex 的题直接保留（含 single_choice / cloze / reading / experiment）；
  - blank 填空题转 4 选 1：正确答案取 answer.final，干扰项按「同答案类型」从
    同文件选项 / 同文件填空答案 / 全部初中填空答案 分层挑选；
  - multi_choice（多选）、short_answer / essay / graph / calculation / proof（主观题、
    识图题、计算证明题）跳过——这些题型需要文字输入或图片，App 界面无法作答；
  - 带 passage 的题把材料拼到题干前，避免「How old is Anna?」没有原文无法作答。
  - 凑不齐 3 个合法干扰项就跳过，绝不生成有歧义的选项。
"""
import collections
import json
import os
import random
import re
import sys

HANZI_RE = re.compile(r"[\u4e00-\u9fff]")
SOURCE_DIR_NOTE = "资料库提供的题库原始数据"

PRIMARY_MATH = [
    ("primary_g1_shang_math.json", "grade1", "一年级"),
    ("primary_g3_shang_math.json", "grade3", "三年级"),
    ("primary_g4_shang_math.json", "grade4", "四年级"),
    ("primary_g5_shang_math.json", "grade5", "五年级"),
    ("primary_g6_shang_math.json", "grade6", "六年级"),
]

JUNIOR_G7 = [
    ("junior_g7_shang_chinese.json", "chinese", "CHINESE", "语文"),
    ("junior_g7_shang_math.json", "math", "MATH", "数学"),
    ("junior_g7_shang_english.json", "english", "ENGLISH", "英语"),
    ("junior_g7_shang_politics.json", "politics", "POLITICS", "道德与法治"),
    ("junior_g7_shang_history.json", "history", "HISTORY", "历史"),
    ("junior_g7_shang_geography.json", "geography", "GEOGRAPHY", "地理"),
    ("junior_g7_shang_biology.json", "biology", "BIOLOGY", "生物"),
]


def answer_kind(a):
    a = a.strip()
    if re.fullmatch(r"[-+]?\d+(\.\d+)?", a) or a in {"<", ">", "=", "≤", "≥", "≠", "+"}:
        return "number"
    if HANZI_RE.search(a):
        return "hanzi"
    if re.search(r"[A-Za-z]", a):
        return "latin"
    return "other"


def usable(c, ans):
    return c != ans and ans not in c and c not in ans


def pick(ans, levels, rng):
    """levels：按优先级排列的候选答案池（列表），逐层补足 3 个干扰项。"""
    picked = []
    for pool in levels:
        if len(picked) >= 3:
            break
        cand = [c for c in pool if usable(c, ans) and c not in picked]
        rng.shuffle(cand)
        picked += cand[: 3 - len(picked)]
    if len(picked) < 3:
        return None
    options = picked + [ans]
    rng.shuffle(options)
    return options, options.index(ans)


def base_fields(q, subject, text):
    return {
        "id": q.get("id", ""),
        "type": "choice",
        "subject": subject,
        "text": text,
        "explanation": (q.get("explanation") or "").strip(),
        "grade": q.get("grade", ""),
        "volume": q.get("volume", ""),
        "chapter": q.get("chapter", ""),
        "label": q.get("label", ""),
    }


def with_passage(q):
    text = (q.get("text") or "").strip()
    passage = (q.get("passage") or "").strip()
    if passage:
        return "【材料】" + passage + "\n\n" + text
    return text


def valid_choice(item):
    o = item["options"]
    return (
        len(o) >= 2 and len(set(o)) == len(o)
        and 0 <= item["answerIndex"] < len(o)
        and item["text"].strip() and item["explanation"].strip()
    )


def convert_primary(path, rng):
    """返回 (items, 源文件 meta, 统计 dict)"""
    d = json.load(open(path, encoding="utf-8-sig"))
    meta = d.get("meta", {})
    qs = d.get("questions", d if isinstance(d, list) else [])
    items, skipped = [], 0

    # 干扰项池：同册所有选项 + 同册填空答案，按答案类型分组
    pools = collections.defaultdict(list)
    for q in qs:
        if q.get("type") == "choice" and q.get("options") and isinstance(q.get("answerIndex"), int):
            for o in q["options"]:
                s = str(o).strip()
                if s and s not in pools[answer_kind(s)]:
                    pools[answer_kind(s)].append(s)
        elif q.get("type") == "fill" and q.get("answer"):
            s = str(q["answer"][0]).strip()
            if s and s not in pools[answer_kind(s)]:
                pools[answer_kind(s)].append(s)

    for q in qs:
        typ = q.get("type")
        if typ == "choice" and isinstance(q.get("answerIndex"), int):
            item = base_fields(q, "MATH", q["text"].strip())
            item["options"] = [str(o).strip() for o in q["options"]]
            item["answerIndex"] = q["answerIndex"]
            if valid_choice(item):
                items.append(item)
            else:
                skipped += 1
        elif typ == "fill" and q.get("answer"):
            ans = str(q["answer"][0]).strip()
            got = pick(ans, [pools[answer_kind(ans)]], rng)
            if not got:
                skipped += 1
                continue
            item = base_fields(q, "MATH", q["text"].strip())
            item["options"], item["answerIndex"] = got
            item["origin"] = "fill_to_choice"
            items.append(item)
        else:
            skipped += 1
    return items, meta, {"source_total": len(qs), "converted": sum(
        1 for x in items if x.get("origin") == "fill_to_choice"), "skipped": skipped}


def blank_answer(q):
    a = q.get("answer")
    if not isinstance(a, dict) or a.get("kind") != "blank":
        return None
    final = str(a.get("final") or "").strip()
    if final and "；" not in final and len(final) <= 30:
        return final
    blanks = a.get("blanks") or []
    if blanks and isinstance(blanks[0], dict):
        acc = blanks[0].get("accept") or []
        if acc:
            return str(acc[0]).strip()
    return final[:30] if final else None


def convert_junior(path, subject, rng, global_blank_pools):
    d = json.load(open(path, encoding="utf-8-sig"))
    meta = d.get("meta", {})
    qs = d.get("questions", [])
    items, skipped = [], 0

    # 同文件候选池：所有选择题选项 + 本文件填空答案，按答案类型分组
    local_pools = collections.defaultdict(list)
    for q in qs:
        if q.get("options"):
            for o in q["options"]:
                s = str(o).strip()
                if s and s not in local_pools[answer_kind(s)]:
                    local_pools[answer_kind(s)].append(s)
        if q.get("type") == "blank":
            ans = blank_answer(q)
            if ans and ans not in local_pools[answer_kind(ans)]:
                local_pools[answer_kind(ans)].append(ans)

    for q in qs:
        typ = q.get("type")
        idx = q.get("answerIndex")
        opts = q.get("options")
        if isinstance(idx, int) and isinstance(opts, list) and len(opts) >= 2:
            # 多选（answer.value 为数组）跳过：App 只支持单选
            av = (q.get("answer") or {}).get("value") if isinstance(q.get("answer"), dict) else None
            if isinstance(av, list):
                skipped += 1
                continue
            item = base_fields(q, subject, with_passage(q))
            item["options"] = [str(o).strip() for o in opts]
            item["answerIndex"] = idx
            if q.get("type") != "single_choice":
                item["origin"] = "objective_to_choice"
            if valid_choice(item):
                items.append(item)
            else:
                skipped += 1
            continue

        if typ == "blank":
            ans = blank_answer(q)
            if not ans:
                skipped += 1
                continue
            k = answer_kind(ans)
            got = pick(ans, [local_pools[k], global_blank_pools[k]], rng)
            if not got:
                skipped += 1
                continue
            item = base_fields(q, subject, with_passage(q))
            item["options"], item["answerIndex"] = got
            item["origin"] = "blank_to_choice"
            items.append(item)
            continue
        skipped += 1
    return items, meta, {"source_total": len(qs), "converted": sum(
        1 for x in items if x.get("origin") == "blank_to_choice"), "skipped": skipped}


def write_bank(out_root, rel, meta, items):
    path = os.path.join(out_root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"meta": meta, "questions": items}, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    return path


def main():
    src_root, out_root = sys.argv[1], sys.argv[2]
    rng = random.Random(20261106)
    summary = []

    # 初中全局填空答案池（跨科同答案类型，供单科样本不足时补足）
    global_blank_pools = collections.defaultdict(list)
    for fn, _subj, _s, _l in JUNIOR_G7:
        d = json.load(open(os.path.join(src_root, fn), encoding="utf-8-sig"))
        for q in d.get("questions", []):
            if q.get("type") == "blank":
                ans = blank_answer(q)
                if ans and ans not in global_blank_pools[answer_kind(ans)]:
                    global_blank_pools[answer_kind(ans)].append(ans)

    # A) 小学数学 5 册
    for fn, grade_id, grade_label in PRIMARY_MATH:
        path = os.path.join(src_root, fn)
        items, src_meta, st = convert_primary(path, rng)
        meta = {
            "stage": "primary", "grade_id": grade_id, "grade_label": grade_label,
            "term": "shang", "subject": "MATH", "subject_label": "数学",
            "edition": src_meta.get("edition", "通用（课标知识点）"),
            "total": len(items),
            "source": SOURCE_DIR_NOTE + "：「%s」" % src_meta.get("grade", grade_label + "上册") + "数学题库",
            "note": "原题库 %d 题，其中 %d 道填空已转为 4 选 1（origin=fill_to_choice）；"
                    "跳过 %d 道格式不合法题。题目数据为通用课标知识点整理，请对照实际教材核验。"
                    % (st["source_total"], st["converted"], st["skipped"]),
        }
        rel = os.path.join("primary", grade_id, "shang", "math.json")
        write_bank(out_root, rel, meta, items)
        summary.append((rel, st["source_total"], len(items), st["converted"], st["skipped"]))

    # B) 初中七年级上册 7 科
    for fn, filename, subject, label in JUNIOR_G7:
        path = os.path.join(src_root, fn)
        items, src_meta, st = convert_junior(path, subject, rng, global_blank_pools)
        meta = {
            "stage": "junior", "grade_id": "grade7", "grade_label": "七年级",
            "term": "shang", "subject": subject, "subject_label": label,
            "edition": src_meta.get("edition", "通用（课标知识点）"),
            "total": len(items),
            "source": SOURCE_DIR_NOTE + "：「七年级上册%s题库」（标准化试卷格式）" % label,
            "note": "原试卷 %d 题；保留单选/完形/阅读/实验等客观题 %d 道，%d 道填空转为 4 选 1"
                    "（origin=blank_to_choice），跳过 %d 道多选/简答/作文/识图/计算/证明题"
                    "（需文字输入或图片，App 暂不支持）。带 passage 的题已把材料拼入题干。"
                    % (st["source_total"], len(items) - st["converted"], st["converted"], st["skipped"]),
        }
        rel = os.path.join("junior", "grade7", "shang", filename + ".json")
        write_bank(out_root, rel, meta, items)
        summary.append((rel, st["source_total"], len(items), st["converted"], st["skipped"]))

    print("file | source | usable | converted_fill | skipped")
    for row in summary:
        print(" | ".join(str(x) for x in row))
    print("TOTAL usable=%d source=%d skipped=%d" % (
        sum(r[2] for r in summary), sum(r[1] for r in summary), sum(r[4] for r in summary)))


if __name__ == "__main__":
    main()
