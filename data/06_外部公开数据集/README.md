# 06_外部公开数据集

本目录**只放说明**，不搬数据。原因：这三份公开数据集目前位于训练仓库内，且被 git 跟踪，
移出会让仓库工作区变脏（`git status` 出现大量删除），因此保持原位。

## 实际位置

| 数据集 | 路径 | 说明 |
| --- | --- | --- |
| ECKGBench（淘宝电商常识图谱） | `..\..\models\qwen3.5-9b-kh-trade-qa\data\raw\ECKGBench-main\` | CIKM 2025，含 `ECKGBench.jsonl` 与 `ECKGBench_large.jsonl` 两份大文件 |
| OpenBG-CSK | `..\..\models\qwen3.5-9b-kh-trade-qa\data\raw\OpenBG-CSK\` | 电商常识基准 |
| product_product | `..\..\models\qwen3.5-9b-kh-trade-qa\data\raw\product_product\` | 商品-商品关系抽取数据 |

## 注意

这三份数据**不属于中哈贸易图谱**，不可混入贸易知识图谱做指标统计（会造成口径污染）。
在 SFT 训练中它们的实际使用率约 0.44%，且 97.6% 的训练样本来自这些非贸易领域数据。
