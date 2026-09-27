import re
import json

text = """
(哈萨克斯坦,计划增加货运量,中欧运输走廊);(时间:"未来三年内",目标:"翻倍")
(哈萨克斯坦,合作建立,交通运输技术联合企业);(合作伙伴:"意大利")
(哈萨克斯坦,改善,投资环境);(方式:"大规模经济改革和完善立法")
(哈萨克斯坦,发展,跨里海国际运输路线);(目标:"重振古丝绸之路")
(哈萨克斯坦段,增长,集装箱货运量);(时间:"去年",增长率:"62%",数量:"450万吨")
(哈萨克斯坦,计划增加,集装箱货运量);(时间:"未来三年",目标:"翻倍")
(意大利,是,哈萨克斯坦三大贸易伙伴之一);()
(意大利,投资,哈萨克斯坦);(总额:"76亿美元")
(意大利资本参与的公司,运营,哈萨克斯坦);(数量:"约270家",领域:"石油天然气、可再生能源、机械制造和农业发展")
(哈萨克斯坦与意大利,双边贸易额增长);(时间:"2024年",增长率:"24%",金额:"200亿美元")
(哈萨克斯坦,邀请合作,意大利);(领域:"纺织、家具、制药和汽车工业")
"""

# 正则表达式匹配结构：(主语, 动作, 宾语);(补充信息)
pattern = re.compile(r"\(([^()]+)\);\(([^()]*)\)")

results = []
for match in pattern.finditer(text):
    main_clause = match.group(1).split(",")
    extra_clause = match.group(2)

    entry = {
        "subject": main_clause[0].strip(),
        "action": main_clause[1].strip(),
        "object": main_clause[2].strip(),
        "extras": {}
    }

    # 提取补充信息中的键值对
    if extra_clause:
        extras = re.findall(r'(\w+):"([^"]+)"', extra_clause)
        for key, value in extras:
            entry["extras"][key] = value

    results.append(entry)

# 输出为 JSON 格式查看结构
print(json.dumps(results, ensure_ascii=False, indent=2))
