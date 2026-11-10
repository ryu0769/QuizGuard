# -*- coding: utf-8 -*-
"""把「对应教材版本一至六年级英语题库.json」拆成 QuizGuard 四级 Catalog 题库。

可复现导入（在工程根目录执行）：
    python tools/import_english_bank.py sources/english_grade1-6_source.json app/src/main/assets/banks

源文件 1132 题 = 810 道 choice + 322 道 fill（词义填空）。
App 只支持选择题，因此把 fill 转成 4 选 1：
  正确答案 = answer[0]；干扰项从同一「年级+册别」的其它 fill 答案中取，
  排除与正确答案相同 / 互相包含的字符串，不足再从全库 fill 答案补；
  固定随机种子，转换结果可复现。原题解析完整保留，用于答错后的解题思路。
"""
import json
import os
import random
import sys

GRADES = {
    "一年级": "grade1", "二年级": "grade2", "三年级": "grade3",
    "四年级": "grade4", "五年级": "grade5", "六年级": "grade6",
    "综合": "shared",
}
TERMS = {"上册": ("shang", "shang"), "下册": ("xia", "xia"), "—": ("all", "all")}
EDITION = "一年级起点体系"
SOURCE = "对应教材版本一至六年级英语题库（资料库提供）"
SHARED_LABEL = "综合（跨年级）"  # 与语文题库共用同一个 grade_id=shared，显示名必须一致


def key_of(q):
    grade = GRADES.get(q.get("grade"))
    if not grade:
        raise ValueError("未知年级: " + repr(q.get("grade")))
    term_dir, term_id = TERMS.get(q.get("volume"), (None, None))
    if not term_dir:
        raise ValueError("未知册别: " + repr(q.get("volume")))
    return grade, term_dir, term_id


def base_fields(q):
    return {
        "id": q["id"],
        "type": "choice",
        "subject": "ENGLISH",
        "text": q["text"],
        "explanation": q.get("explanation", ""),
        "grade": q.get("grade", ""),
        "volume": q.get("volume", ""),
        "level": q.get("level", ""),
        "chapter": q.get("chapter", ""),
        "label": q.get("label", ""),
    }


def main():
    src, out_root = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8-sig") as f:
        questions = json.load(f)["questions"]

    rng = random.Random(20261105)
    # 全库 fill 答案池（去重，保持顺序稳定）
    all_fill_answers = []
    for q in questions:
        if q.get("type") == "fill":
            a = str(q["answer"][0]).strip()
            if a and a not in all_fill_answers:
                all_fill_answers.append(a)

    groups = {}
    for q in questions:
        k = key_of(q)
        groups.setdefault(k, {"choice": [], "fill": []})[
            "choice" if q.get("type") == "choice" else "fill"
        ].append(q)

    summary = []
    for (grade_id, term_dir, term_id), g in sorted(groups.items()):
        items = []
        for q in g["choice"]:
            item = base_fields(q)
            item["options"] = q["options"]
            item["answerIndex"] = q["answerIndex"]
            items.append(item)

        local_answers = []
        for q in g["fill"]:
            a = str(q["answer"][0]).strip()
            if a and a not in local_answers:
                local_answers.append(a)

        converted = 0
        for q in g["fill"]:
            ans = str(q["answer"][0]).strip()

            def usable(c):
                return c != ans and ans not in c and c not in ans

            pool = [c for c in local_answers if usable(c)]
            rng.shuffle(pool)
            picked = pool[:3]
            if len(picked) < 3:
                extra = [c for c in all_fill_answers
                         if usable(c) and c not in picked]
                rng.shuffle(extra)
                picked += extra[: 3 - len(picked)]
            if len(picked) < 3:
                # 极端情况：凑不齐就跳过这道填空题，绝不生成有歧义的选项
                continue
            options = picked + [ans]
            rng.shuffle(options)
            item = base_fields(q)
            item["options"] = options
            item["answerIndex"] = options.index(ans)
            item["origin"] = "fill_to_choice"
            items.append(item)
            converted += 1

        grade_label = [k for k, v in GRADES.items() if v == grade_id][0]
        if grade_id == "shared":
            grade_label = SHARED_LABEL
        term_label = {"shang": "上册", "xia": "下册", "all": "全册"}[term_id]
        meta = {
            "stage": "primary",
            "grade_id": grade_id,
            "grade_label": grade_label,
            "term": term_id,
            "subject": "ENGLISH",
            "subject_label": "英语",
            "edition": EDITION,
            "total": len(items),
            "source": SOURCE,
            "note": ("原题库含 choice / fill 两类；%d 道词义填空已按 App 选择题格式"
                     "转为 4 选 1（origin=fill_to_choice），干扰项取自同册其它词义。"
                     % converted) if converted else "原题库选择题，兼容 QuizGuard 选择题格式。",
        }
        rel = os.path.join("primary", grade_id, term_dir, "english.json")
        path = os.path.join(out_root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"meta": meta, "questions": items}, f,
                      ensure_ascii=False, indent=2)
            f.write("\n")
        summary.append((rel, g["choice"].__len__(), converted, len(items)))

    print("file | choice | fill_converted | total")
    for row in summary:
        print(" | ".join(str(x) for x in row))
    print("TOTAL choice=%d fill_converted=%d all=%d" % (
        sum(r[1] for r in summary), sum(r[2] for r in summary),
        sum(r[3] for r in summary)))


if __name__ == "__main__":
    main()
