# Changelog

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