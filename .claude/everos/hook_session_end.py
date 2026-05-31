#!/usr/bin/env python3
"""Stop hook: ingest session summary into EverOS when Claude Code session ends.

Called by the Stop hook in settings.json. Reads the summary from
command-line args or generates a timestamp-based stub.
"""

import sys
import time
from pathlib import Path

# Ensure .claude/everos/ is importable
sys.path.insert(0, str(Path(__file__).resolve().parent))

from ingest import ingest_conversation, flush, SAAS_USER_ID


def main():
    summary = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else None
    if not summary:
        summary = f"Claude Code session ended at {time.strftime('%Y-%m-%d %H:%M:%S')}"

    try:
        resp = ingest_conversation(
            turns=[
                ("user", f"[Session Summary] {summary}"),
            ],
            session_id=f"session-{int(time.time() * 1000)}",
        )
        # Fire-and-forget: print task ID for debugging
        data = getattr(resp, "data", resp)
        print(f"EverOS: session ingested ({getattr(data, 'status', 'ok')})")
    except Exception as e:
        # Don't let EverOS errors break the session exit
        print(f"EverOS: ingestion failed ({e})", file=sys.stderr)


if __name__ == "__main__":
    main()
