export const title = "Share a voice message and read it";
export const state = (v) => ({
  shared: "Audio received",
  voice: "Asked to install the language",
  transcribing: "Text appearing by segments",
  done: "Full transcript",
  error: "Error with setup advice",
  stopped: "Stopped, finished text kept",
}[v.screen]);
const steps = {
  NeedVoice: "the language pack is missing",
  VoiceInstalled: "Android installs it and resumes",
  Starts: "transcription starts",
  Finished: "the last segment arrives",
  Fails: "the engine or server fails",
  YouStop: "you press Parar",
  ShareAnother: "you share another audio",
};
export const step = (action) => steps[action] ?? null;
export const group = (v) => (v.screen === "done" ? "done" : ["error"].includes(v.screen) ? "failed" : "working");
export const colors = { done: "teal", failed: "orange", working: "blue" };
