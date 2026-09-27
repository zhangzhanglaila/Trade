# -*- coding: utf-8 -*-
import os

file_path = r"D:\trade-back\TDProject-main\src\main\java\com\example\tdproject\generator\service\impl\OntologyServiceImpl.java"
backup_path = file_path + '.backup'

# 从备份恢复原始字节
with open(backup_path, 'rb') as f:
    raw_bytes = f.read()

print(f"Original file size: {len(raw_bytes)} bytes")

# 尝试多种解码方式

# 方式1：直接用GBK解码
try:
    text_gbk = raw_bytes.decode('gbk')
    print(f"Method 1 - GBK decode: SUCCESS, {len(text_gbk)} chars")
except Exception as e:
    print(f"Method 1 - GBK decode: FAILED - {e}")
    text_gbk = None

# 方式2：GBK解码，忽略错误
text_gbk_ignore = raw_bytes.decode('gbk', errors='ignore')
print(f"Method 2 - GBK ignore: {len(text_gbk_ignore)} chars")

# 方式3：UTF-8解码
try:
    text_utf8 = raw_bytes.decode('utf-8')
    print(f"Method 3 - UTF-8 decode: SUCCESS, {len(text_utf8)} chars")
except Exception as e:
    print(f"Method 3 - UTF-8 decode: FAILED - {e}")
    text_utf8 = None

# 方式4：UTF-8解码，忽略错误
text_utf8_ignore = raw_bytes.decode('utf-8', errors='ignore')
print(f"Method 4 - UTF-8 ignore: {len(text_utf8_ignore)} chars")

# 检查哪种方式产生了正确的中文
# 我们将结果写入不同的文件来比较

def write_and_verify(text, label, output_path):
    """写入文件并检查是否包含正确的中文"""
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(text)
    
    # 检查是否包含正确的中文关键词
    keywords = ['分页查询', '当前版本', '创建', '更新', '删除', '本体', '版本', '项目', '列表', 
                '查询', '实例', '类型', '根据', '名称', '存在', '保存', '继承', '属性', '关系',
                '导入', '导出', 'Excel', '图数据库', '可视化', '统计']
    found_keywords = [kw for kw in keywords if kw in text]
    
    return len(found_keywords), found_keywords[:5]

# 测试各种方法
results = []

if text_gbk:
    count, kws = write_and_verify(text_gbk, "GBK", r"D:\trade-back\TDProject-main\test_gbk.txt")
    results.append(("GBK", count, kws, text_gbk))
    print(f"GBK: Found {count} keywords: {kws}")

count, kws = write_and_verify(text_gbk_ignore, "GBK-ignore", r"D:\trade-back\TDProject-main\test_gbk_ignore.txt")
results.append(("GBK-ignore", count, kws, text_gbk_ignore))
print(f"GBK-ignore: Found {count} keywords: {kws}")

if text_utf8:
    count, kws = write_and_verify(text_utf8, "UTF-8", r"D:\trade-back\TDProject-main\test_utf8.txt")
    results.append(("UTF-8", count, kws, text_utf8))
    print(f"UTF-8: Found {count} keywords: {kws}")

count, kws = write_and_verify(text_utf8_ignore, "UTF-8-ignore", r"D:\trade-back\TDProject-main\test_utf8_ignore.txt")
results.append(("UTF-8-ignore", count, kws, text_utf8_ignore))
print(f"UTF-8-ignore: Found {count} keywords: {kws}")

# 选择最佳结果
best = max(results, key=lambda x: x[1])
print(f"\nBest method: {best[0]} with {best[1]} keywords")

# 将最佳结果保存到原文件
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(best[3])

print(f"\nFile saved to: {file_path}")

# 显示前10行包含中文的行
print("\nSample lines with Chinese characters:")
lines = best[3].split('\n')
found = 0
for line in lines:
    if any('\u4e00' <= c <= '\u9fff' for c in line):
        # 过滤掉乱码行（包含太多生僻字的行）
        cjk_chars = [c for c in line if '\u4e00' <= c <= '\u9fff']
        if len(cjk_chars) > 0:
            safe_line = line.encode('ascii', 'replace').decode('ascii')
            print(safe_line[:100])
            found += 1
            if found >= 10:
                break
