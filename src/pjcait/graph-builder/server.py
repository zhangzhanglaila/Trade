from datetime import datetime
from flask import Flask, request
import logging
from concurrent.futures import ThreadPoolExecutor
import requests

app = Flask(__name__)

from neo4j import GraphDatabase

import builder2
from _trade_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE

driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD), database=NEO4J_DATABASE)

@app.route('/api', methods=['POST'])
def add_data():
    req_json = request.get_json()
    content = req_json.get('content')

    pass