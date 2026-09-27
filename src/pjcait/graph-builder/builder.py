import hashlib
import json
import os

from langchain_core.prompts import ChatPromptTemplate
from langchain_experimental.graph_transformers import LLMGraphTransformer
from langchain_experimental.graph_transformers.llm import system_prompt
from langchain_openai import ChatOpenAI

from langchain_neo4j import Neo4jGraph

# 凭据统一来自项目根 config/.env（该文件不入库），详见 config/README.md
from _trade_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE

graph = Neo4jGraph(
  url=NEO4J_URI,
  username=NEO4J_USERNAME,
  password=NEO4J_PASSWORD,
  database=NEO4J_DATABASE,
)

llm = ChatOpenAI(
  temperature=0,
  # model_name="model",
  model_name='/hd_2t/lj/hty/models/Qwen3-30B-A3B-Q8_0.gguf',
  openai_api_key='None',
  openai_api_base='http://127.0.0.1:46802/v1',
  # openai_proxy=''
)

llm_transformer = LLMGraphTransformer(
  llm=llm,
)

from langchain_core.documents import Document


def generate_id(link: str) -> str:
  return hashlib.md5(link.encode('utf-8')).hexdigest()

def node_to_dict(node):
  return {'id': node.id, 'type': node.type, 'properties': node.properties}

def process(file_dir, file_id, text):
  documents = [Document(page_content=text)]
  graph_documents = llm_transformer.convert_to_graph_documents(documents)

  for document in graph_documents:
    for node in document.nodes:
      print(node)

    for relationship in document.relationships:
      print(relationship)
  graph.add_graph_documents(graph_documents)

  with open(os.path.join(file_dir, f'{file_id}.json'), encoding='utf-8') as file:
    data = [{
      'nodes': [node_to_dict(node) for node in document.nodes],
      'relationships': [{
        'source': node_to_dict(relationship.source),
        'target': node_to_dict(relationship.target),
        'type': relationship.type,
        'properties': relationship.properties,
      } for relationship in document.relationships]
    } for document in graph_documents]

    for document in graph_documents:
      document.to_json()
      data.append({
        'nodes': document.nodes,
        'relationships': document.relationships,
      })
    file.write(json.dumps(data))
  print(f'result file write to {file_id}.json')

