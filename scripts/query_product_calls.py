import sqlite3
from pathlib import Path
from collections import defaultdict

db_path = Path(__file__).parent.parent / '.code-review-graph' / 'graph.db'
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

print('=' * 100)
print('Product Domain Call Graph')
print('=' * 100)

# 1. 查找 ProductService 的所有调用者（谁调用 ProductService）
print('\n' + '=' * 100)
print('1. Who calls ProductService')
print('=' * 100)

cursor.execute("""
    SELECT DISTINCT
        e.source_qualified as caller,
        e.kind as edge_type,
        e.file_path,
        e.line
    FROM edges e
    WHERE e.target_qualified LIKE '%ProductService%'
       AND e.kind = 'CALLS'
    ORDER BY e.source_qualified
    LIMIT 50
""")

callers = cursor.fetchall()
if callers:
    for caller, edge_type, file_path, line in callers:
        short_file = file_path.replace('\\', '/').split('/')[-1] if file_path else 'N/A'
        # 只显示类名，去掉包名
        caller_short = caller.split('::')[-1] if '::' in caller else caller
        print(f'  - {caller_short} ({short_file}:{line})')
else:
    print('  (No callers found)')

# 2. ProductService 调用的服务
print('\n' + '=' * 100)
print('2. What ProductService calls')
print('=' * 100)

cursor.execute("""
    SELECT DISTINCT
        e.target_qualified as callee,
        e.kind as edge_type,
        e.file_path,
        e.line
    FROM edges e
    WHERE e.source_qualified LIKE '%ProductService%'
       AND e.kind = 'CALLS'
    ORDER BY e.target_qualified
    LIMIT 80
""")

callees = cursor.fetchall()
if callees:
    for callee, edge_type, file_path, line in callees:
        # 只显示有意义的调用，过滤掉基础类型
        if any(skip in callee for skip in ['String.', 'Integer.', 'Long.', 'Boolean.',
                                           'List.', 'Map.', 'Set.', 'Optional.', 'Object.']):
            continue
        callee_short = callee.split('::')[-1] if '::' in callee else callee
        short_file = file_path.replace('\\', '/').split('/')[-1] if file_path else 'N/A'
        print(f'  - {callee_short}')
else:
    print('  (No callees found)')

# 3. 商品域类之间的调用关系
print('\n' + '=' * 100)
print('3. Product Domain Class-Level Dependencies')
print('=' * 100)

cursor.execute("""
    SELECT DISTINCT
        e.source_qualified,
        e.target_qualified,
        e.kind,
        e.line
    FROM edges e
    WHERE (e.source_qualified LIKE '%Product%' OR e.target_qualified LIKE '%Product%')
       AND e.kind IN ('CALLS', 'USES', 'IMPORTS_FROM')
    ORDER BY e.source_qualified, e.target_qualified
    LIMIT 100
""")

class_deps = cursor.fetchall()
if class_deps:
    for source, target, kind, line in class_deps:
        # 提取类名
        source_class = source.split('::')[0].split('.')[-1] if '::' in source or '.' in source else source
        target_class = target.split('::')[0].split('.')[-1] if '::' in target or '.' in target else target

        # 只显示有意义的类间依赖
        if source_class == target_class:
            continue
        if any(skip in target_class for skip in ['String', 'Integer', 'Long', 'Boolean', 'List',
                                                  'Map', 'Set', 'Optional', 'Object', 'Class']):
            continue

        arrow = '-->' if kind == 'CALLS' else '-..>' if kind == 'IMPORTS_FROM' else '-->'
        print(f'  {source_class} {arrow} {target_class}')
else:
    print('  (No class-level dependencies found)')

# 4. 商品域的外部依赖（其他域的服务）
print('\n' + '=' * 100)
print('4. External Dependencies (Product -> Other Domains)')
print('=' * 100)

cursor.execute("""
    SELECT DISTINCT
        e.source_qualified,
        e.target_qualified,
        e.kind
    FROM edges e
    WHERE e.source_qualified LIKE '%Product%'
       AND e.target_qualified NOT LIKE '%Product%'
       AND e.kind = 'CALLS'
       AND e.target_qualified NOT LIKE '%String%'
       AND e.target_qualified NOT LIKE '%Integer%'
       AND e.target_qualified NOT LIKE '%Long%'
       AND e.target_qualified NOT LIKE '%Boolean%'
       AND e.target_qualified NOT LIKE '%List%'
       AND e.target_qualified NOT LIKE '%Map%'
       AND e.target_qualified NOT LIKE '%Set%'
       AND e.target_qualified NOT LIKE '%Optional%'
       AND e.target_qualified NOT LIKE '%Object%'
    ORDER BY e.target_qualified
    LIMIT 50
""")

external_deps = cursor.fetchall()
if external_deps:
    # 按目标服务分组
    deps_by_target = defaultdict(list)
    for source, target, kind in external_deps:
        target_class = target.split('::')[0].split('.')[-1] if '::' in target or '.' in target else target
        source_class = source.split('::')[0].split('.')[-1] if '::' in source or '.' in source else source
        deps_by_target[target_class].append(source_class)

    for target_class, callers in sorted(deps_by_target.items()):
        unique_callers = sorted(set(callers))
        print(f'  {target_class}:')
        for c in unique_callers[:5]:
            print(f'    - {c}')
        if len(unique_callers) > 5:
            print(f'    ... and {len(unique_callers) - 5} more')
else:
    print('  (No external dependencies found)')

# 5. 商品域被其他域调用
print('\n' + '=' * 100)
print('5. External Callers (Other Domains -> Product)')
print('=' * 100)

cursor.execute("""
    SELECT DISTINCT
        e.source_qualified,
        e.target_qualified,
        e.kind
    FROM edges e
    WHERE e.target_qualified LIKE '%Product%'
       AND e.source_qualified NOT LIKE '%Product%'
       AND e.kind = 'CALLS'
       AND e.source_qualified NOT LIKE '%String%'
       AND e.source_qualified NOT LIKE '%Integer%'
       AND e.source_qualified NOT LIKE '%Long%'
       AND e.source_qualified NOT LIKE '%Boolean%'
       AND e.source_qualified NOT LIKE '%List%'
       AND e.source_qualified NOT LIKE '%Map%'
       AND e.source_qualified NOT LIKE '%Set%'
       AND e.source_qualified NOT LIKE '%Optional%'
       AND e.source_qualified NOT LIKE '%Object%'
    ORDER BY e.source_qualified
    LIMIT 50
""")

external_callers = cursor.fetchall()
if external_callers:
    # 按源服务分组
    callers_by_source = defaultdict(list)
    for source, target, kind in external_callers:
        target_class = target.split('::')[0].split('.')[-1] if '::' in target or '.' in target else target
        source_class = source.split('::')[0].split('.')[-1] if '::' in source or '.' in source else source
        callers_by_source[source_class].append(target_class)

    for source_class, targets in sorted(callers_by_source.items()):
        unique_targets = sorted(set(targets))
        print(f'  {source_class}:')
        for t in unique_targets[:5]:
            print(f'    - calls {t}')
        if len(unique_targets) > 5:
            print(f'    ... and {len(unique_targets) - 5} more')
else:
    print('  (No external callers found)')

conn.close()
