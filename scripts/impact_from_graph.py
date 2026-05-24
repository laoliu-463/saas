#!/usr/bin/env python3
"""Blast-radius from .code-review-graph for current git working tree."""
import sqlite3
import subprocess
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DB = REPO / ".code-review-graph" / "graph.db"


def git_changed_files() -> list[str]:
    parts: list[str] = []
    for cmd in (
        ["git", "diff", "--name-only"],
        ["git", "diff", "--cached", "--name-only"],
        ["git", "ls-files", "--others", "--exclude-standard"],
    ):
        parts.append(subprocess.check_output(cmd, cwd=REPO, text=True, errors="replace"))
    files = {line.strip().replace("\\", "/") for chunk in parts for line in chunk.splitlines() if line.strip()}
    return sorted(files)


def normalize_path(p: str) -> str:
    return p.replace("\\", "/").lower()


def file_matches_node(changed: str, node_path: str | None) -> bool:
    if not node_path:
        return False
    np = normalize_path(node_path)
    cp = normalize_path(changed)
    return np.endswith(cp) or cp in np


def main() -> None:
    changed = git_changed_files()
    print(f"changed_files={len(changed)}")

    if not DB.exists():
        print("graph_db_missing")
        return

    conn = sqlite3.connect(DB)
    c = conn.cursor()

    c.execute("SELECT id, name, kind, file_path FROM nodes")
    all_nodes = c.fetchall()

    changed_node_ids: set[int] = set()
    for nid, name, kind, fpath in all_nodes:
        for ch in changed:
            if file_matches_node(ch, fpath):
                changed_node_ids.add(nid)
                break

    print(f"changed_nodes={len(changed_node_ids)}")

    callers_by_file: defaultdict[str, set[str]] = defaultdict(set)
    callees_by_file: defaultdict[str, set[str]] = defaultdict(set)

    def node_info(nid: int):
        c.execute("SELECT name, kind, file_path FROM nodes WHERE id=?", (nid,))
        row = c.fetchone()
        return row if row else ("?", "?", None)

    for nid in changed_node_ids:
        c.execute(
            "SELECT source_id FROM edges WHERE target_id=? AND kind='CALLS'",
            (nid,),
        )
        for (sid,) in c.fetchall():
            name, kind, fpath = node_info(sid)
            if fpath:
                callers_by_file[fpath].add(f"{kind}:{name}")

        c.execute(
            "SELECT target_id FROM edges WHERE source_id=? AND kind='CALLS'",
            (nid,),
        )
        for (tid,) in c.fetchall():
            name, kind, fpath = node_info(tid)
            if fpath:
                callees_by_file[fpath].add(f"{kind}:{name}")

    def print_top(title: str, mapping: dict, limit: int = 40):
        print(f"\n=== {title} ===")
        for fpath, refs in sorted(mapping.items(), key=lambda x: (-len(x[1]), x[0]))[:limit]:
            short = fpath.replace("\\", "/")
            if "SAAS" in short:
                short = short.split("SAAS/")[-1]
            print(f"{short}\t{len(refs)}")

    print_top("CALLERS (into changed)", callers_by_file)
    print_top("CALLEES (from changed)", callees_by_file)

    # Classify changed backend layers
    layers = defaultdict(list)
    for ch in changed:
        if ch.startswith("backend/src/main/java/"):
            if "/controller/" in ch:
                layers["controller"].append(ch)
            elif "/service/" in ch:
                layers["service"].append(ch)
            elif "/mapper/" in ch:
                layers["mapper"].append(ch)
            elif "/entity/" in ch:
                layers["entity"].append(ch)
            elif "/dto/" in ch or "/vo/" in ch:
                layers["dto_vo"].append(ch)
        elif ch.startswith("backend/src/test/"):
            layers["test"].append(ch)
        elif ch.startswith("frontend/src/views/"):
            layers["frontend_views"].append(ch)
        elif ch.startswith("frontend/src/api/"):
            layers["frontend_api"].append(ch)

    print("\n=== LAYER COUNTS ===")
    for k, v in sorted(layers.items()):
        print(f"{k}\t{len(v)}")

    conn.close()


if __name__ == "__main__":
    main()
