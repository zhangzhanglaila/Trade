import neo4j from 'neo4j-driver'

// Neo4j 连接信息来自环境变量（真实值见项目根 config/.env，该文件不入库）。
// vue.config.js 会在启动时载入 config/.env，因此 npm run serve 无需额外操作。
const NEO4J_URI = process.env.VUE_APP_NEO4J_URI || 'bolt://localhost:7687'
const NEO4J_USER = process.env.VUE_APP_NEO4J_USER || 'neo4j'
const NEO4J_PASSWORD = process.env.VUE_APP_NEO4J_PASSWORD || ''

if (!NEO4J_PASSWORD) {
  console.warn('[neo4j] 未配置 VUE_APP_NEO4J_PASSWORD，图谱可视化将无法连接数据库。参见 config/README.md')
}

const driver = neo4j.driver(
  NEO4J_URI,   // Neo4j Bolt 端口
  neo4j.auth.basic(NEO4J_USER, NEO4J_PASSWORD) // 账号密码
)

export async function runCypher(cypher, params = {}) {
  const session = driver.session()
  try {
    const res = await session.run(cypher, params)
    return res.records.map(r => r.toObject()) // 转 JS 数组
  } finally {
    await session.close()
  }
}