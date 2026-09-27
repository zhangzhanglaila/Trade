import json

import openai
from neo4j import GraphDatabase

from _trade_config import (
  DEEPSEEK_BASE_URL, DEEPSEEK_API_KEY,
  NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE,
  NEO4J_URI_REMOTE, NEO4J_DATABASE_REMOTE,
)

openclient = openai.Client(
  base_url=DEEPSEEK_BASE_URL,
  api_key=DEEPSEEK_API_KEY
)

system_prompt = """
# 知识图谱构建指令

## 1. 概述
你是一个专为结构化信息抽取而设计的顶级算法和专家模型，目标是根据输入的新闻文本，抽取贸易和物流相关或者有影响的知识信息，用于构建知识图谱。
请你理解文本语义并进行信息抽取。
在确保准确性的前提下，针对用户的数据，尽可能从文本中提取更多信息。严禁添加任何文本中未明确提及的内容。
- **节点**代表实体和概念，包括但不限于货物、运输方式、地点、组织机构、政策、时间、数据等信息，**关系**表示实体或概念之间的连接
- 目标是保持知识图谱的简洁性和清晰性，确保广大用户都能理解
- 同时，请注意抽取出的关系三元组是否存在常识性的错误，禁止出现如关系：(中国,位于,非洲)等这类明显的严重的错误

## 2. 节点标注规范
- **一致性**：确保使用统一的节点标签类型，必须使用基础或基本类型作为节点标签，如地点、组织机构、人物等
- 示例：当识别到代表人物的实体时，统一标注为**"人物"**。避免使用更具体的术语如"数学家"或"科学家"
- **节点ID**：禁止使用数字作为节点ID。节点ID应使用文本中出现的人名或人类可读的标识符

## 3. 关系标注规范
- **关系**表示实体或概念之间的连接
构建知识图谱时，确保关系类型的一致性和通用性，使用更通用和无时态的关系类型。避免使用"成为教授"等具体瞬时性关系，而应使用"教授"等通用且持久的关系类型。必须使用通用且持久的关系类型！

## 4. 指代消解规范
- **保持实体一致性**：提取实体时，确保一致性至关重要
如果某个实体（如"张三"）在文本中被多次提及但使用了不同名称或代词（如"三哥"、"他"），在整个知识图谱中始终使用该实体最完整的标识符。本例中应使用"张三"作为实体ID
注意：知识图谱应保持连贯性和易理解性，因此保持实体引用的一致性非常关键

## 5. 输出格式
输出分为两部分，第一部分是 **实体节点**，第二部分为 **实体间的关系三元组**，这两部分之间使用三个横线 `---` 分隔。
第一部分每一行为一个实体节点信息。每行的节点信息包含了 **节点ID（实体名称）** 和 **实体类型**，节点与实体类型之间使用半角逗号`,`分隔。
中间使用三个横线 `---` 分隔。
第二部分每一行为一条实体间的关系三元组。每行关系三元组使用形如 `(实体1,关系类型1,实体2);(属性1:属性值1,属性2:属性值2)` 表示该关系三元组的源节点为`实体1`，关系为`关系类型1`，目标节点为`实体2`，代表`实体1`到`实体2`具有关系`关系类型1`，并且该关系含有属性信息`属性1:"属性值1",属性2:"属性值2"`这两条属性信息。如果这个关系没有可用的属性信息，则留空，即`(实体1,关系类型1,实体2);()`，关系的三元组中必须为且只能为三个元素组成（即必须包含源节点、关系以及目标节点）。

例如，输入以下新闻文本：哈铁路公司网站2月5日报道，2024年，里海库雷克港共转运货物195.78万吨，其中铁路码头转运货物115.06万吨，汽运码头转运货物78.13万吨，粮食码头转运货物2.59万吨。

以下是输出示例，请你参考：

```
哈铁路公司,组织机构
里海库雷克港,地点
铁路码头,地点
汽运码头,地点
粮食码头,地点
---
(哈铁路公司,转运,货物);(时间:"2024年",数量:"195.78万吨")
(铁路码头,转运,货物);(时间:"2024年",数量:"115.06万吨")
(汽运码头,转运,货物);(时间:"2024年",数量:"78.13万吨")
(粮食码头,转运,货物);(时间:"2024年",数量:"2.59万吨")
(铁路码头,属于,里海库雷克港);()
(汽运码头,属于,里海库雷克港);()
(粮食码头,属于,里海库雷克港);()
```

输出参考结束

## 6. 严格性要求
必须严格遵守上述规则，违反规则将导致处理终止
"""

# Prompt 模板
user_prompt_tmpl = """
指令：请确保以正确的格式回答，并且不要包含任何解释。使用给定的格式从以下输入中提取【贸易和物流相关信息】：

{text}

"""


# 文本处理函数
def generate_prompt(text, insert_no_think=None):
  if insert_no_think:
    return f"{user_prompt_tmpl.format(text=text)}/no_think"
  else:
    return user_prompt_tmpl.format(text=text)


# 调用 LLM
def call_llm(text, insert_no_think=None):
  prompt = generate_prompt(text, insert_no_think)
  response = openclient.chat.completions.create(
    stream=True,
    temperature=0,
    max_tokens=4000,
    max_completion_tokens=4000,
    model="deepseek-chat",  # 或其他兼容模型
    messages=[
      {"role": "system", "content": system_prompt},
      {"role": "user", "content": prompt}
    ],
  )

  result = ''
  for chunk in response:
    content = chunk.choices[0].delta.content
    result += content

  return result


import re

relation_pattern = re.compile(r"\(([^()]+)\);\(([^()]*)\)")


def parse_relations(relations_text):
  results = []
  for match in relation_pattern.finditer(relations_text):
    main_clause = match.group(1).split(",")
    if len(main_clause) != 3:
      continue

    relation = {
      "source": main_clause[0].strip(),
      "rel": main_clause[1].strip(),
      "target": main_clause[2].strip(),
      "attrs": {}
    }

    extra_clause = match.group(2)

    if extra_clause:
      extras = re.findall(r'(\w+):"([^"]+)"', extra_clause)
      for key, value in extras:
        relation["attrs"][key] = value.replace("'", '')
      pass
    pass

    results.append(relation)
  pass

  return results


result_pattern = re.compile(r"```[^\n]*\n(.*?)```", re.DOTALL)


def parse_llm_output(output_text: str):
  output_text = output_text.strip()
  if output_text.startswith("```"):
    output_text = result_pattern.search(output_text).group(1)

  lines = output_text.strip().split('---')
  if len(lines) < 2:
    return [], []

  node_lines, relation_lines = lines[0], lines[1]

  nodes = []
  for line in node_lines.strip().splitlines():
    node = line.strip().split(',')
    nodes.append({'name': node[0].strip(), 'type': node[1].strip()})

  return nodes, parse_relations(relation_lines)


driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD), database=NEO4J_DATABASE)
# 另一台图库：driver = GraphDatabase.driver(NEO4J_URI_REMOTE, auth=(NEO4J_USERNAME, NEO4J_PASSWORD), database=NEO4J_DATABASE_REMOTE)


# 生成并执行 Cypher 插入
def insert_to_neo4j(driver, nodes, relations):
  # driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD), database=NEO4J_DATABASE_REMOTE)
  with driver.session() as session:
    # 创建实体节点
    node_types = {}
    with session.begin_transaction() as tx:
      for node in nodes:
        name = node.get('name')
        ntype = node.get('type')

        query = f"MERGE (n:`{ntype}` {{id: $name}})"
        tx.run(query, name=name)
        node_types[name] = ntype
      pass

      for relation in relations:
        source = relation.get('source')
        rel = relation.get('rel')
        target = relation.get('target')
        attrs = relation.get('attrs')

        stype = node_types.get(source)
        if stype is None:
          tx.run(f"MERGE (n:`节点` {{id: $name}})", name=source)
          stype = '节点'

        ttype = node_types.get(target)
        if ttype is None:
          tx.run(f"MERGE (n:`节点` {{id: $name}})", name=target)
          ttype = '节点'

        kv_items = []
        for key, value in attrs.items():
          if key[0].isdigit():
            continue
          kv_items.append(f"{key}:'{value}'")
        attr_str = '{ %s }' % (', '.join(kv_items))
        query = f"""
        MATCH (a:`{stype}` {{id: $source}}), (b:`{ttype}` {{id: $target}})
        MERGE (a)-[:`{rel}` {attr_str}]->(b)
        """
        tx.run(query, source=source, target=target)
      pass
    pass
  pass


# 保存原始模型输出
def save_output(output_text, filename="llm_raw_output.txt"):
  with open(filename, "w", encoding="utf-8") as f:
    f.write(output_text)


# text = """
# （哈萨克国际通讯社讯）中国已与全球124个国家建立起贸易伙伴关系，其中哈萨克斯坦是其具有战略意义的重要合作方之一。对我国而言，中国的投资和庞大市场具有不可忽视的吸引力。那么，从中国的角度看，哈萨克斯坦作为贸易伙伴又具备哪些优势与挑战？ 对此，中国某大型物流公司股东瞿斌近日在接受丝绸之路电视频道（Jibek Joly）“全球视野”栏目采访时中进行了深入解读。
# 哈通社：在打造国内和国际物流网络方面，中国近年来有哪些重点举措？您认为最关键的发展方向有哪些？
# 瞿斌：中国近些年完善了基础设施的建设，比如专业化和现代化的物流工业园区，加强了铁路与公路枢纽的对接，完善了多式联运，同时完善了仓储、分拣等一系列的配套设施，引入了物联网，提升信息化水平。此外，加强了与其他国家的合作，开展物流基础设施合作，比如推进中哈、中俄铁路跨境设施的建设。提高了口岸的服务水平，优化通关流程，提高效率，加速通关速度等。
# 哈通社：途经哈萨克斯坦的过境路线在中国对外贸易与物流战略中扮演着怎样的角色？其重要性是否呈上升趋势？
# 瞿斌：肯定有所上升，哈萨克斯坦的过境路线不仅是一带一路的枢纽，也是中国联通欧亚，保证供应链安全，推动区域经济一体化战略的重要支点，我相信，随着哈萨克斯坦作为欧亚枢纽的地位的进一步凸显，将为中国经济的良性发展持续注入动能。
# Фото: видеодан алынған скрин
# 哈通社：对于中国来说，相比经俄罗斯的传统运输通道，通过哈萨克斯坦的路线有哪些显著的优势与不足？您如何评估这两条路径的互补性或竞争性？
# 瞿斌：先说说哈萨克斯坦的薄弱点吧，哈萨克斯坦近十几年来在基础设施方面有很大的发展，但相比于俄罗斯来说，仍要稍微薄弱一些，比如在公路路网覆盖方面就存在一些限制。这一点，可能会影响货物的运输速度和质量。而且，我认为，相比于俄罗斯相对完善的运输网络，哈萨克斯坦与周边国家在运输网络连接方面，也不是非常通畅，在货物的转运方面存在效率不高的情况。
# 优势方面的话，哈萨克斯坦与中国建立了很好的关系，在能源方面比较依赖中国的投资和市场，双方实际上是一种相互依赖的关系，这能够建立一种很好的稳定性。
# 与之相比，中俄之间可能存在一些地缘政治上的不确定因素。从运输成本上来说，哈国与中国西部接壤，部分货物走哈萨克斯坦路线更近，相对来说会更加实惠，成本更低。
# Фото: видеодан алынған скрин
# 哈通社：“新丝绸之路”倡议为中国的贸易伙伴带来了哪些新的合作机遇？是否出现了新的产业集群或合作模式？
# 瞿斌：中国作为124个国家的第一大贸易伙伴，“新丝绸之路”连接了亚洲、非洲、欧洲的各个国家，为这些国家的企业提供了更加广阔的市场空间。中国与沿线国家的贸易额在不断的增长，为各个国家的特色产品提供了更加广阔的销售渠道。这是我的看法。
# 哈通社：近年来，像西安、阿拉木图这样的物流枢纽在区域物流格局中承担着怎样的新职能？其作用如何演变？
# 瞿斌：西安是中国的一座内陆城市，由于一带一路和中欧班列的出现，它成为了中国第一个不靠海的大型物流枢纽城市，构建起了一个辐射全球的物流网络，从深处内陆腹地变成了对外开放的前沿。而阿拉木图，它实现了从交通节点向现代化物流枢纽的转变。阿拉木图是哈萨克斯坦最重要的枢纽，拥有最大的国际机场，铁路连接了中亚、乌拉尔和中国西北，随着基础设施的升级，正在逐步成为一个现代化的大型物流枢纽城市。
# 哈通社：您认为，在过境运输实践中，中哈双方目前面临哪些主要的障碍？有哪些正在推进的改善措施？
# 瞿斌：就我所知，哈萨克斯坦从2024年3月开始，对中国过境的物资进行百分之百的开箱查验，这造成了口岸过境时间的延长，货物的积压。这尤其使得电商和高价值商品，面临更高的风险。而且，中哈双方在海关政策，铁路运费优惠等方面缺乏统一的标准，相比之下，在里海沿岸，如哈萨克斯坦、格鲁吉亚、阿塞拜疆等，已经形成了一致的过境费率。
# 哈通社：那么根据您的经验，您最看好哪一种方式，在哈中之间能够占据主导地位？
# 瞿斌：对于哈中来说，我比较看好汽车运输，毕竟两国过境相互接壤，而且公路运输的局限性相对较小，装卸便捷，能够提供门到门服务，对客户更方便。
# Фото: видеодан алынған скрин
# Фото: видеодан алынған скрин
# """

# 主流程
def main():
  # input_text = text
  # print("Calling LLM...")
  # llm_output = call_llm(input_text)
  # save_output(llm_output)
  #
  # print("Parsing output...")
  llm_output = ""
  entities, relations = parse_llm_output(llm_output)

  print("Inserting into Neo4j...")
  insert_to_neo4j(driver, entities, relations)

  print("Done.")


if __name__ == "__main__":
  main()
