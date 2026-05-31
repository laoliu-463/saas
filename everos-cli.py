#!/usr/bin/env python3
"""Runner script for EverOS CLI.

Adds .claude/everos/ to sys.path so direct imports work,
then delegates to cli.main().
"""

import sys
from pathlib import Path

_EVEROS_DIR = str(Path(__file__).resolve().parent / ".claude" / "everos")
sys.path.insert(0, _EVEROS_DIR)

from cli import main  # noqa: E402

if __name__ == "__main__":
    main()
