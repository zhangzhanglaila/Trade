# -*- coding: utf-8 -*-
file_path = r"D:\trade-back\TDProject-main\src\main\java\com\example\tdproject\generator\service\impl\OntologyServiceImpl.java.backup"

with open(file_path, 'rb') as f:
    data = f.read()

print(f"File size: {len(data)} bytes")

# 检查BOM
if data[:3] == b'\xef\xbb\xbf':
    print("BOM: UTF-8")
elif data[:2] == b'\xff\xfe':
    print("BOM: UTF-16 LE")
elif data[:2] == b'\xfe\xff':
    print("BOM: UTF-16 BE")
else:
    print("BOM: None")

# 查找中文注释的特征字节序列
# 在Java中，中文注释通常以 "/*" 或 "//" 开头

# 查找 "/*" 后面跟着中文字节的模式
print("\nSearching for comment patterns...")

# 查看前2000字节的内容
print("\nFirst 500 bytes (hex):")
for i in range(0, min(500, len(data)), 16):
    hex_str = ' '.join(f'{b:02x}' for b in data[i:i+16])
    ascii_str = ''.join(chr(b) if 32 <= b < 127 else '.' for b in data[i:i+16])
    print(f"{i:04x}: {hex_str:<48} {ascii_str}")

# 查找UTF-8中文字节的模式 (0xE0-0xEF, 0x80-0xBF, 0x80-0xBF)
print("\nSearching for UTF-8 Chinese byte sequences...")
utf8_count = 0
for i in range(len(data) - 2):
    if (0xE4 <= data[i] <= 0xEF and 
        0x80 <= data[i+1] <= 0xBF and 
        0x80 <= data[i+2] <= 0xBF):
        utf8_count += 1
        if utf8_count <= 5:
            print(f"  Found at offset {i}: {data[i]:02x} {data[i+1]:02x} {data[i+2]:02x}")

print(f"  Total UTF-8-like sequences: {utf8_count}")

# 查找GBK中文字节的模式 (0xB0-0xF7, 0xA1-0xFE)
print("\nSearching for GBK Chinese byte sequences...")
gbk_count = 0
for i in range(len(data) - 1):
    if (0xB0 <= data[i] <= 0xF7 and 
        0xA1 <= data[i+1] <= 0xFE):
        gbk_count += 1
        if gbk_count <= 5:
            print(f"  Found at offset {i}: {data[i]:02x} {data[i+1]:02x}")

print(f"  Total GBK-like sequences: {gbk_count}")

# 如果UTF-8序列多，文件可能是UTF-8编码
# 如果GBK序列多，文件可能是GBK编码
if utf8_count > gbk_count * 2:
    print("\nConclusion: File appears to be UTF-8 encoded")
elif gbk_count > utf8_count * 2:
    print("\nConclusion: File appears to be GBK encoded")
else:
    print("\nConclusion: Mixed or unknown encoding")
