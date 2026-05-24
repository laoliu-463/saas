import sqlite3
from pathlib import Path
from collections import defaultdict

db_path = Path(__file__).parent.parent / '.code-review-graph' / 'graph.db'
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

print('=' * 100)
print('Product Domain External Call Graph')
print('=' * 100)

# Who calls ProductService (external callers only)
print()
print('1. External Classes/Services that Call ProductService')
print('=' * 100)

cursor.execute("""
    SELECT DISTINCT
        e.source_qualified as caller,
        e.file_path,
        e.line
    FROM edges e
    WHERE e.target_qualified LIKE '%ProductService%'
       AND e.kind = 'CALLS'
       AND e.source_qualified NOT LIKE '%ProductService%'
    ORDER BY e.source_qualified
    LIMIT 50
""")

callers = cursor.fetchall()
callers_by_class = defaultdict(list)
for caller, file_path, line in callers:
    caller_class = caller.split('::')[0] if '::' in caller else caller.split('.')[0]
    short_file = file_path.replace('\\', '/').split('/')[-1] if file_path else 'N/A'
    callers_by_class[caller_class].append(f'{short_file}:{line}')

for cls, locations in sorted(callers_by_class.items()):
    print(f'\n  {cls}:')
    for loc in sorted(set(locations))[:3]:
        print(f'    - {loc}')
    if len(set(locations)) > 3:
        print(f'    ... and {len(set(locations)) - 3} more')

# What ProductService calls (external services only)
print()
print()
print('2. External Services Called by ProductService')
print('=' * 100)

cursor.execute("""
    SELECT DISTINCT
        e.target_qualified as callee,
        e.file_path,
        e.line
    FROM edges e
    WHERE e.source_qualified LIKE '%ProductService%'
       AND e.kind = 'CALLS'
       AND e.target_qualified NOT LIKE '%ProductService%'
       AND e.target_qualified NOT LIKE '%String%'
       AND e.target_qualified NOT LIKE '%Integer%'
       AND e.target_qualified NOT LIKE '%Long%'
       AND e.target_qualified NOT LIKE '%Boolean%'
       AND e.target_qualified NOT LIKE '%List%'
       AND e.target_qualified NOT LIKE '%Map%'
       AND e.target_qualified NOT LIKE '%Set%'
       AND e.target_qualified NOT LIKE '%Optional%'
       AND e.target_qualified NOT LIKE '%Object%'
       AND e.target_qualified NOT LIKE '%Arrays%'
       AND e.target_qualified NOT LIKE '%Collections%'
    ORDER BY e.target_qualified
    LIMIT 80
""")

callees = cursor.fetchall()
callees_by_class = defaultdict(int)
for callee, file_path, line in callees:
    callee_class = callee.split('::')[0] if '::' in callee else callee.split('.')[0]
    callees_by_class[callee_class] += 1

print('\n  Dependency Frequency:')
for cls, count in sorted(callees_by_class.items(), key=lambda x: -x[1]):
    if cls not in ['java', 'javax', 'org', 'com', 'lombok']:
        print(f'    {cls}: {count} call(s)')

# Cross-domain calls
print()
print()
print('3. Other Domains That Call ProductService')
print('=' * 100)

cursor.execute("""
    SELECT DISTINCT
        e.source_qualified,
        e.file_path
    FROM edges e
    WHERE e.target_qualified LIKE '%ProductService%'
       AND e.kind = 'CALLS'
       AND e.source_qualified NOT LIKE '%ProductService%'
       AND e.source_qualified NOT LIKE '%Test%'
       AND e.source_qualified NOT LIKE '%test%'
    ORDER BY e.source_qualified
""")

external_callers = cursor.fetchall()
domains = defaultdict(list)
for source, file_path in external_callers:
    if file_path:
        parts = file_path.replace('\\', '/').split('/')
        # Extract domain from file path
        if 'src/main/java' in file_path:
            idx = parts.index('java')
            if idx + 2 < len(parts):
                domain = parts[idx + 2]  # e.g., controller, service
                cls = source.split('::')[0] if '::' in source else source.split('.')[0]
                domains[domain].append(cls)

for domain, classes in sorted(domains.items()):
    unique_classes = sorted(set(classes))
    print(f'\n  [{domain}]')
    for cls in unique_classes[:5]:
        print(f'    - {cls}')
    if len(unique_classes) > 5:
        print(f'    ... and {len(unique_classes) - 5} more')

conn.close()
