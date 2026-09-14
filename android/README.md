# Second Memory Android MVP

The Android app is the capture and recall client for the Spring Boot backend.

## User flow

1. **Activate Memory** requests microphone access and starts a visible microphone foreground service.
2. AAC audio is recorded into a private `.m4a` file under the app's internal storage. Recording continues while the screen is locked.
3. **Deactivate Memory** stops capture and changes the service to foreground data processing.
4. The app creates a session, uploads the audio, and calls the backend `/process` endpoint.
5. The Ready screen displays the summary, extracted memories, and transcript.
6. **Ask Memory** calls the backend `/memory/ask` endpoint and displays the answer with transcript evidence.

Failed processing retains the local audio and pending backend session so **Retry processing** can resume without recording again. The latest successful result and the generated user ID persist across app restarts.

## Configuration

The default backend is `http://10.133.23.116:8081`, matching the laptop address at project creation. It can be changed in the app under **Backend connection**. API keys remain only on the backend.

The app enables cleartext HTTP for local-network MVP testing. Replace this with HTTPS before exposing the backend outside the trusted local network.

## Build and install

From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-android.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-android.ps1
```

The install script requires exactly one authorized USB-debugging device. The APK can also be installed manually from `app/build/outputs/apk/debug/app-debug.apk`.

## Current MVP limits

- Intentional Activate/Deactivate capture only; no continuous boot-time listening.
- One generated local user identity; no login or account synchronization.
- Latest result is cached; there is no full local session browser yet.
- Processing requires connectivity to the laptop backend.
- The UI can view memories but cannot edit, delete, or complete them yet.
