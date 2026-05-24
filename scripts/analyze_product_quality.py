"""Analyze ProductService.java for code quality issues."""
import re
import sys

FILE = r"D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\service\ProductService.java"

with open(FILE, encoding="utf-8") as f:
    lines = f.readlines()

# --- 1. Find method signatures and compute lengths ---
decl_pat = re.compile(r'^\s+(public|private|protected)\s+')
skip_keywords = {'class', 'interface', 'enum', 'record', 'static', 'final', 'new'}

methods = []
for i, line in enumerate(lines, 1):
    if not decl_pat.match(line):
        continue
    # find opening paren
    paren_idx = line.find('(')
    if paren_idx < 0:
        continue
    before_paren = line[:paren_idx].rstrip()
    # extract last word as method name
    parts = before_paren.split()
    if not parts:
        continue
    name = parts[-1]
    # skip constructors, keywords, annotations
    if name in skip_keywords or not name[0].isalpha():
        continue
    methods.append((i, name))

# compute line spans (next method start - current method start)
method_spans = []
for idx, (start, name) in enumerate(methods):
    if idx + 1 < len(methods):
        end = methods[idx + 1][0] - 1
    else:
        end = len(lines)
    span = end - start + 1
    method_spans.append((name, start, end, span))

# Sort by span descending
long_methods = sorted(method_spans, key=lambda x: -x[3])

print("=" * 70)
print("1. TOP 30 LONGEST METHODS")
print("=" * 70)
for name, start, end, span in long_methods[:30]:
    flag = " *** GOD METHOD" if span > 100 else (" ** LONG" if span > 50 else "")
    print(f"  {name:50s}  L{start:4d}-L{end:4d}  ({span:3d} lines){flag}")

# --- 2. Count methods > 50 lines ---
over_50 = [m for m in method_spans if m[3] > 50]
over_100 = [m for m in method_spans if m[3] > 100]
print(f"\n  Methods > 50 lines: {len(over_50)}")
print(f"  Methods > 100 lines: {len(over_100)}")

# --- 3. Deep nesting analysis ---
print("\n" + "=" * 70)
print("2. DEEP NESTING (>4 levels)")
print("=" * 70)

deep_lines = []
current_method = None
method_idx = 0

for i, line in enumerate(lines, 1):
    # track current method
    while method_idx < len(methods) and methods[method_idx][0] <= i:
        current_method = methods[method_idx][1]
        method_idx += 1

    stripped = line.rstrip('\n')
    if not stripped or stripped.lstrip().startswith('//') or stripped.lstrip().startswith('*'):
        continue

    leading = len(stripped) - len(stripped.lstrip())
    level = leading // 4  # assume 4-space indent
    if level > 4:
        deep_lines.append((i, level, current_method, stripped.strip()[:60]))

# Count by method
from collections import Counter
deep_by_method = Counter()
deep_max_by_method = {}
for lineno, level, method, _ in deep_lines:
    deep_by_method[method] += 1
    if method not in deep_max_by_method or level > deep_max_by_method[method]:
        deep_max_by_method[method] = level

print(f"  Total deeply nested lines (>4 levels): {len(deep_lines)}")
print(f"  Methods with deep nesting:")
for method, count in deep_by_method.most_common(20):
    max_lvl = deep_max_by_method[method]
    print(f"    {method:50s}  {count:3d} lines, max level {max_lvl}")

# --- 4. Naming analysis ---
print("\n" + "=" * 70)
print("3. METHOD NAMING PATTERNS")
print("=" * 70)

prefixes = Counter()
for _, name, _, _ in method_spans:
    if isinstance(name, str):
        prefixes[name[:5]] += 1

print("  Method name prefixes (top 20):")
for prefix, count in prefixes.most_common(20):
    print(f"    '{prefix}': {count}")

# --- 5. Responsibility analysis ---
print("\n" + "=" * 70)
print("4. RESPONSIBILITY CLUSTERS")
print("=" * 70)

clusters = {
    "Filter/Match (筛选)": [],
    "Snapshot/State (快照/状态)": [],
    "ActivityProduct (活动商品)": [],
    "Promotion (推广)": [],
    "Audit (审核)": [],
    "TalentFollow (达人跟单)": [],
    "Display/View (展示)": [],
    "ColonelBuyinId (团长百应ID)": [],
    "Parse/Format (解析/格式化)": [],
    "Other": []
}

def classify(name):
    n = name.lower()
    if 'filter' in n or 'match' in n or 'selectedlibrary' in n:
        return "Filter/Match (筛选)"
    elif 'snapshot' in n or 'operationstate' in n or 'operationlog' in n:
        return "Snapshot/State (快照/状态)"
    elif 'activityproduct' in n or 'activity' in n:
        return "ActivityProduct (活动商品)"
    elif 'promotion' in n or 'promolink' in n:
        return "Promotion (推广)"
    elif 'audit' in n:
        return "Audit (审核)"
    elif 'talent' in n or 'follow' in n:
        return "TalentFollow (达人跟单)"
    elif 'display' in n or 'view' in n or 'tag' in n or 'mark' in n:
        return "Display/View (展示)"
    elif 'colonel' in n or 'buyinid' in n:
        return "ColonelBuyinId (团长百应ID)"
    elif 'parse' in n or 'format' in n or 'read' in n or 'normalize' in n or 'resolve' in n or 'safe' in n:
        return "Parse/Format (解析/格式化)"
    else:
        return "Other"

for name, start, end, span in method_spans:
    cat = classify(name)
    clusters[cat].append((name, span))

for cat, items in sorted(clusters.items(), key=lambda x: -len(x[1])):
    total_lines = sum(s for _, s in items)
    print(f"\n  {cat}: {len(items)} methods, {total_lines} total lines")
    for name, span in items[:5]:
        print(f"    - {name} ({span} lines)")
    if len(items) > 5:
        print(f"    ... and {len(items)-5} more")

# --- 6. Duplicate pattern: matches*Filter methods ---
print("\n" + "=" * 70)
print("5. POTENTIAL DUPLICATION: matches*Filter methods")
print("=" * 70)

filter_methods = [(n, s, e, sp) for n, s, e, sp in method_spans if n.startswith('matches')]
print(f"  Total matches* methods: {len(filter_methods)}")
total_filter_lines = sum(sp for _, _, _, sp in filter_methods)
print(f"  Total lines: {total_filter_lines}")
print(f"  Average: {total_filter_lines // len(filter_methods) if filter_methods else 0} lines each")
print("  These likely follow a repetitive pattern and could be consolidated.")

# --- 7. parse*/read* duplication ---
print("\n" + "=" * 70)
print("6. POTENTIAL DUPLICATION: parse*/read* utility methods")
print("=" * 70)

util_methods = [(n, s, e, sp) for n, s, e, sp in method_spans
                if n.startswith('parse') or n.startswith('read') or n.startswith('format')
                or n.startswith('normalize') or n.startswith('safe')]
print(f"  Total utility methods: {len(util_methods)}")
total_util_lines = sum(sp for _, _, _, sp in util_methods)
print(f"  Total lines: {total_util_lines}")

# --- 8. Map<String, Object> usage ---
print("\n" + "=" * 70)
print("7. Map<String, Object> OVERUSE (type-safety concern)")
print("=" * 70)

map_count = 0
for line in lines:
    map_count += line.count('Map<String, Object>')
print(f"  Occurrences of Map<String, Object>: {map_count}")
print("  This suggests missing DTOs/records for structured data.")

# --- Summary ---
print("\n" + "=" * 70)
print("SUMMARY")
print("=" * 70)
print(f"  File: {len(lines)} lines, {len(methods)} methods")
print(f"  Long methods (>50 lines): {len(over_50)}")
print(f"  God methods (>100 lines): {len(over_100)}")
print(f"  Deeply nested lines: {len(deep_lines)}")
print(f"  Filter/match methods: {len(filter_methods)} ({total_filter_lines} lines)")
print(f"  Utility methods: {len(util_methods)} ({total_util_lines} lines)")
print(f"  Map<String,Object> uses: {map_count}")
