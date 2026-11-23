#!/usr/bin/env python3
"""Fail if somebody reintroduces hidden framework APIs to normal app code."""
from pathlib import Path
source = Path(__file__).resolve().parents[1] / "app/src/main/java"
blocked = (".getHomeActivities(", ".getRoleHolders(")
for file in source.rglob("*.java"):
    text = file.read_text(encoding="utf-8")
    for token in blocked:
        # Allow comments to document the removed APIs.
        for number, line in enumerate(text.splitlines(), 1):
            if token in line and not line.lstrip().startswith(("//", "*")):
                raise SystemExit(f"[FAIL] System-only SDK call {token} at {file}:{number}")
print("[OK] No hidden HOME/RoleManager SDK method calls")
