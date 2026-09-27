const express = require('express');
const cors = require('express');
const app = express();
app.use(cors());
app.use(express.json());

let nodes = [{ id: 1, name: 'test' }];   // 假数据

/* 新增 GET 路由 ⬅️ 就是这条缺了 */
app.get('/api/v1/graph/nodes', (req, res) => res.json(nodes));

/* 你已有的 POST 路由 */
app.post('/api/v1/graph/nodes', (req, res) => {
  nodes.push(req.body);
  res.status(201).json(req.body);
});

app.listen(3000, () => console.log('3000 ok, GET/POST /nodes 都通了'));