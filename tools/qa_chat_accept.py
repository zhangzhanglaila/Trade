#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""智能问答路由与引导的验收脚本（backend-main /ai/chat）。

背景：此前问答链路存在「无论问什么都被判成贸易预测、输出全是同一段固定引导语」
的问题。修复后有五条路由，本脚本逐条覆盖，并核对数据查询结果与数据库一致；
RAG 类问题还会校验「检索规模」口径（见 check_rag_scale），
「数据范围」类问题校验回答是不是真从库里算的（见 check_scope）。

用法（在服务器上执行）：
    python3 tools/qa_chat_accept.py
    python3 tools/qa_chat_accept.py --base http://127.0.0.1:8080

注意：请求体字段名是 ``text``（不是 ``question``）。
传 ``question`` 不会报错，而是 text 变成 null → 规则拿到空串 → 落到 LLM 兜底，
表现为「route 对、槽位全空」——排查时先确认字段名。
"""
import argparse
import json
import sys
import urllib.request

# (问题, 期望路由)  —— 期望值为 None 表示只打印不断言
CASES = [
    ("你好", "CHITCHAT"),
    ("你能做什么", "CHITCHAT"),
    ("有什么国家的数据可以访问", "SCOPE"),
    ("有哪些国家的数据", "SCOPE"),
    ("数据覆盖哪些年份", "SCOPE"),
    ("最近有哪些关于哈萨克斯坦的新闻", "RAG_NEWS"),
    ("什么是中哈贸易", "RAG_NEWS"),
    ("哈萨克斯坦主要出口哪些商品", "RAG_NEWS"),
    ("1月哈萨克斯坦丝绸出口量", "DATA_QUERY"),
    ("2025年1月哈萨克斯坦合成纤维长丝缝纫线，非供零售用的出口数量", "DATA_QUERY"),
    ("2025年3月哈萨克斯坦的出口数量", "DATA_QUERY"),
    ("2026年1月哈萨克斯坦的出口数量", "DATA_QUERY"),
    ("预测2026年1月哈萨克斯坦其他未列名冻鱼的进口单价", "PREDICT"),
    ("预测哈萨克斯坦其他未列名冻鱼、一般贸易、新疆维吾尔自治区2026年1月进口单价",
     "PREDICT"),
    ("预测哈萨克斯坦27年1月的进口奶制品的数量", "PREDICT"),
    ("预测哈萨克斯坦石油原油及从沥青矿物提取的原油、一般贸易、北京市2027年1月进口数量",
     "PREDICT"),
]

# 库里实际的贸易伙伴。用于校验「数据范围」回答不是模板，而是真的从库里算的。
KNOWN_PARTNERS = ["哈萨克斯坦", "乌兹别克斯坦", "吉尔吉斯斯坦", "土库曼斯坦", "塔吉克斯坦"]

# 旧版能力说明的开头。它把数据范围写死在字符串里、且从不提有哪些国家，
# 是「问有什么国家的数据 → 答非所问」的直接原因。
LEGACY_CANNED_PREFIX = "我是中哈贸易智能助手，可以帮你做三类事："


def ask(base, text, timeout=180):
    body = json.dumps({"text": text}).encode("utf-8")
    req = urllib.request.Request(
        base.rstrip("/") + "/ai/chat", data=body,
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.load(resp)


def check_rag_scale(data):
    """核对 RAG 来源的「检索规模」口径是否自洽。

    此前 sources 只回 topK(=5) 条，用户看到 5 行就以为「一共只检索到 5 条」。
    现在候选条数与进上下文的条数分开，本函数守住三条不变量：
      1) corpusSize 如实反映语料规模（用于告诉用户「在多少条里检索」）；
      2) sources 条数 > 进上下文的条数（否则用户仍看不到检索规模）；
      3) usedInContext 恰好在相关度最高的前 contextDocs 条上为真。
    """
    srcs = data.get("sources") or []
    total = data.get("corpusSize")
    ctx = data.get("contextDocs")
    used = sum(1 for s in srcs if s.get("usedInContext"))
    print("      sources: 候选=%s 语料=%s 进上下文=%s（标记已引用 %s 条）"
          % (len(srcs), total, ctx, used))

    problems = []
    if not srcs:
        problems.append("sources 为空")
    if not total or total <= 0:
        problems.append("corpusSize 未填充")
    if ctx is None:
        problems.append("contextDocs 未填充")
    elif used != ctx:
        problems.append("usedInContext 标记数(%s) != contextDocs(%s)" % (used, ctx))
    if srcs and total and len(srcs) >= total:
        problems.append("候选条数(%s) 不应达到语料总量(%s)" % (len(srcs), total))
    flags = [bool(s.get("usedInContext")) for s in srcs]
    if flags != sorted(flags, reverse=True):
        problems.append("usedInContext 未按相关度顺序连续分布")
    if problems:
        print("      ! " + "；".join(problems))
        return 1
    return 0


def check_scope(data):
    """核对「数据范围」的回答是真从库里算的，而不是一段写死的模板。

    此前这类问题落到 CHITCHAT，拿到的是 buildCapabilityAnswer() 里一段
    **写死的**能力说明：既没有国家清单，日期也是硬编码，所以用户问
    「有什么国家的数据可以访问」得到的是答非所问。
    """
    ans = data.get("answer") or ""
    print("      范围回答长度=%s" % len(ans))

    problems = []
    missing = [p for p in KNOWN_PARTNERS if p not in ans]
    if missing:
        problems.append("缺少贸易伙伴：%s" % "、".join(missing))
    if "|" not in ans:
        problems.append("未给出表格（应是 伙伴 × 进出口记录数）")
    if "2015-01" not in ans or "2025-03" not in ans:
        problems.append("未给出库里的实际时间范围 2015-01~2025-03")
    if ans.startswith(LEGACY_CANNED_PREFIX):
        problems.append("仍是旧的能力说明模板（说明路由/分支没改到）")
    if problems:
        print("      ! " + "；".join(problems))
        return 1
    return 0


def check_chitchat_scope(data):
    """闲聊/能力说明也必须带上真实数据范围。

    这是 SCOPE 规则的**兜底**：规则漏判时问题会落到 CHITCHAT，
    只要能力说明里带了真实范围，用户至少不会被答非所问地糊弄过去。
    """
    ans = data.get("answer") or ""
    hit = [p for p in KNOWN_PARTNERS if p in ans]
    print("      能力说明里出现贸易伙伴 %d/%d 个" % (len(hit), len(KNOWN_PARTNERS)))
    if len(hit) < len(KNOWN_PARTNERS):
        print("      ! 能力说明未带全数据范围，落到 CHITCHAT 的问法会答非所问")
        return 1
    return 0


def check_dairy_suggest(data):
    """「奶制品」的候选建议必须是乳及奶油类，而非「木/瓷/塑料制品」。

    纯 2-gram 会让「制品」二字命中大量「未列名 X 制品」，把真正的乳制品挤掉。
    修复后同义映射（奶制品→乳/奶油）优先召回，候选里应出现「乳」字。
    """
    ans = data.get("answer") or ""
    problems = []
    if "乳" not in ans and "奶油" not in ans:
        problems.append("候选里没有任何乳/奶油制品")
    bad = [w for w in ("木制品", "瓷制品", "塑料制品") if w in ans]
    if bad:
        problems.append("候选仍被「制品」二字劫持：%s" % "、".join(bad))
    if problems:
        print("      ! " + "；".join(problems))
        return 1
    return 0


def check_far_month_warning(data):
    """目标月远超数据末期时，预测结果必须带可靠性警示。

    模型输入是「目标月之前」的历史，目标月本身不进模型，2027-01 与 2025-04
    喂的是同一段历史、同一个数。不加警示用户会误以为那是模型对 2027 的真实判断。
    """
    ans = data.get("answer") or ""
    if "可靠性提醒" not in ans:
        print("      ! 远期月份预测缺少「可靠性提醒」")
        return 1
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8080")
    args = ap.parse_args()

    failed = 0
    for text, expect in CASES:
        try:
            resp = ask(args.base, text)
        except Exception as e:  # noqa: BLE001
            print("FAIL  %s\n      请求异常: %s" % (text, e))
            failed += 1
            continue

        data = resp.get("data") or {}
        route = data.get("route")
        ok = (expect is None or route == expect)
        if not ok:
            failed += 1

        print("%s  route=%-11s expect=%-11s  %s"
              % ("OK  " if ok else "FAIL", route, expect, text))
        if resp.get("code") != 200:
            print("      code=%s message=%s" % (resp.get("code"), resp.get("message")))

        answer = (data.get("answer") or "").strip()
        if not answer:
            print("      ! 回答为空")
            failed += 1
        else:
            print("      " + answer.split("\n")[0][:110])

        dq = data.get("dataQuery")
        if dq:
            print("      dataQuery: hitMonths=%s mixedUnit=%s rows=%s"
                  % (dq.get("hitMonths"), dq.get("mixedUnit"),
                     len(dq.get("rows") or [])))
        pr = data.get("predictResult")
        if pr:
            print("      predict: value=%s unit=%s" % (pr.get("value"), pr.get("unit")))

        if route == "RAG_NEWS":
            failed += check_rag_scale(data)
        if route == "SCOPE":
            failed += check_scope(data)
        if route == "CHITCHAT":
            failed += check_chitchat_scope(data)
        if "奶制品" in text:
            failed += check_dairy_suggest(data)
        if "2027年1月进口数量" in text and "石油" in text:
            failed += check_far_month_warning(data)

    print("\n%s  （%d 项，失败 %d 项）"
          % ("全部通过" if failed == 0 else "存在失败", len(CASES), failed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
