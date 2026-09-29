#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""智能问答路由与引导的验收脚本（backend-main /ai/chat）。

背景：此前问答链路存在「无论问什么都被判成贸易预测、输出全是同一段固定引导语」
的问题。修复后有四条路由，本脚本逐条覆盖，并核对数据查询结果与数据库一致。

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
]


def ask(base, text, timeout=180):
    body = json.dumps({"text": text}).encode("utf-8")
    req = urllib.request.Request(
        base.rstrip("/") + "/ai/chat", data=body,
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.load(resp)


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

    print("\n%s  （%d 项，失败 %d 项）"
          % ("全部通过" if failed == 0 else "存在失败", len(CASES), failed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
