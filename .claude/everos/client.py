"""EverOS client initialization with project defaults."""

import os
from pathlib import Path

_ENV_FILE = Path(__file__).resolve().parent.parent.parent / ".everos.env"


def _load_api_key() -> str:
    """Load API key from .everos.env or environment."""
    key = os.environ.get("EVEROS_API_KEY", "")
    if not key and _ENV_FILE.exists():
        for line in _ENV_FILE.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                if k.strip() == "EVEROS_API_KEY":
                    key = v.strip()
                    os.environ["EVEROS_API_KEY"] = key
                    break
    if not key:
        raise RuntimeError(
            "EVEROS_API_KEY not set. "
            "Set it in .everos.env or as an environment variable."
        )
    return key


_client = None


def get_client():
    """Return a cached EverOS sync client (lazy singleton)."""
    global _client
    if _client is None:
        from everos import EverOS
        _client = EverOS(api_key=_load_api_key())
    return _client
