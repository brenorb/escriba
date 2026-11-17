------------------------------ MODULE Sessions ------------------------------
\* Two shared audios in a row in MainActivity. Each begin() calls cancel(),
\* which bumps `generation`, cancels the old engine, and starts a fresh
\* Transcript; every engine callback posts to the UI thread with the ticket
\* it was started with.
\*   Share(a)   -> begin(): cancel(), state = new Transcript(), ticket
\*   Emit(a)    -> the worker for audio a calls listener.segment; a cancelled
\*                 worker may still emit one segment it already decoded
\*   Deliver(a) -> runOnUiThread: if (ticket != generation) return;
\*                 state.commit(...)
\* Broken = TRUE drops the ticket check, as if a callback forgot it.
EXTENDS Naturals, Sequences

CONSTANT Broken

Audios == {"first", "second"}

VARIABLES generation, ticket, current, shown, queue

vars == <<generation, ticket, current, shown, queue>>

Init ==
  /\ generation = 0 /\ ticket = [a \in Audios |-> 0] /\ current = "none"
  /\ shown = {} /\ queue = <<>>

Share(a) ==
  /\ ticket[a] = 0
  /\ a = "first" \/ ticket["first"] # 0
  /\ generation' = generation + 1
  /\ ticket' = [ticket EXCEPT ![a] = generation + 1]
  /\ current' = a /\ shown' = {}
  /\ UNCHANGED queue

Emit(a) ==
  /\ ticket[a] # 0 /\ Len(queue) < 3
  /\ queue' = Append(queue, a)
  /\ UNCHANGED <<generation, ticket, current, shown>>

Deliver ==
  /\ queue # <<>>
  /\ LET a == Head(queue) IN
       shown' = IF Broken \/ ticket[a] = generation THEN shown \cup {a} ELSE shown
  /\ queue' = Tail(queue)
  /\ UNCHANGED <<generation, ticket, current>>

Idle == UNCHANGED vars

Next == Idle \/ Deliver \/ \E a \in Audios : Share(a) \/ Emit(a)

Spec == Init /\ [][Next]_vars

\* The transcript on screen holds text from the audio just shared, never
\* from one it replaced.
OnlyCurrentAudio == shown \subseteq {current}
=============================================================================
