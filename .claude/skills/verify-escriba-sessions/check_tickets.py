"""Check, on the real MainActivity.java, that every engine callback is ticket-guarded.

Each method of the RemoteEngine.Listener and NativeEngine.Listener created in
begin() must compare its ticket with `generation`, or hand the ticket to
complete()/failed(), and those two must return early on a stale ticket. This
is the guard Sessions.tla's Deliver step models.
Usage: python3 .claude/skills/verify-escriba-sessions/check_tickets.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[3] / "app/src/main/java/dev/brenorb/escriba/MainActivity.java"
text = SOURCE.read_text(encoding="utf-8")
failures: list[str] = []


def block(start: int) -> str:
    """The text from the brace at or after `start` to its matching brace."""
    open_at = text.index("{", start)
    depth = 0
    for index in range(open_at, len(text)):
        depth += {"{": 1, "}": -1}.get(text[index], 0)
        if depth == 0:
            return text[open_at : index + 1]
    raise SystemExit("unbalanced braces in MainActivity.java")


GUARD = re.compile(r"ticket\s*(!=|==)\s*generation")

# StaleTicketReturns: complete() and failed() ignore a stale ticket first.
for name in ("complete", "failed"):
    match = re.search(rf"private void {name}\(long ticket[^)]*\)\s*", text)
    if not match:
        failures.append(f"{name}(long ticket, ...) not found")
        continue
    body = block(match.end() - 1)
    first = body.strip("{} \n").splitlines()[0].strip()
    if not re.fullmatch(r"if \(ticket != generation\) return;", first):
        failures.append(f"{name}() does not start with 'if (ticket != generation) return;'")

# EveryCallbackGuarded: each listener method in begin() checks its ticket.
begin = block(re.search(r"private void begin\(boolean reconnecting\)", text).end())
methods = 0
for listener in re.finditer(r"new (RemoteEngine|NativeEngine)\.Listener\(\)", begin):
    body = None
    depth, start = 0, begin.index("{", listener.end())
    for index in range(start, len(begin)):
        depth += {"{": 1, "}": -1}.get(begin[index], 0)
        if depth == 0:
            body = begin[start : index + 1]
            break
    for method in re.finditer(r"public void (\w+)\([^)]*\)\s*\{", body):
        methods += 1
        open_at = method.end() - 1
        depth = 0
        for index in range(open_at, len(body)):
            depth += {"{": 1, "}": -1}.get(body[index], 0)
            if depth == 0:
                method_body = body[open_at : index + 1]
                break
        guarded = GUARD.search(method_body) or re.search(r"\b(complete|failed)\(ticket\b", method_body)
        if not guarded:
            failures.append(f"{listener.group(1)}.Listener.{method.group(1)} does not check its ticket")

print(f"checked {methods} engine callbacks and complete()/failed() in MainActivity.java")
if failures or methods == 0:
    print("\n".join(failures or ["no engine callbacks found; the checker no longer matches the code"]))
    sys.exit(1)
print("every engine callback is ticket-guarded")
