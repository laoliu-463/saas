import sqlite3
from pathlib import Path

db_path = Path(__file__).parent.parent / '.code-review-graph' / 'graph.db'
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# 查找商品域相关节点
cursor.execute("""
    SELECT name, kind, file_path, line_start, line_end, is_test
    FROM nodes
    WHERE name LIKE '%Product%'
       OR file_path LIKE '%product%'
    ORDER BY kind, name
    LIMIT 100
""")

print('=' * 80)
print('Product Domain Nodes')
print('=' * 80)

current_kind = None
for row in cursor.fetchall():
    name, kind, file_path, line_start, line_end, is_test = row
    if kind != current_kind:
        print(f'\n[{kind}]')
        current_kind = kind

    # 简化文件路径
    if file_path:
        parts = file_path.replace('\\', '/').split('/')
        short_file = '/'.join(parts[-2:]) if len(parts) > 1 else parts[0]
    else:
        short_file = 'N/A'

    # 标记测试文件
    test_mark = ' [TEST]' if is_test else ''
    line_info = f' (L{line_start}-{line_end})' if line_start else ''

    print(f'  - {name}{test_mark}')
    print(f'    File: {short_file}{line_info}')

conn.close()
