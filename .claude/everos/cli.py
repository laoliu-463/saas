#!/usr/bin/env python3
"""CLI for EverOS memory operations in the SAAS project.

Usage:
    python -m .claude.everos.cli ingest-session "session summary text"
    python -m .claude.everos.cli search "活动分配" --top-k 10
    python -m .claude.everos.cli recent --count 5
    python -m .claude.everos.cli flush
    python -m .claude.everos.cli ingest-doc docs/05-API契约总表.md
    python -m .claude.everos.cli status
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

# Force UTF-8 on Windows to avoid GBK garbling
if sys.platform == "win32":
    os.environ.setdefault("PYTHONIOENCODING", "utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


def _add_project_root():
    """Add project root to sys.path so .claude.everos resolves."""
    root = Path(__file__).resolve().parent.parent.parent
    if str(root) not in sys.path:
        sys.path.insert(0, str(root))


def cmd_ingest_session(args):
    from ingest import ingest_session
    resp = ingest_session(args.text, session_id=args.session)
    print(json.dumps(_to_dict(resp), indent=2, ensure_ascii=False))


def cmd_search(args):
    from search import search_memories
    results = search_memories(
        args.query,
        method=args.method,
        top_k=args.top_k,
    )
    for i, r in enumerate(results, 1):
        rtype = r.pop("_type", "unknown")
        print(f"\n--- Result {i} ({rtype}) ---")
        print(json.dumps(r, indent=2, ensure_ascii=False, default=str))
    if not results:
        print("No memories found.")


def cmd_recent(args):
    from search import get_recent_episodes
    episodes = get_recent_episodes(page_size=args.count)
    for i, ep in enumerate(episodes, 1):
        print(f"\n--- Episode {i} ---")
        subject = ep.get("subject", "")
        summary = ep.get("summary", ep.get("episode", "")[:200])
        ts = ep.get("timestamp", "")
        print(f"  Subject: {subject}")
        print(f"  Summary: {summary}")
        print(f"  Time:    {ts}")
    if not episodes:
        print("No episodes found.")


def cmd_flush(args):
    from ingest import flush
    resp = flush()
    print(json.dumps(_to_dict(resp), indent=2, ensure_ascii=False))


def cmd_ingest_doc(args):
    """Ingest a markdown doc as domain knowledge."""
    from ingest import ingest_domain_knowledge

    path = Path(args.file)
    if not path.exists():
        # Try relative to project root
        root = Path(__file__).resolve().parent.parent.parent
        path = root / args.file
    if not path.exists():
        print(f"File not found: {args.file}", file=sys.stderr)
        sys.exit(1)

    text = path.read_text(encoding="utf-8")
    domain = args.domain or path.stem

    # Split into chunks of ~500 words to stay under message limits
    words = text.split()
    chunks = []
    chunk_size = 500
    for i in range(0, len(words), chunk_size):
        chunks.append(" ".join(words[i : i + chunk_size]))

    resp = ingest_domain_knowledge(domain, chunks)
    print(f"Ingested {len(chunks)} chunks from {args.file} as domain '{domain}'")
    print(json.dumps(_to_dict(resp), indent=2, ensure_ascii=False))


def cmd_status(args):
    """Check EverOS connectivity and show user memories summary."""
    from client import get_client
    from search import get_recent_episodes, SAAS_USER_ID

    try:
        client = get_client()
        episodes = get_recent_episodes(page_size=3)
        total = len(episodes)
        print(f"EverOS connection: OK")
        print(f"User ID: {SAAS_USER_ID}")
        print(f"Recent episodes: {total}")
        for ep in episodes:
            print(f"  - {ep.get('subject', ep.get('episode', '')[:80])}")
    except Exception as e:
        print(f"EverOS connection: FAILED - {e}", file=sys.stderr)
        sys.exit(1)


def _to_dict(obj) -> dict:
    if hasattr(obj, "model_dump"):
        return obj.model_dump()
    if hasattr(obj, "dict"):
        return obj.dict()
    return vars(obj)


def main():
    _add_project_root()

    parser = argparse.ArgumentParser(
        prog="everos-cli",
        description="EverOS memory operations for SAAS project",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    # ingest-session
    p = sub.add_parser("ingest-session", help="Ingest session summary")
    p.add_argument("text", help="Session summary text")
    p.add_argument("--session", help="Session ID (auto-generated if omitted)")

    # search
    p = sub.add_parser("search", help="Search memories")
    p.add_argument("query", help="Search query")
    p.add_argument("--method", default="hybrid", choices=["keyword", "vector", "hybrid", "agentic"])
    p.add_argument("--top-k", type=int, default=5)

    # recent
    p = sub.add_parser("recent", help="Show recent episodes")
    p.add_argument("--count", type=int, default=5)

    # flush
    sub.add_parser("flush", help="Trigger memory extraction")

    # ingest-doc
    p = sub.add_parser("ingest-doc", help="Ingest a markdown doc as domain knowledge")
    p.add_argument("file", help="Path to markdown file")
    p.add_argument("--domain", help="Domain label (defaults to filename)")

    # status
    sub.add_parser("status", help="Check EverOS connectivity")

    args = parser.parse_args()
    cmd_map = {
        "ingest-session": cmd_ingest_session,
        "search": cmd_search,
        "recent": cmd_recent,
        "flush": cmd_flush,
        "ingest-doc": cmd_ingest_doc,
        "status": cmd_status,
    }
    cmd_map[args.command](args)


if __name__ == "__main__":
    main()
