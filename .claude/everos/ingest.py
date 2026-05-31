"""Memory ingestion functions for SAAS project events."""

from __future__ import annotations

import time
from pathlib import Path
from typing import Any

from client import get_client

SAAS_USER_ID = "saas-laoliu"
SAAS_GROUP_ID = "saas-dev-team"


def ingest_session(
    summary: str,
    *,
    session_id: str | None = None,
    user_id: str = SAAS_USER_ID,
) -> dict[str, Any]:
    """Ingest a Claude Code session summary as a user message.

    This creates a user-role message so EverOS can extract episodic
    memories from the session content. Call this at the end of each
    session or via a Stop hook.
    """
    client = get_client()
    ts = int(time.time() * 1000)
    return client.v1.memories.add(
        user_id=user_id,
        session_id=session_id or f"claude-session-{ts}",
        messages=[
            {
                "role": "user",
                "timestamp": ts,
                "content": summary,
            }
        ],
    )


def ingest_decisions(
    decisions: list[dict[str, str]],
    *,
    session_id: str | None = None,
    user_id: str = SAAS_USER_ID,
) -> dict[str, Any]:
    """Ingest architectural / domain decisions as agent cases.

    Each decision dict should have:
      - title: short decision name
      - context: why the decision was needed
      - decision: what was decided
      - consequences: expected impact
    """
    client = get_client()
    ts = int(time.time() * 1000)
    messages = []
    for d in decisions:
        content = (
            f"Decision: {d.get('title', 'Untitled')}\n"
            f"Context: {d.get('context', '')}\n"
            f"Decision: {d.get('decision', '')}\n"
            f"Consequences: {d.get('consequences', '')}"
        )
        messages.append(
            {
                "role": "assistant",
                "timestamp": ts,
                "content": content,
            }
        )
        ts += 1  # ensure unique timestamps

    return client.v1.memories.agent.add(
        user_id=user_id,
        session_id=session_id or f"decisions-{int(time.time() * 1000)}",
        messages=messages,
    )


def ingest_domain_knowledge(
    domain: str,
    facts: list[str],
    *,
    user_id: str = SAAS_USER_ID,
) -> dict[str, Any]:
    """Ingest domain-specific knowledge as user messages.

    Use this for domain contracts, invariants, flow descriptions, etc.
    """
    client = get_client()
    ts = int(time.time() * 1000)
    messages = []
    for fact in facts:
        messages.append(
            {
                "role": "user",
                "timestamp": ts,
                "content": f"[{domain}] {fact}",
            }
        )
        ts += 1

    return client.v1.memories.add(
        user_id=user_id,
        session_id=f"domain-{domain}-{int(time.time() * 1000)}",
        messages=messages,
    )


def ingest_agent_interaction(
    user_msg: str,
    assistant_msg: str,
    *,
    session_id: str | None = None,
    user_id: str = SAAS_USER_ID,
) -> dict[str, Any]:
    """Ingest a user-assistant exchange as agent memory.

    Useful for recording how problems were solved in Claude Code.
    """
    client = get_client()
    ts = int(time.time() * 1000)
    return client.v1.memories.agent.add(
        user_id=user_id,
        session_id=session_id or f"agent-{ts}",
        messages=[
            {"role": "user", "timestamp": ts, "content": user_msg},
            {"role": "assistant", "timestamp": ts + 1, "content": assistant_msg},
        ],
    )


def flush(user_id: str = SAAS_USER_ID) -> dict[str, Any]:
    """Trigger memory extraction for accumulated messages."""
    client = get_client()
    return client.v1.memories.flush(user_id=user_id)


def ingest_conversation(
    turns: list[tuple[str, str]],
    *,
    session_id: str | None = None,
    user_id: str = SAAS_USER_ID,
) -> dict[str, Any]:
    """Ingest a full conversation (list of (role, content) tuples).

    This creates a proper multi-turn conversation that EverOS can
    extract episodic memories from. Use for recording how problems
    were solved or decisions were made.
    """
    client = get_client()
    ts = int(time.time() * 1000)
    messages = []
    for i, (role, content) in enumerate(turns):
        messages.append({
            "role": role,
            "timestamp": ts + i,
            "content": content,
        })
    return client.v1.memories.add(
        user_id=user_id,
        session_id=session_id or f"conv-{ts}",
        messages=messages,
    )
