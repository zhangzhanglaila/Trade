# 训练数据目录 —— 这里放的是**样例/占位文件，不是真实训练数据**

⚠️ **这两个文件不能用来训练。** 它们曾导致产出的模型 R² 全为负，详见
[`docs/模型评测报告.md`](../../../docs/模型评测报告.md) §3.4。

| 文件 | 实际内容 | 按 `seq_length=2` 能生成的训练样本 |
|---|---|---|
| `出口/merged_output.csv` | 206 行样例（GBK） | **29 条** |
| `进口/merged_input.csv` | **实为 Excel 工作簿**（文件头 `50 4B 03 04`，可当 zip 打开，内含 `xl/worksheets/sheet1.xml`），只是扩展名写成了 `.csv` | **0 条**（`pandas.read_csv` 无法解析） |

## 真实数据在哪

```
data/05_运行_进出口CSV/出口/merged_output.csv   264 MB  191 万行  → 约 190 万条样本
data/05_运行_进出口CSV/进口/merged_output.csv   5.7 MB   4 万行  → 约 3.8 万条样本
```

（UTF-8 with BOM 编码；列名与训练脚本期望的完全一致。）

后端配置 `src/backend-training/src/main/resources/application.yml` 的
`python.data.*.csv-path` **已改指向上述真实数据**。若你要手动跑训练脚本，
`--csv_path` 也要指到那里，不要指回本目录。

## 为什么保留这两个文件

它们在 git 里有历史、且被旧配置引用过，直接删除会破坏可追溯性。
保留原样，但配置已不再指向它们。**如果你确认不再需要，可以删掉整个目录。**
