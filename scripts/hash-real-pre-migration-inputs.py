#!/usr/bin/env python3
"""Create a deterministic digest for the migration inputs consumed by CD."""

from __future__ import annotations

import argparse
import hashlib
import subprocess
from pathlib import PurePosixPath


PREFIXES = (
    "backend/src/main/resources/db/migration/",
    "scripts/run-real-pre-db-migrations.sh",
    "scripts/check-real-pre-schema.sh",
)


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ref", default="HEAD")
    args = parser.parse_args()

    names = git("ls-tree", "-r", "--name-only", args.ref).decode().splitlines()
    selected = sorted(name for name in names if name.startswith(PREFIXES))
    digest = hashlib.sha256()
    for name in selected:
        digest.update(name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(git("show", f"{args.ref}:{name}"))
        digest.update(b"\0")
    print(f"sha256:{digest.hexdigest()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
