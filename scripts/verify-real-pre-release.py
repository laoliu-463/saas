#!/usr/bin/env python3
"""Validate the immutable release manifest used by the real-pre CD queue."""

from __future__ import annotations

import argparse
import json
import re
import shlex
import sys
from pathlib import Path
from typing import Any


SHA40 = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
REPOSITORY = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/-]*$")


def fail(message: str) -> None:
    raise ValueError(message)


def required_string(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        fail(f"{name} must be a non-empty string")
    return value.strip()


def validate_sha(value: Any, name: str) -> str:
    value = required_string(value, name)
    if not SHA40.fullmatch(value):
        fail(f"{name} must be a 40-character lowercase Git SHA")
    return value


def validate_digest(value: Any, name: str) -> str:
    value = required_string(value, name)
    if not DIGEST.fullmatch(value):
        fail(f"{name} must be a content-addressed sha256 digest")
    return value


def validate_image(image: Any, name: str) -> dict[str, str]:
    if not isinstance(image, dict):
        fail(f"{name} must be an object")
    repository = required_string(image.get("repository"), f"{name}.repository")
    if not REPOSITORY.fullmatch(repository) or ":" in repository or "@" in repository:
        fail(f"{name}.repository must be a repository name without tag or digest")
    digest = validate_digest(image.get("digest"), f"{name}.digest")
    return {"repository": repository, "digest": digest}


def validate_release_pointer(pointer: Any, name: str) -> dict[str, Any]:
    if not isinstance(pointer, dict):
        fail(f"{name} must be an object")
    source_sha = validate_sha(pointer.get("sourceMainSha"), f"{name}.sourceMainSha")
    images = pointer.get("images")
    if not isinstance(images, dict):
        fail(f"{name}.images must be an object")
    backend = validate_image(images.get("backend"), f"{name}.images.backend")
    frontend = validate_image(images.get("frontend"), f"{name}.images.frontend")
    return {
        "sourceMainSha": source_sha,
        "images": {"backend": backend, "frontend": frontend},
    }


def load_manifest(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        fail(f"manifest not found: {path}")
    except json.JSONDecodeError as exc:
        fail(f"manifest is not valid JSON: {exc}")

    if not isinstance(data, dict):
        fail("manifest root must be an object")
    if data.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")

    source_sha = validate_sha(data.get("sourceMainSha"), "sourceMainSha")
    images = data.get("images")
    if not isinstance(images, dict):
        fail("images must be an object")
    backend = validate_image(images.get("backend"), "images.backend")
    frontend = validate_image(images.get("frontend"), "images.frontend")

    database = data.get("database")
    if not isinstance(database, dict):
        fail("database must be an object")
    migration_version = required_string(database.get("migrationVersion"), "database.migrationVersion")
    migration_input_sha = validate_digest(database.get("inputSha256"), "database.inputSha256")

    previous = data.get("previous")
    bootstrap = data.get("bootstrap", False)
    if not isinstance(bootstrap, bool):
        fail("bootstrap must be boolean")
    previous_value = None
    if previous is None:
        if not bootstrap:
            fail("previous is required unless bootstrap=true")
    else:
        previous_value = validate_release_pointer(previous, "previous")
        if previous_value["sourceMainSha"] == source_sha:
            fail("previous.sourceMainSha must differ from sourceMainSha")

    return {
        "schemaVersion": 1,
        "sourceMainSha": source_sha,
        "images": {"backend": backend, "frontend": frontend},
        "database": {
            "migrationVersion": migration_version,
            "inputSha256": migration_input_sha,
        },
        "previous": previous_value,
        "bootstrap": bootstrap,
    }


def shell_assignments(manifest: dict[str, Any]) -> str:
    images = manifest["images"]
    previous = manifest["previous"] or {}
    previous_images = previous.get("images", {})
    values = {
        "SOURCE_MAIN_SHA": manifest["sourceMainSha"],
        "IMAGE_TAG": manifest["sourceMainSha"],
        "BACKEND_REPOSITORY": images["backend"]["repository"],
        "BACKEND_IMAGE_DIGEST": images["backend"]["digest"],
        "BACKEND_IMAGE": f"{images['backend']['repository']}@{images['backend']['digest']}",
        "FRONTEND_REPOSITORY": images["frontend"]["repository"],
        "FRONTEND_IMAGE_DIGEST": images["frontend"]["digest"],
        "FRONTEND_IMAGE": f"{images['frontend']['repository']}@{images['frontend']['digest']}",
        "MIGRATION_VERSION": manifest["database"]["migrationVersion"],
        "MIGRATION_INPUT_SHA256": manifest["database"]["inputSha256"],
        "PREVIOUS_SOURCE_MAIN_SHA": previous.get("sourceMainSha", ""),
        "PREVIOUS_BACKEND_IMAGE": "",
        "PREVIOUS_FRONTEND_IMAGE": "",
    }
    if previous_images:
        values["PREVIOUS_BACKEND_IMAGE"] = (
            f"{previous_images['backend']['repository']}@{previous_images['backend']['digest']}"
        )
        values["PREVIOUS_FRONTEND_IMAGE"] = (
            f"{previous_images['frontend']['repository']}@{previous_images['frontend']['digest']}"
        )
    return "".join(f"{key}={shlex.quote(value)}\n" for key, value in values.items())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--format", choices=("summary", "shell"), default="summary")
    args = parser.parse_args()
    try:
        manifest = load_manifest(args.manifest)
    except ValueError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2

    if args.format == "shell":
        sys.stdout.write(shell_assignments(manifest))
    else:
        print(
            "PASS: release manifest validated "
            f"sourceMainSha={manifest['sourceMainSha']} "
            f"backend={manifest['images']['backend']['repository']}@{manifest['images']['backend']['digest']} "
            f"frontend={manifest['images']['frontend']['repository']}@{manifest['images']['frontend']['digest']}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
