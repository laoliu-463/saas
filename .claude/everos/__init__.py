"""
EverOS memory layer integration for SAAS project.

All modules use direct imports (not relative) because the parent
directory '.claude' starts with a dot, which breaks Python's
relative import resolution.

The runner script (everos-cli.py) adds this directory to sys.path
before loading modules.
"""

from client import get_client
from ingest import (
    ingest_session,
    ingest_decisions,
    ingest_domain_knowledge,
    ingest_conversation,
    ingest_agent_interaction,
    flush,
)
from search import search_memories, get_recent_episodes

__all__ = [
    "get_client",
    "ingest_session",
    "ingest_decisions",
    "ingest_domain_knowledge",
    "ingest_conversation",
    "ingest_agent_interaction",
    "flush",
    "search_memories",
    "get_recent_episodes",
]
