# J~Net AI Assistant

A complete private AI workstation for Android. Chat with cloud AI, run local models, search your own documents with RAG, use voice and authorised automation — all from one app.

## Features

- **AI Connection Profiles** — unlimited profiles for OpenCode, Ollama, OpenAI-compatible, custom endpoints and local devices. Each profile has its own endpoint, port, API key, model, headers, generation parameters, TLS settings and token limits.
- **Chat** — streaming AI chat with normal / RAG / hybrid / agent / voice modes.
- **RAG** — index TXT/MD/PDF/CSV/XLSX/DOCX/JSON/HTML/XML/source code locally; hybrid semantic + keyword search; source citations on every answer; documents organised into collections.
- **Local Models** — import GGUF/llama.cpp-compatible models, view metadata, pick CPU/GPU acceleration with memory warnings (no crashes on oversized models).
- **Voice Assistant** — real-time state machine (IDLE→LISTENING→TRANSCRIBING→THINKING→SPEAKING), voice interruption, Android STT + TTS with provider interfaces.
- **Agent Mode** — extensible tool framework with a permission system, trust levels and strict argument validation. No raw shell execution.
- **Security** — API keys encrypted in the Android Keystore, PIN (PBKDF2-salted), biometric unlock, encrypted backups/export.
- **Usage tracking** — daily/monthly token accounting per profile, activity log.
- **100% local by default** — no analytics, no tracking, no forced cloud.

## Default PIN

If app protection is enabled, the app locks with a PIN.

- **Default PIN: `12345678`**
- PIN fields are shown masked (•••••).
- While the default PIN is in use the lock screen shows it clearly.
- The **first time you unlock with the default PIN you are forced to set a personal PIN** before the app opens.
- PINs are stored only as salted PBKDF2 hashes — never in the clear.

## Tech Stack

- Kotlin, Jetpack Compose, Material 3, MVVM
- Coroutines + Flow, Room (SQLite), WorkManager
- OkHttp (chat/embeddings/streaming)
- Apache POI + pdfbox-android (document parsing)
- Android Keystore / AES-GCM encryption
- Min SDK 26 (Samsung S8+, Pixel 6+), Target SDK 34, ARM64

## Building

Release builds are produced by GitHub Actions on a `v*` tag push, signed with the app's stable keystore so the app can update in place.

```bash
git tag v1.0.0 && git push origin master --tags
```

## License

Proprietary. Made by jnetai.com.