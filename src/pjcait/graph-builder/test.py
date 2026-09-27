import os

from langchain_core.prompts import ChatPromptTemplate
from langchain_experimental.graph_transformers import LLMGraphTransformer
from langchain_experimental.graph_transformers.llm import system_prompt
from langchain_openai import ChatOpenAI

from langchain_neo4j import Neo4jGraph

# 从项目根 config/.env 载入 NEO4J_URI / NEO4J_USERNAME / NEO4J_PASSWORD（该文件不入库）
import _trade_config  # noqa: F401

graph = Neo4jGraph()

llm = ChatOpenAI(
  temperature=0,
  # model_name="model",
  model_name="/hd_2t/lj/hty/models/Qwen3-30B-A3B-Q8_0.gguf",
  openai_api_key='None',
  openai_api_base='http://127.0.0.1:46802/v1',
  # openai_proxy=''
)

# llm = ChatOpenAI(
#   temperature=0,
#   # model_name="model",
#   model_name="deepseek-chat",
#   openai_api_key=os.getenv('DEEPSEEK_API_KEY'),   # 值见 config/.env
#   openai_api_base='https://api.deepseek.com',
#   # openai_proxy=''
# )

# llm = ChatOpenAI(
#   temperature=0,
#   model_name="Qwen/Qwen3-30B-A3B",
#   openai_api_key=os.getenv('SILICONFLOW_API_KEY'),  # 值见 config/.env
#   openai_api_base='https://api.siliconflow.cn/v1',
#   # openai_proxy=''
# )

# system_prompt_cn='''
# # 大型语言模型的知识图谱构建指令
#
# ## 1. 概述
# 你是一个专为结构化信息抽取而设计的顶级算法和专家模型，用于构建知识图谱。
# 在确保准确性的前提下，尽可能从文本中提取更多信息。严禁添加任何文本中未明确提及的内容。
# - **节点**代表实体和概念
# - 目标是保持知识图谱的简洁性和清晰性，确保广大用户都能理解
#
# ## 2. 节点标注规范
# - **一致性**：确保使用统一的节点标签类型，必须使用基础或基本类型作为节点标签
# - 示例：当识别到代表人物的实体时，统一标注为**"人物"**。避免使用更具体的术语如"数学家"或"科学家"
# - **节点ID**：禁止使用数字作为节点ID。节点ID应使用文本中出现的人名或人类可读的标识符
#
# ## 3. 关系标注规范
# - **关系**表示实体或概念之间的连接
# 构建知识图谱时，确保关系类型的一致性和通用性。避免使用"成为教授"等具体瞬时性关系，而应使用"教授"等通用且持久的关系类型。必须使用通用且持久的关系类型！
#
# ## 4. 指代消解规范
# - **保持实体一致性**：提取实体时，确保一致性至关重要
# 如果某个实体（如"张三"）在文本中被多次提及但使用了不同名称或代词（如"三哥"、"他"），在整个知识图谱中始终使用该实体最完整的标识符。本例中应使用"张三"作为实体ID
# 注意：知识图谱应保持连贯性和易理解性，因此保持实体引用的一致性非常关键
#
# ## 5. 严格性要求
# 必须严格遵守上述规则。违反规则将导致处理终止
# '''

llm_transformer = LLMGraphTransformer(
  llm=llm,
  # prompt=ChatPromptTemplate.from_messages(
  #       [
  #           ("system", system_prompt_cn),
  #           (
  #               "human",
  #               # additional_instructions
  #               "提示：请确保按正确格式输出，不要包含任何解释说明。"
  #               "使用指定格式从以下输入中提取信息：{input}"
  #           ),
  #       ]
  #   )
)

from langchain_core.documents import Document

text = """
热点栏目
自选股
[数据中心]( http://vip.stock.finance.sina.com.cn/q/go.php/vIR_RatingNewest/index.phtml)
行情中心
资金流向
模拟交易
客户端
华尔街见闻
作者： 张进
5月18日早盘，白糖板块一度摸高，南宁糖业逼近涨停，中粮糖业、甘化科工等盘中亦有异动。
消息面上，继哈萨克斯坦和巴基斯坦发布食糖出口禁令后，全球食糖产量最大的国家巴西也表示，当地大量的甘蔗加工厂正在取消部分糖出口合同，在2022/23年度或将减少约120万吨食糖产量。
多个国家食糖出口遇阻
据一财等报道，2022年1月，巴基斯坦CPI上升至13%，为近两年最高。为降低国内通货膨胀预期，巴基斯坦宣布禁止食糖出口。资料显示该国家年产食糖750万吨左右，名列全球第8位。
此外哈萨克斯坦近日也发布了为期6个月的食糖和原糖出口禁令，5月23日开始生效。资料显示，哈萨克斯坦每年国内食糖自给率仅7%左右，大部分食糖需求依靠进口。
再就是此次的主角巴西，其是世界最大的甘蔗生产国和出口国，每年的食糖产量大约是3300万吨，位居全球第一，供应全球50%的食糖。
另外在3月新华社曾报道称俄罗斯已暂时禁止了食糖出口。
原材料甘蔗用以利润更高的乙醇
对于食糖产量降低的原因，华泰期货援引有关人士透露，巴西甘蔗加工厂正取消部分糖出口合同，并转而生产乙醇，以便从能源价格高企中获利。一家大型国际大宗商品交易商近日在纽约举行的一场糖业会议上表示，几乎所有参与巴西糖业交易的公司都有合同被取消。
另一方面，Archer咨询公司称，由于预计甘蔗单产下降，巴西2022/23年度中南部地区甘蔗产量料为5.48亿吨，低于上一年度的5.52亿吨。预计巴西糖厂在2022/23年度用干糖生产的甘蔗比例为43.8%，前次预估为45.2%。
巴西糖业分析师预计，在乙醇价格高企的背景下，巴西在2022/23年度可能减少约120万吨食糖产量。
国内状况如何？
国内方面，中国每年食糖产量大约是1030万吨，约为巴西产量的三分之一，但中国却是全球主要的食糖进口大国，2021年全国累计进口食糖566.62万吨。
而从供需平衡的角度来看，自2017年以来，中国白糖的库存消费比自41.83%下滑至25.5%，国内消费总计整体表现平稳，但各年度的期初库存却持续回落，意味着行业供给小于需求。
分地区来看，广西则是国内桂冠，数据显示，2021年，广西占全国成品糖产量比值达47.5%，糖料蔗种植面积和食糖产量连续17个榨季占全国的60%左右，是名副其实的中国“糖罐子”。
此外，在多重因素的作用下，近期郑商所白糖主力合约209亦呈现四连涨。
华泰期货指出，从中期来看，高油价使得头号主产国巴西产量维持低水平，对原糖价格形成支撑，国内进口利润严重倒挂削减进口，以及到夏季后进入采购旺季，国内供需情况逐渐好转，将会支撑糖价震荡上行。
风险提示及免责条款
市场有风险，投资需谨慎。本文不构成个人投资建议，也未考虑到个别用户特殊的投资目标、财务状况或需要。用户应考虑本文中的任何意见、观点或结论是否符合其特定状况。据此投资，责任自负。
新浪合作大平台期货开户 安全快捷有保障
责任编辑：赵思远
"""

documents = [Document(page_content=text)]
graph_documents = llm_transformer.convert_to_graph_documents(documents)

for document in graph_documents:
  for node in document.nodes:
    print(node)

  for relationship in document.relationships:
    print(relationship)

# print(f"Nodes:{graph_documents[0].nodes}")
# print(f"Relationships:{graph_documents[0].relationships}")

# print(graph_documents)
import os

# graph.add_graph_documents(graph_documents)