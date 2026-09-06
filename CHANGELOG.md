# Changelog

## [1.0.15] - 2026-09-06

### Added
- **Agent executes real Android shell commands:** the model emits proposed commands in `<command>…</command>` tags; the app extracts, gates and runs each one via `sh -c` (app UID, 20s timeout) and appends the real output (exit code + stdout/stderr) to the response.
- **Per-command permission dialog** showing the exact command with **Allow once**, **Allow forever** and **Deny** buttons. Auto-skips forever-granted commands and "trusted" trust level; disabled by the new **Shell** permission toggle / "disabled" trust level. "Revoke permanently-allowed shell command(s)" clears all forever-grants.
- **Mic input in Agent mode:** transcribes into the prompt box alongside text input.
- **Copy to clipboard** on every agent response (command + real output), and the result is long-press selectable.
- New `run_shell` tool + error codes **E0703** (shell failed) and **E0704** (clipboard failed), logged with stack traces.

## [1.0.14] - 2026-09-06

### Fixed
- **Upload (in a collection) now opens the Android file picker.** The Compose ActivityResultRegistry's dynamically counted request codes grew past the 16-bit limit over the app's lifetime, making `startActivityForResult` throw "Can only use lower 16 bits for requestCode" (E0505) before the picker opened. All file pickers (documents upload, model import, backup export/import, history export) now use the classic `startActivityForResult` path with fixed low request codes (7001–7005) that can never overflow — handled in `MainActivity.onActivityResult`. Every launch remains guarded, logging E0505 + a friendly status instead of closing the app.

## [1.0.13] - 2026-09-06

### Added
- **Upload Document button inside every collection:** each collection card on the Documents tab has its own Upload button that picks documents (any format) and indexes them directly into that collection.
- **Save and Remove buttons per collection:** Save renames a collection; Remove (with confirmation) deletes the collection and all its documents from Docs / RAG / search.
- **Individual document removal** inside every collection, plus an "Unassigned documents" card for anything not in a collection.
- New error codes E0505 (picker open failure) and E0506 (collection operation failure), both recorded in Error logs.

### Fixed
- **Pressing + no longer closes the app:** the file picker launch is guarded — any failure is logged (E0505) and surfaced as a status instead of crashing.
- Embedding failures during indexing now fall back to keyword-only indexing (E0503, still searchable) instead of failing the import.
- Picked URIs persist their read permission so documents stay re-indexable later.
- Removing documents/collections only clears the local RAG index — files on the device are never deleted.

## [1.0.12] - 2026-09-06

### Fixed
- GlowCard now lays its content out vertically (was a stacked Box), so response text, sources and action buttons can never overlap — fixes the Voice Assistant response/buttons overlap and latent overlaps on Activity rows and the Security form across every page/tab.
- Chat mic accepts only the recogniser's final transcript once per press — interim partial results and stale callbacks are ignored, so spoken text no longer appears twice in the message box.

## [1.0.10] - 2026-09-06

### Changed
- Default OpenCode model is now `deepseek-v4-flash`; existing OpenCode profiles on the old prefill are migrated in place.
- Streaming is OFF by default for new OpenCode profiles (toggle per profile).
- Chat sends are crash-proofed: transient request/handler failures are logged (E0205) and surfaced as a status instead of crashing.

### Added
- "Show" on a saved API key now decrypts and reveals the real key in plaintext; a copy-to-clipboard icon copies it.

## [1.0.9] - 2026-09-05

### Fixed
- Default provider base URLs now end with a trailing `/` (`https://opencode.ai/zen/go/v1/`, `http://localhost:11434/v1/` etc.) with the port blank — fixes streaming request failures on OpenCode Go / Ollama-style endpoints.
- Chat mic now transcribes speech into the message box (STT-only) instead of doing nothing / running the full voice assistant pipeline.

### Added
- Error logs section in Settings (copy log to clipboard, share, clear).
- History screen for all modes (Normal / RAG / Hybrid / Agent / Voice) with Open, Rename, Duplicate, Delete, Export history and Clear history (with confirmation).
- Voice Assistant responses are persisted to history automatically.
- Share conversation button (copies to clipboard + share sheet).
- Voice response actions: Copy response, Save clip (WAV to Downloads, no permission on Android 10+, runtime permission on 8/9), and Share.

## [1.0.0] - Initial Release

### Added
- AI connection profiles (OpenCode, Ollama, OpenAI-compatible, custom, local) with per-profile endpoint/port/API key/model/headers/params/TLS/token limits
- Streaming chat with normal / RAG / hybrid / agent / voice modes and model discovery
- Full RAG pipeline: document parsing (TXT/MD/PDF/CSV/XLSX/DOCX/JSON/HTML/XML/code), chunking with overlap, local embeddings, hybrid semantic+keyword search, source citations, collections, duplicate detection by file hash
- Local model manager: import metadata, CPU/GPU acceleration detection, memory warnings, activate/remove models
- Voice assistant: real-time state machine (IDLE/LISTENING/TRANSCRIBING/THINKING/SPEAKING/INTERRUPTED/ERROR), voice interruption, Android STT + TTS with provider interfaces
- Agent framework: extensible tools (calculate, open URL, clipboard, RAG search, settings, file read), permission system, trust levels, strict argument validation, no raw shell execution
- Security: AES-256 Keystore encryption of API keys, PBKDF2-salted PIN app lock, biometric unlock, encrypted backup/export with integrity checks, clipboard protection
- Usage/token accounting with daily/monthly limits and activity log
- First-launch onboarding with local-AI option
- About screen (Made by jnetai.com, version, Check for update, Share app)
- Persistent debug logging with error codes (E0001–E1001)
- GitHub Actions release workflow with stable keystore for in-place updates