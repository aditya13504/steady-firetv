# Steady: design

Hackathon: Build, Ship, Shape (Amazon Developer), **Fire TV track**. Deadline 2026-10-23 12:00 PT.

## Why

- 1 in 4 adults over 65 falls every year (14 million people); falls are the leading cause of injury death after 65 (CDC).
- The Otago home exercise programme cuts falls by roughly a third, but after a year only about 37% of people are still doing it. The exercises are not the problem; staying with them alone at home is.
- The TV is the biggest, most familiar screen an older adult owns, and Fire TV is already in tens of millions of living rooms. Most Fire TVs have no camera, but everyone has a phone.

## What

Steady turns Fire TV into a patient, encouraging strength-and-balance coach:

1. Pair a phone by scanning a QR code on the TV. The phone, propped against a book, becomes the camera. Pose estimation (MediaPipe) runs **on the phone**; only 33 body points per frame travel to the TV. Video never leaves the phone.
2. The TV shows an animated coach demonstrating each move next to a live mirror of you, counts reps, times balance holds, and gives one plain-language form cue at a time.
3. Multi-modal control in one flow: D-pad on the Fire TV remote (OK pauses, arrows skip), voice on the phone ("pause", "easier", "next"), big visuals on the TV.
4. Safety: if the body goes horizontal and stays there, the session stops, a full-screen "Are you OK?" appears, and the family contact is alerted unless the person answers.
5. Adapts to the household: each person has a profile and history; the home screen predicts who is exercising from usual times; an AI coach plans the next session from history and writes a two-sentence summary.
6. Measures what matters clinically: the CDC STEADI 30-second chair stand test, scored against age and sex norms, tracked over time.

## Architecture

```
phone browser  ──(pose points 15 fps, voice commands)──▶  companion server  ──WebSocket──▶  Fire TV app
 MediaPipe Pose Landmarker (on device)                     Node: pairing relay,           Kotlin, Compose for TV
 Web Speech commands                                        AI coach (Groq, OpenAI API),   exercise engine (reps,
                                                            household history store        holds, cues, fall check)
```

- `tv/`: Android app for Fire OS (minSdk 25, Android TV / Leanback launcher). Pure-Kotlin exercise engine with unit tests.
- `companion/`: Node + TypeScript. Serves the phone page, relays pose frames by pairing code, stores history, calls the LLM (key stays server side). Deterministic fallback when no key is set.
- A synthetic "demo person" stream (generated from the same keyframes as the coach animation) lets anyone try the app without a camera.

## Non-goals

- Medical diagnosis. Steady reports a screening test against published norms and suggests talking to a clinician.
- Sending video anywhere.

## Builder's calls

- Native Kotlin + Compose for TV (Amazon's own Fire TV starter stack) over React Native: the camera work happens on the phone, so the TV app is all focus-driven 10-foot UI where Compose for TV is strongest.
- Exercise analysis runs on the TV (from points), so the phone stays a thin sensor and the logic is unit-testable on the JVM.
