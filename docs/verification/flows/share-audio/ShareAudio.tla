----------------------------- MODULE ShareAudio -----------------------------
\* Sharing a voice message to Escriba and reading it, as the user sees it.
VARIABLES screen

vars == <<screen>>

Init == screen = "shared"

NeedVoice == screen = "shared" /\ screen' = "voice"
VoiceInstalled == screen = "voice" /\ screen' = "transcribing"
Starts == screen = "shared" /\ screen' = "transcribing"
ServerBusy == screen = "transcribing" /\ UNCHANGED screen
Finished == screen = "transcribing" /\ screen' = "done"
Fails == screen = "transcribing" /\ screen' = "error"
YouStop == screen = "transcribing" /\ screen' = "stopped"
ShareAnother == screen' = "shared"

Next == NeedVoice \/ VoiceInstalled \/ Starts \/ ServerBusy \/ Finished \/ Fails \/ YouStop \/ ShareAnother

Spec == Init /\ [][Next]_vars

Known == screen \in {"shared", "voice", "transcribing", "done", "error", "stopped"}
=============================================================================
