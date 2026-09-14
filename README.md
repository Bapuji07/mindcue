# Second Memory MVP

Personal conversation-memory system with an Android capture app and an AI-provider-independent backend.

## Current milestone

Implemented:

- Spring Boot backend (Java 17)
- Local PostgreSQL support (Docker is optional)
- Flyway schema migrations
- Memory sessions
- Local audio upload/storage
- Manual transcript chunk APIs
- Automatic audio transcription pipeline
- `gemini` transcription adapter using the Files API and `generateContent` (including MP4)
- `openai-compatible` transcription adapter
- Provider-neutral `ChatAiProvider`, `EmbeddingProvider`, and `TranscriptionProvider`
- Structured memory extraction
- Memory provenance links back to transcript chunks
- `/process` endpoint: audio -> transcript -> memory extraction
- `/memory/ask` with keyword retrieval for the current no-pgvector local setup
- Kotlin/Jetpack Compose Android app under `android/`
- Activate/Deactivate foreground recording that continues with the screen locked
- Automatic phone upload, transcription, extraction, retry, transcript display, and Ask Memory

Not implemented yet:

- streaming/live transcription
- authentication/multi-user identity
- production-grade vector retrieval / pgvector migration
- memory editing/deletion and offline upload scheduling

## Important security note

For this local setup, credentials remain in `backend/src/main/resources/application.yml` as requested.
That file is intentionally ignored by Git; `application.example.yml` documents the required structure safely.
Gemini transcription reuses the configured chat key unless `MEMORY_TRANSCRIPTION_API_KEY` overrides it.
Environment variables and an optional `.env` file can override YAML settings. Do not publish the credential-bearing YAML.

## Local setup (your current pgAdmin/PostgreSQL setup)

Your current backend uses port `8081`.

After cloning, copy the safe configuration template and add your local credentials:

```powershell
Copy-Item .\backend\src\main\resources\application.example.yml `
  .\backend\src\main\resources\application.yml
```

The backend can then start directly with the local YAML configuration. Creating `.env` is optional:

```powershell
cd E:\second-memory-updated\second-memory
Copy-Item .env.example .env
notepad .env
```

Set at least:

```env
DB_URL=jdbc:postgresql://localhost:5432/memory
DB_USERNAME=postgres
DB_PASSWORD=YOUR_POSTGRES_PASSWORD

MEMORY_CHAT_API_KEY=YOUR_GEMINI_KEY

MEMORY_TRANSCRIPTION_PROVIDER=gemini
MEMORY_TRANSCRIPTION_API_KEY=YOUR_GEMINI_KEY
MEMORY_TRANSCRIPTION_MODEL=gemini-3.6-flash
```

The chat extraction example remains provider-independent and currently uses Gemini through its OpenAI-compatible chat endpoint.

### Start backend

Use the helper script, which loads `.env` if present and otherwise uses the YAML settings:

```powershell
cd E:\second-memory-updated\second-memory
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
```

Or set environment variables manually and run from `backend`.

## Health

```powershell
Invoke-RestMethod http://localhost:8081/api/v1/health
```

The response now shows both chat and transcription provider/key status.

## New audio test flow

### 1. Create a session

Use the existing session API and save the returned `id`.

### 2. Record a short audio file

For the first test, record 10-30 seconds on your phone and copy the `.m4a`, `.mp3`, `.wav`, or `.mp4` file to your laptop.

Example location:

```text
E:\second-memory-updated\second-memory\test-audio.m4a
```

### 3. Upload audio from PowerShell

```powershell
$sessionId = "YOUR_SESSION_ID"
$audioPath = "E:\second-memory-updated\second-memory\test-audio.m4a"

curl.exe -X POST `
  "http://localhost:8081/api/v1/memory/sessions/$sessionId/audio" `
  -F "file=@$audioPath"
```

### 4A. Transcribe only

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/v1/memory/sessions/$sessionId/transcribe"
```

Then verify chunks:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8081/api/v1/memory/sessions/$sessionId/transcript-chunks"
```

### 4B. Or process everything after upload

This performs transcription and memory extraction in one call:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/v1/memory/sessions/$sessionId/process"
```

## Transcription providers

### Gemini

```env
MEMORY_TRANSCRIPTION_PROVIDER=gemini
MEMORY_TRANSCRIPTION_BASE_URL=https://generativelanguage.googleapis.com/v1beta
MEMORY_TRANSCRIPTION_API_KEY=YOUR_GEMINI_KEY
MEMORY_TRANSCRIPTION_MODEL=gemini-3.6-flash
```

The Gemini adapter uploads the recording using the Files API, waits for it to become active, then calls `/models/{model}:generateContent` with its URI and a transcription prompt.
MP4 track metadata determines whether it is sent as `audio/mp4` or `video/mp4`. M4A is sent as `audio/mp4`; filenames are not changed and media is not converted.
The application permits files up to 500 MiB, subject to provider duration limits and request timeouts. Start with a short recording. Temporary provider uploads are deleted after success or failure; your local recording is retained.
Gemini currently returns one transcript chunk without speaker timestamps.
Unsupported files return HTTP 400. Provider rejection returns HTTP 502 with an actionable hint, and connection/timeouts return HTTP 504.

### Repeatable smoke test

After restarting the backend to load these changes:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-audio-flow.ps1 -AudioPath .\audio.mp4 -Question "What was discussed in this recording?"
```

Each run creates a new session and saves its audio, transcript, and extracted memories. The script uses a fresh test user ID by default so recall cannot pick up older sessions. Use `-UserId` to test under your usual identity.

### OpenAI-compatible

```env
MEMORY_TRANSCRIPTION_PROVIDER=openai-compatible
MEMORY_TRANSCRIPTION_BASE_URL=https://api.openai.com/v1
MEMORY_TRANSCRIPTION_API_KEY=YOUR_OPENAI_KEY
MEMORY_TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe
```

For `gpt-4o-transcribe-diarize`, the adapter requests diarized JSON and stores speaker labels/timestamps as separate transcript chunks.

## No pgvector is OK for now

Your local schema keeps pgvector disabled. `MEMORY_EMBEDDING_ENABLED=false` is the default.

For the MVP, `/api/v1/memory/ask` ranks a bounded set of memories by keyword overlap before sending the retrieved memories to the configured chat provider. Later we can install pgvector and replace only the retrieval layer.

## Android app

The debug app defaults to this laptop's current Wi-Fi address, `http://10.133.23.116:8081`. The address is editable from the app. If Wi-Fi assigns a new address, run `ipconfig`, update the app field, and select **Save and test connection**.

Build the APK:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-android.ps1
```

Enable Developer options and USB debugging on the phone, connect it by USB, accept the authorization prompt, and install:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-android.ps1
```

The app asks for microphone and notification permission on first activation. **Activate Memory** starts a visible microphone foreground service; **Deactivate Memory** stops recording and runs session creation, upload, transcription, and extraction. A failed processing attempt keeps the local audio and exposes **Retry processing**.

The current APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

## Next milestone

Test the Android app on the physical phone, then add memory editing/deletion, task completion, and a durable offline upload queue.
