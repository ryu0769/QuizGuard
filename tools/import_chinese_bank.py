# -*- coding: utf-8 -*-
"""把「对应教材版本一至六年级语文题库.json」拆成 QuizGuard 四级 Catalog 题库。

可复现导入（在工程根目录执行）：
    python tools/import_chinese_bank.py sources/chinese_grade1-6_source.json app/src/main/assets/banks

源文件 3074 题 = 1599 道 choice + 1475 道 fill。
App 只支持选择题，因此把 fill 转成 4 选 1：
  正确答案 = answer[0]；干扰项按「同题型 + 同答案类型 + 尽量同年级同册」分层挑选，
  避免把「拼音音节 / 汉字词语 / 诗句 / 数字 / 字母」混进同一组选项导致一眼看穿。
  排除与正确答案相同、互相包含的字符串；凑不齐 3 个合法干扰项就跳过该题，
  绝不生成有歧义的选项。原题解析完整保留，用于答错后的解题思路。

答案类型：
  digit  纯数字（笔画数、声调序号等）
  letter 单个拉丁字母（字母表、大小写）
  hanzi  含汉字（词语、诗句、日积月累）
  pinyin 拼音 / 音节（含声调符号）
"""
import collections
import json
import os
import random
import re
import sys

GRADES = {
    "一年级": "grade1", "二年级": "grade2", "三年级": "grade3",
    "四年级": "grade4", "五年级": "grade5", "六年级": "grade6",
    "综合": "shared",
}
TERMS = {"上册": ("shang", "shang"), "下册": ("xia", "xia"), "—": ("all", "all")}
EDITION = "统编体系"
SOURCE = "对应教材版本一至六年级语文题库（资料库提供）"
SHARED_LABEL = "综合（跨年级）"

HANZI_RE = re.compile(r"[\u4e00-\u9fff]")
TONED_RE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜńňǹǖǘǚǜ]")


def key_of(q):
    grade = GRADES.get(q.get("grade"))
    if not grade:
        raise ValueError("未知年级: " + repr(q.get("grade")))
    term_dir, term_id = TERMS.get(q.get("volume"), (None, None))
    if not term_dir:
        raise ValueError("未知册别: " + repr(q.get("volume")))
    return grade, term_dir, term_id


def answer_kind(a):
    if re.fullmatch(r"\d+", a):
        return "digit"
    if re.fullmatch(r"[A-Za-z]", a):
        return "letter"
    if HANZI_RE.search(a):
        return "hanzi"
    if re.search(r"[A-Za-z]", a) or TONED_RE.search(a):
        return "pinyin"
    return "other"


def template(text):
    """题型模板：取中文冒号前的提示语，抹掉具体词例，让同类题共用答案池。"""
    head = text.split("：")[0] if "：" in text else text
    head = re.sub(r"「[^」]*」", "「X」", head)
    head = re.sub(r"第[一二三四1-4]声", "第N声", head)
    head = re.sub(r"\d+", "N", head)
    return head.strip()


def base_fields(q):
    return {
        "id": q["id"],
        "type": "choice",
        "subject": "CHINESE",
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
    with open(src, encoding="utf-8-sig") as fh:
        data = json.load(fh)
    questions = data["questions"]
    src_note = data.get("meta", {}).get("note", "")

    rng = random.Random(20261105)
    groups = collections.defaultdict(lambda: {"choice": [], "fill": []})
    for q in questions:
        g = groups[key_of(q)]
        g["choice" if q.get("type") == "choice" else "fill"].append(q)

    # 分层答案池：key -> 去重答案列表（保持源文件顺序，保证可复现）
    pools = collections.defaultdict(list)

    def add_pool(key, ans):
        if ans and ans not in pools[key]:
            pools[key].append(ans)

    for q in questions:
        if q.get("type") != "fill":
            continue
        ans = str(q["answer"][0]).strip()
        kind = answer_kind(ans)
        tpl = template(q["text"])
        grade, term_dir, _ = key_of(q)
        gv = (grade, term_dir)
        add_pool(("all", kind), ans)
        add_pool(("tpl", tpl, kind), ans)
        add_pool(("gv", gv, kind), ans)
        add_pool(("gv_tpl", gv, tpl, kind), ans)

    summary = []
    skipped = 0
    for (grade_id, term_dir, term_id), g in sorted(groups.items()):
        items = []
        for q in g["choice"]:
            item = base_fields(q)
            item["options"] = q["options"]
            item["answerIndex"] = q["answerIndex"]
            items.append(item)

        gv = (grade_id, term_dir)
        converted = 0
        for q in g["fill"]:
            ans = str(q["answer"][0]).strip()
            kind = answer_kind(ans)
            tpl = template(q["text"])

            def usable(c):
                return c != ans and ans not in c and c not in ans

            picked = []
            # 优先级：同册同题型 → 同册同类 → 全库同题型 → 全库同类
            levels = [
                ("gv_tpl", gv, tpl, kind),
                ("gv", gv, kind),
                ("tpl", tpl, kind),
                ("all", kind),
            ]
            for key in levels:
                if len(picked) >= 3:
                    break
                cand = [c for c in pools[key]
                        if usable(c) and c not in picked]
                rng.shuffle(cand)
                picked += cand[: 3 - len(picked)]
            if len(picked) < 3:
                skipped += 1
                continue
            options = picked + [ans]
            rng.shuffle(options)
            item = base_fields(q)
            item["options"] = options
            item["answerIndex"] = options.index(ans)
            item["origin"] = "fill_to_choice"
            items.append(item)
            converted += 1

        grade_label = SHARED_LABEL if grade_id == "shared" else \
            [k for k, v in GRADES.items() if v == grade_id][0]
        term_label = {"shang": "上册", "xia": "下册", "all": "全册"}[term_id]
        meta = {
            "stage": "primary",
            "grade_id": grade_id,
            "grade_label": grade_label,
            "term": term_id,
            "subject": "CHINESE",
            "subject_label": "语文",
            "edition": EDITION,
            "total": len(items),
            "source": SOURCE,
            "note": (
                "原题库含 choice / fill 两类；%d 道填空题已按「同题型 + 同答案类型」"
                "分组转为 4 选 1（origin=fill_to_choice），干扰项优先取同年级同册。"
                "题库为统编版基础版，古诗篇目与词语表请对照实际教材核验。" % converted
            ),
        }
        rel = os.path.join("primary", grade_id, term_dir, "chinese.json")
        path = os.path.join(out_root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump({"meta": meta, "questions": items}, fh,
                      ensure_ascii=False, indent=2)
            fh.write("\n")
        summary.append((rel, len(g["choice"]), converted, len(items)))

    print("file | choice | fill_converted | total")
    for row in summary:
        print(" | ".join(str(x) for x in row))
    print("TOTAL choice=%d fill_converted=%d all=%d skipped_fill=%d" % (
        sum(r[1] for r in summary), sum(r[2] for r in summary),
        sum(r[3] for r in summary), skipped))
    if src_note:
        print("SOURCE_NOTE:", src_note[:120], "...")


if __name__ == "__main__":
    main()
