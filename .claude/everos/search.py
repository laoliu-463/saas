"""Memory retrieval utilities."""

from __future__ import annotations

import json
from typing import Any

from client import get_client

# SAAS project user ID — single operator (LaoLiu)
SAAS_USER_ID = "saas-laoliu"


def search_memories(
    query: str,
    *,
    user_id: str = SAAS_USER_ID,
    method: str = "hybrid",
    memory_types: list[str] | None = None,
    top_k: int = 5,
) -> list[dict[str, Any]]:
    """Search memories and return a flat list of result dicts."""
    client = get_client()
    if memory_types is None:
        memory_types = ["episodic_memory", "profile", "agent_memory", "raw_message"]

    resp = client.v1.memories.search(
        filters={"user_id": user_id},
        query=query,
        method=method,
        memory_types=memory_types,
        top_k=top_k,
    )
    return _flatten_results(resp)


def get_recent_episodes(
    *,
    user_id: str = SAAS_USER_ID,
    page_size: int = 10,
) -> list[dict[str, Any]]:
    """Fetch most recent episodic memories."""
    client = get_client()
    resp = client.v1.memories.get(
        filters={"user_id": user_id},
        memory_type="episodic_memory",
        page=1,
        page_size=page_size,
    )
    episodes = getattr(resp.data, "episodes", [])
    return [_obj_to_dict(ep) for ep in episodes]


def _flatten_results(resp) -> list[dict[str, Any]]:
    """Flatten a search response into a uniform list."""
    data = resp.data
    results = []
    # Keys that may contain result arrays in the search response
    for key in ("episodes", "profiles", "agent_memory", "raw_messages", "agent_cases", "agent_skills"):
        items = getattr(data, key, None)
        if items:
            for item in items:
                d = _obj_to_dict(item)
                d["_type"] = key.rstrip("s")
                results.append(d)
    return results


def _obj_to_dict(obj) -> dict[str, Any]:
    """Convert a response object to a plain dict."""
    if hasattr(obj, "model_dump"):
        return obj.model_dump()
    if hasattr(obj, "dict"):
        return obj.dict()
    return vars(obj)
