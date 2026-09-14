# Update summary

## Android MVP (2026-09-14)

- Added a native Kotlin/Jetpack Compose app under `android/` with Inactive, Active, Processing, Ready, and Error states.
- Added Activate/Deactivate microphone recording through an Android foreground service, including a persistent notification and locked-screen recording.
- Added backend address configuration, health testing, session creation, audio upload, `/process`, local result persistence, processing retry, transcript/memory display, and Ask Memory with evidence.
- Added Gradle wrapper, command-line build/install scripts, API 36-compatible dependencies, and an editable default backend URL for the laptop's current Wi-Fi address.
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
- Verification: `assembleDebug` and `lintDebug` pass with zero lint errors. The backend is reachable at `http://10.133.23.116:8081`; physical-device installation is pending because no authorized ADB device is connected.

## Local correction (2026-09-13)

- Replaced the original Gemini `/interactions` request/response handling with Files API upload, processing-state polling, `generateContent`, and temporary-upload cleanup.
- Added MP4 track inspection to distinguish audio-only recordings from video, M4A MIME handling, bounded HTTP timeouts, and incomplete-response checks.
- Selected Gemini transcription by default, reusing the chat API key stored in `application.yml` at the user's request.
- Added safe provider error responses, focused automated tests, and `scripts/test-audio-flow.ps1` for live upload -> transcription -> extraction -> recall verification.
- The startup script now treats `.env` as optional. No database schema changes.
- JSON responses explicitly use UTF-8 so Windows PowerShell correctly reads non-English transcripts.

Verification: Maven packaging and 15 tests passed (14 added regression tests plus the existing placeholder). A generated short recording and the original `audio.mp4` both completed transcription and extraction; recall returned answers with links to their saved transcript chunks. Test sessions and local recordings are retained for inspection.

The final PowerShell smoke test also passed end to end on the original recording using session `bc05d9c7-747c-4963-a599-a3c4d5526898` and test user `0a2a66f8-ae1c-4e2c-9d1a-d7ff2accf691`. UTF-8 response decoding was verified. The temporary server on port 8082 was stopped; restart the existing backend on 8081 to load the updated code.

The sections below describe the earlier ZIP handoff and are retained as history; their inline limit and credential-removal statements do not describe the current local configuration.

This update adds the next MVP milestone without changing the existing database schema.

## Added

- `TranscriptionProvider` now returns structured transcription results.
- `GeminiTranscriptionProvider`
  - provider name: `gemini`
  - default model: `gemini-3.5-transcribe`
  - inline audio up to 20 MB for this MVP
- `OpenAiCompatibleTranscriptionProvider`
  - provider name: `openai-compatible`
  - supports standard JSON transcription
  - supports diarized segments when using a diarization model
- `TranscriptionService`
  - reads uploaded session audio
  - updates session status
  - creates transcript chunks automatically
- `POST /api/v1/memory/sessions/{sessionId}/transcribe`
- `POST /api/v1/memory/sessions/{sessionId}/process`
  - transcribe + memory extraction in one call
- no-pgvector `/memory/ask` fallback based on keyword retrieval
- health response includes transcription configuration status
- local PowerShell starter loads `.env` and does not require Docker

## Security cleanup

- Removed hard-coded database password from `application.yml`.
- Removed hard-coded AI key from `application.yml`.
- Removed compiled `backend/target` files from the returned project because the uploaded target copy contained the old application configuration.

If the previously hard-coded AI key was real, rotate it.

## No DB migration required

The schema was not changed in this update.
