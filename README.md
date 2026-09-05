# J~Net AI Assistant

A complete private AI workstation for Android. Chat with cloud AI, run local models, search your own documents with RAG, use voice and authorised automation — all from one app.

## Features

- **AI Connection Profiles** — unlimited profiles for OpenCode, Ollama, OpenAI-compatible, custom endpoints and local devices. Each profile has its own endpoint, port, API key, model, headers, generation parameters, TLS settings and token limits. Sample endpoints are pre-filled when you pick a provider (e.g. OpenCode Go `https://opencode.ai/zen/go/v1` with model `opencode-go`, Ollama `http://localhost:11434/v1`, custom `http://SERVER_IP:PORT/v1`) and remain fully editable.
- **Chat** — streaming AI chat with normal / RAG / hybrid / agent / voice modes.
- **RAG** — index TXT/MD/PDF/CSV/XLSX/DOCX/JSON/HTML/XML/source code locally; hybrid semantic + keyword search; source citations on every answer; documents organised into collections.
- **Local Models** — import GGUF/llama.cpp-compatible models, view metadata, pick CPU/GPU acceleration with memory warnings (no crashes on oversized models).
- **Voice Assistant** — real-time state machine (IDLE→LISTENING→TRANSCRIBING→THINKING→SPEAKING), voice interruption, Android STT + TTS with provider interfaces.
- **Agent Mode** — extensible tool framework with a permission system, trust levels and strict argument validation. No raw shell execution.
- **Security** — API keys encrypted in the Android Keystore, PIN (PBKDF2-salted), biometric unlock, encrypted backups/export.
- **Usage tracking** — daily/monthly token accounting per profile, activity log.
- **100% local by default** — no analytics, no tracking, no forced cloud.

## Default PIN

PIN security (Secure mode) is **OFF by default** — the app opens without a PIN and never locks until you opt in.

Enable it in **Settings → Security**:
1. Toggle **Secure mode ON**.
2. Enter the default PIN **`12345678`**.
3. Enter a new personal PIN and confirm it.
4. Only the hashed PIN is stored and the app lock turns on.

- PIN fields are shown masked (•••••).
- The lock screen always accepts `12345678` as a recovery path, and has **Reset app protection** (type `12345678`, tap Reset) plus **Copy diagnostic log**.
- Settings → Security → **Diagnostics & crash log** shows the log with Copy/Share/Clear.
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