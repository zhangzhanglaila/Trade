# -*- coding: utf-8 -*-
import os

backup_path = r"D:\trade-back\TDProject-main\src\main\java\com\example\tdproject\generator\service\impl\OntologyServiceImpl.java.backup"
output_path = r"D:\trade-back\TDProject-main\src\main\java\com\example\tdproject\generator\service\impl\OntologyServiceImpl.java"

# 读取备份文件的UTF-8内容（当前是乱码）
with open(backup_path, 'r', encoding='utf-8', errors='replace') as f:
    garbled_text = f.read()

print(f"Read {len(garbled_text)} characters from backup")

# 关键修复步骤：
# 1. 遍历每个字符
# 2. 如果字符可以被GBK编码，则编码它并收集字节
# 3. 如果字符不能被GBK编码（如ASCII字符），则直接用UTF-8编码
# 4. 最后将所有收集到的字节用UTF-8解码

result_bytes = bytearray()
skipped_chars = 0
encoded_chars = 0

for i, char in enumerate(garbled_text):
    # 尝试用GBK编码这个字符
    try:
        gbk_encoded = char.encode('gbk')
        result_bytes.extend(gbk_encoded)
        encoded_chars += 1
    except UnicodeEncodeError:
        # 不能用GBK编码，直接用UTF-8
        utf8_encoded = char.encode('utf-8')
        result_bytes.extend(utf8_encoded)
        skipped_chars += 1

print(f"GBK encoded: {encoded_chars} chars")
print(f"UTF-8 encoded: {skipped_chars} chars")
print(f"Total bytes: {len(result_bytes)}")

# 现在用UTF-8解码这些字节
try:
    fixed_text = result_bytes.decode('utf-8')
    print(f"Successfully decoded to UTF-8: {len(fixed_text)} chars")
except UnicodeDecodeError as e:
    print(f"UTF-8 decode error: {e}")
    fixed_text = result_bytes.decode('utf-8', errors='ignore')
    print(f"Decoded with ignore: {len(fixed_text)} chars")

# 保存修复后的文件
with open(output_path, 'w', encoding='utf-8') as f:
    f.write(fixed_text)

print(f"\nSaved to: {output_path}")

# 验证：检查是否包含正确的中文
keywords = ['分页查询', '当前版本', '创建', '更新', '删除', '本体', '版本', '项目', '列表', 
            '查询', '实例', '类型', '根据', '名称', '存在', '保存', '继承', '属性', '关系',
            '导入', '导出', 'Excel', '图数据库', '可视化', '统计']

found_keywords = [kw for kw in keywords if kw in fixed_text]
print(f"\nFound {len(found_keywords)} Chinese keywords: {found_keywords[:10]}")

# 写入验证文件
verify_path = r"D:\trade-back\TDProject-main\verify_v2.txt"
with open(verify_path, 'w', encoding='utf-8') as f:
    f.write(f"Found {len(found_keywords)} keywords: {found_keywords}\n\n")
    f.write("Sample lines with Chinese:\n")
    f.write("="*50 + "\n")
    
    lines = fixed_text.split('\n')
    count = 0
    for line in lines:
        if any(kw in line for kw in keywords):
            f.write(line + "\n")
            count += 1
            if count >= 30:
                break

print(f"\nVerification written to: {verify_path}")
