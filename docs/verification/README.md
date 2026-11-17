# Escriba verification

| Skill | What it checks | Tool |
| --- | --- | --- |
| [verify-escriba-sessions](../../.claude/skills/verify-escriba-sessions/SKILL.md) | The transcript shows only the audio shared last, even when a replaced engine still delivers text | TLA+ / TLC, 39 states |

The model passes on the current code. The generation ticket checked in every callback already prevents mixed transcripts. The check guards that design.

## Share a voice message and read it

Generated with pstack `/statechart` from [ShareAudio.tla](flows/share-audio/ShareAudio.tla) ([TLC report](flows/share-audio/report.md)).

```mermaid
---
title: "Share a voice message and read it"
---
stateDiagram-v2
  state "Legend" as legend {
    lg0: done
    lg1: failed
    lg2: working
  }
  class lg0 teal
  class lg1 orange
  class lg2 blue
  s0: Audio received
  s1: Text appearing by segments
  s2: Asked to install the language
  s3: Full transcript
  s4: Error with setup advice
  s5: Stopped, finished text kept
  [*] --> s0
  s0 --> s1: transcription starts
  s0 --> s2: the language pack is missing
  s1 --> s0: you share another audio
  s1 --> s3: the last segment arrives
  s1 --> s4: the engine or server fails
  s1 --> s5: you press Parar
  s2 --> s0: you share another audio
  s2 --> s1: Android installs it and resumes
  s3 --> s0: you share another audio
  s4 --> s0: you share another audio
  s5 --> s0: you share another audio
  class s0 blue
  class s1 blue
  class s2 blue
  class s3 teal
  class s4 orange
  class s5 blue
```
