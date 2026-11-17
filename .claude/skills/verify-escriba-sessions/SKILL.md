---
name: verify-escriba-sessions
description: Check that the transcript Escriba shows only ever holds text from the audio most recently shared, even when a replaced engine still delivers a segment. Use when changing begin, cancel, complete, failed, generation or any RemoteEngine or NativeEngine listener in MainActivity.java, or the cancellation in RemoteEngine.java and NativeEngine.java.
---

# Verify Escriba transcription sessions

**Property.** `OnlyCurrentAudio`: the transcript on screen holds text only from the audio shared last.

**Harm if broken.** A new share's transcript is mixed with sentences from the previous audio, and Copy or Enviar exports someone else's words.

## Run

```sh
export TLA2TOOLS=/absolute/path/to/tla2tools.jar   # TLC 1.7.4 release
export JAVA=/path/to/java                          # Java 11 or later
bash scripts/verification/check-model.sh .claude/skills/verify-escriba-sessions/model/Sessions.tla
bash scripts/verification/check-model.sh .claude/skills/verify-escriba-sessions/model/Sessions.tla --mutant
python3 .claude/skills/verify-escriba-sessions/check_tickets.py
./scripts/test-core.sh
```

The model passes on the code at commit `96db3e7` (39 distinct states). The `--mutant` run drops the `ticket != generation` check and must report `OnlyCurrentAudio` violated: share the first audio, its worker emits a segment, share the second audio, and the old segment is delivered. No bug was found here. This check guards the design. Read every TLC result with the pstack `/statechart` skill.

`check_tickets.py` checks the real `MainActivity.java` for what the model's `Deliver` step assumes: every method of the `RemoteEngine.Listener` and `NativeEngine.Listener` built in `begin()` compares its `ticket` with `generation`, or passes it to `complete`/`failed`, and those two start with `if (ticket != generation) return;`. Removing that guard from the remote `segment` callback, or from `failed()`, makes it fail. The model and `test-core.sh` alone did not catch either break. If it reports "no engine callbacks found", the activity's structure changed: update the checker before trusting a pass. Instrumented tests in `ShareFlowTest` cover the real activity on a device.

## Coverage

Synced with the code at commit `96db3e7`. Move this forward only after you confirm that the model still describes the code at the new commit.

| Model step | Code (`app/src/main/java/dev/brenorb/escriba/`) |
| --- | --- |
| `Share` | `MainActivity.begin` → `cancel()` (`generation++`), `new Transcript()`, `ticket = generation` |
| `Emit` | `RemoteEngine` / `NativeEngine` worker → `listener.segment` |
| `Deliver` | `runOnUiThread` → `if (ticket != generation) return;` → `state.commit` |

## Not covered

Audio decoding, the 503 retry loop, reconnects of the native recognizer, and what the server returns.
