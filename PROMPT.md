# J~Net Local AI Assistant — Full Build Specification

Build a complete, production-quality Android application called **J~Net AI Assistant**.

The application is a privacy-focused, highly configurable AI assistant combining:

* Local document management
* RAG document search
* Cloud AI APIs
* Custom OpenAI-compatible endpoints
* Ollama remote/local connections
* Local mobile AI models
* Agent/automation mode
* Voice assistant mode
* Speech-to-text
* Text-to-speech
* Real-time voice conversations
* Keyboard/text interaction
* Per-provider connection profiles
* Strong API-key and application security

Do NOT build a prototype, mockup, proof of concept, or incomplete demo.

Build the actual working application with proper error handling, secure storage, background processing, cancellation, lifecycle handling, permissions, and a polished Android UI.

---

# 1. Technology Requirements

Use a modern Android architecture.

Prefer:

* Kotlin
* Jetpack Compose
* Material 3
* MVVM / clean architecture
* Kotlin Coroutines
* Kotlin Flow
* Room/SQLite where appropriate
* Android Keystore
* WorkManager for persistent background jobs
* Android Storage Access Framework for file selection
* MediaRecorder/AudioRecord or appropriate Android audio APIs
* Android TextToSpeech where appropriate
* Android permissions following current Android best practices

The application must work correctly on older supported Android hardware as well as modern Android devices.

Avoid unnecessary dependencies.

Keep the application efficient because it may be used on relatively low-powered Android phones.

---

# 2. Main Application Sections

Create a bottom/navigation structure containing:

1. Chat
2. Documents
3. Models
4. Agents
5. Voice
6. Activity/History
7. Settings

The exact UI can be improved if a better UX is found.

Use a dark-first interface with a modern compact design.

Use subtle glow/highlight effects, clear cards, smooth transitions and excellent touch targets.

Do not use intrusive alert dialogs for normal status/errors.

Use an in-app status area, snackbar, banner, inline error state or status panel instead.

---

# 3. AI Connection Profiles

The user must be able to create unlimited AI connection profiles.

Each profile must independently contain:

* Profile name
* Provider type
* Endpoint URL
* Port
* API key
* Model
* Optional organisation/project identifier
* Authentication type
* Request timeout
* Maximum tokens
* Temperature
* Top-p
* Streaming enabled/disabled
* TLS/HTTPS settings where applicable
* Optional custom HTTP headers
* Optional system prompt
* Enabled/disabled state

Profiles must be selectable from the Chat screen.

Example profiles:

* OpenCode
* OpenAI-compatible
* Ollama
* Local server
* Custom server
* Other compatible API

Do NOT assume that every provider uses the same URL or authentication scheme.

Allow the user to configure the complete endpoint.

Examples:

https://example.com/v1

http://192.168.1.100:11434

http://192.168.1.100:8080/v1

The port must be independently configurable where applicable.

---

# 4. OpenCode API

Provide first-class support for OpenCode API-compatible services.

Allow:

* API key
* endpoint
* model selection
* streaming
* configurable generation parameters

Do not hard-code a single model.

Retrieve available models where the endpoint supports model discovery.

Also allow manually entering a model ID.

The user must be able to change models without changing the connection profile.

---

# 5. Ollama Support

Provide dedicated Ollama support.

The user must be able to configure:

* Ollama hostname/IP
* port
* HTTPS if applicable
* model name

Example:

http://192.168.1.50:11434

Do not assume Ollama is running on the phone.

Support:

* Ollama running locally on the Android device if available
* Ollama running on another computer
* Ollama running on a Raspberry Pi
* Ollama running on a LAN server

Add a "Test Connection" function.

Show:

* connection status
* server response
* available models
* selected model
* latency

---

# 6. Generic OpenAI-Compatible Endpoint

Implement a generic OpenAI-compatible provider.

The user must be able to configure:

* Base URL
* port
* API key
* model
* custom headers
* streaming
* timeout

Do not assume the endpoint is OpenAI itself.

The goal is to support as many compatible servers as possible.

Handle:

* /v1/chat/completions
* /v1/models

where supported.

Gracefully handle servers that don't implement model discovery.

---

# 7. Local Mobile AI Models

The application must support running AI models directly on the Android device where technically possible.

Design the model subsystem so that local inference can be implemented using mobile-compatible runtimes such as:

* llama.cpp
* GGUF
* MediaPipe/Gemini-compatible local runtimes
* other suitable Android-native inference engines

Do not require cloud connectivity for local models.

Allow the user to choose whether local inference can use:

* CPU
* GPU
* hardware acceleration where supported

Detect available hardware and expose the available acceleration options.

Show:

* model name
* model size
* context length
* quantisation
* estimated memory requirements
* CPU/GPU availability
* loaded/unloaded state

Warn users before loading models that may exceed available memory.

Do not crash the application because a model is too large.

---

# 8. Model Manager

Create a model-management interface.

Users must be able to:

* import model files
* select local models
* remove models
* inspect model metadata
* activate/deactivate models
* select inference backend
* configure context size
* configure threads
* configure GPU layers where supported

Use Android's file picker rather than requiring hard-coded filesystem paths.

Do not assume a particular storage location.

---

# 9. RAG System

Implement a complete RAG system.

Supported document types should include at minimum:

* TXT
* Markdown
* PDF
* CSV
* XLSX
* DOCX
* JSON
* HTML
* XML
* source-code files
* images where OCR is available
* WAV
* MP3
* M4A
* other common text/audio formats where practical

Documents must be indexed locally.

Do not upload entire documents to the AI provider.

The normal workflow should be:

File
→ extraction
→ cleaning
→ chunking
→ embedding
→ local index
→ retrieval
→ relevant chunks
→ AI model

Only the relevant retrieved context should be sent to the remote model.

---

# 10. Local Vector Database

Use a local database/index.

Support:

* document metadata
* chunks
* embeddings
* page numbers
* source filename
* section/title
* timestamps
* hashes
* indexing status

Prevent duplicate indexing by calculating a file hash.

If a file changes, detect the change and re-index it.

Allow the user to delete an indexed document and all associated chunks.

---

# 11. Hybrid RAG

Do NOT rely exclusively on vector similarity.

Implement hybrid retrieval where practical:

* semantic/vector search
* keyword search
* SQLite FTS5
* metadata filtering
* document filtering
* optional recency weighting

Combine results and rank the most relevant chunks.

The system should work particularly well for exact searches such as:

"invoice 18473"

as well as semantic questions such as:

"What does the contract say about termination?"

---

# 12. RAG Citations

Every RAG answer must identify its sources.

For example:

Answer...

Sources:

contract.pdf — page 14
contract.pdf — page 15
terms.pdf — page 7

Make source references clickable where possible.

Tapping a source should open the document at the relevant page/section.

Never fabricate a source.

If the answer cannot be supported by retrieved documents, explicitly tell the user that the information was not found in the indexed documents.

---

# 13. Document Collections

Allow documents to be organised into collections.

Examples:

* Work
* Personal
* Manuals
* Contracts
* Programming
* Research

A chat can be configured to use:

* all documents
* one collection
* selected documents
* no RAG

Allow multiple collections to be searched simultaneously.

---

# 14. Chat Modes

Implement:

### Normal Chat

Direct AI conversation.

### RAG Chat

AI answers using selected documents.

### Hybrid Chat

AI can use documents plus general model knowledge.

### Agent Mode

AI can perform authorised actions.

### Voice Assistant

Real-time voice interaction.

The user must clearly see which mode is currently active.

---

# 15. Agent Mode

Create an extensible Android agent framework.

The agent must be capable of using tools.

Examples:

* Open an application
* Navigate Android settings where Android permissions allow it
* Read selected application information where APIs allow it
* Create/edit files
* Search documents
* Perform RAG searches
* Start voice recording
* Stop voice recording
* Use the clipboard
* Open URLs
* Launch intents
* Read notifications where explicitly authorised
* Control supported application functions
* Perform calculations
* Query local data
* Query configured AI endpoints

Use Android's supported APIs and accessibility mechanisms where appropriate.

Do NOT implement unsafe unrestricted automation.

Every potentially destructive or privacy-sensitive action must be permission-controlled.

---

# 16. Agent Permissions

Create an explicit permission system.

Examples:

Documents
Microphone
Notifications
Clipboard
Accessibility automation
Network
Files
Device actions

Allow users to enable/disable individual capabilities.

Sensitive actions should require confirmation unless the user explicitly enables trusted-agent mode.

Do not use confirmation dialogs for every harmless operation.

Use configurable trust levels:

* Ask every time
* Ask for destructive actions
* Trusted
* Disabled

---

# 17. Agent Tool Calling

The AI should receive a structured tool list.

Tools should have:

* name
* description
* parameters
* permission requirement
* safety classification

Validate all tool arguments before execution.

Never directly execute arbitrary shell commands supplied by the model.

Any command-execution capability must be isolated, explicitly enabled, restricted and validated.

---

# 18. Voice Input

The application must support microphone input throughout the app.

Voice input must work in:

* Normal Chat
* RAG Chat
* Agent Mode
* Voice Assistant

Provide a microphone button beside the text input.

Pressing it should begin recording/listening.

Show a clear recording state.

Do not use alert boxes.

---

# 19. Speech-to-Text Modes

Support two distinct modes.

### Transcription Mode

Record speech and convert it to text.

Put the resulting transcription into the normal text input box.

The user can edit it before sending.

Example:

Microphone
→ speech recognition
→ text input
→ user edits
→ Send

### Live Assistant Mode

Speech is processed continuously.

Example:

User speaks
→ STT
→ AI
→ response
→ TTS

This should support near-real-time interaction.

---

# 20. STT Providers

Design STT as a provider interface.

Support:

* Android speech recognition where available
* Google speech recognition/API where configured
* Whisper-compatible APIs
* OpenAI-compatible transcription endpoints where supported
* local Whisper/mobile speech models where practical
* custom transcription endpoint

Allow STT provider selection in Settings.

Each provider should have its own configuration/profile where required.

For file transcription, support:

* WAV
* MP3
* M4A
* AAC
* common audio formats

Do not require the user to manually transcode files beforehand.

---

# 21. Real-Time Voice

Implement an actual live voice mode.

UI:

* large microphone state
* listening indicator
* processing indicator
* speaking indicator
* transcript
* response
* stop button

Pipeline:

Microphone
→ audio capture
→ STT
→ AI
→ streamed response
→ TTS
→ speaker

Allow interruption.

If the user starts speaking while TTS is playing:

TTS stops
→ new speech begins
→ previous response is cancelled where possible.

---

# 22. Text-to-Speech

Support Android TTS.

Also design TTS as a provider interface for future providers.

Settings:

* voice
* language
* speed
* pitch
* automatic speaking
* speak AI responses
* speak only in voice mode

Allow the user to press a speaker icon on any AI response.

---

# 23. Voice + RAG

RAG must work completely through voice.

Example:

User:

"What does my contract say about cancellation?"

System:

STT
→ RAG search
→ retrieve contract sections
→ AI
→ TTS response

Display the transcript and source citations simultaneously.

---

# 24. Voice + Agent Mode

Voice commands must also be capable of invoking the agent.

Example:

"Find the PDF about my Raspberry Pi and summarise it."

Pipeline:

STT
→ agent
→ document search
→ RAG
→ AI
→ response
→ TTS

---

# 25. Live Transcription

Provide a separate transcription function.

The microphone can produce:

live speech
→ live transcription

The transcription appears in the text field.

The user can:

* edit
* copy
* save
* clear
* send to AI
* run RAG
* send to agent

---

# 26. API Key Security

API keys must NEVER be stored as plain text.

Use:

* Android Keystore
* encrypted storage
* encryption at rest
* secure memory handling where practical

API keys should never appear in:

* logs
* crash reports
* UI debug output
* analytics
* exported backups

When displayed:

••••••••••••••••

Provide show/hide only after authentication where appropriate.

---

# 27. Application Password Security

Add a Settings option:

"Protect App"

When enabled, require authentication before opening the application.

Support the strongest appropriate Android mechanisms available:

* device credential
* biometric authentication
* application PIN/password

Do not store the raw password.

Use a secure password hash/KDF.

---

# 28. Data Protection

Allow the user to enable encryption for sensitive application data.

Protect:

* API keys
* connection profiles
* chat history
* document metadata
* RAG indexes
* transcripts
* agent history
* settings

Explain clearly what is encrypted.

Do not claim that Android sandbox storage alone is equivalent to encryption.

---

# 29. Token Usage Protection

Add token-usage controls.

Per connection profile:

* maximum tokens
* daily token limit
* monthly token limit
* warning threshold
* maximum context size
* maximum RAG chunks
* maximum document context
* maximum conversation history

Show estimated token usage before expensive requests where possible.

Display:

Today
Tokens used: 12,450
Requests: 34

Also show usage by:

* model
* connection profile
* RAG
* agent
* voice

Warn the user before exceeding configured limits.

Never silently continue spending beyond a configured hard limit.

---

# 30. Connection Profiles

The profile architecture is extremely important.

Each profile must have independent:

* endpoint
* port
* API key
* model
* headers
* parameters
* token limits
* timeout
* provider
* TLS configuration

For example:

OpenCode Flash
endpoint=...
api_key=...
model=...

Ollama Pi
endpoint=http://192.168.1.50
port=11434
api_key=none
model=...

Local Phone
provider=local
model=...

The user should be able to switch between profiles instantly.

---

# 31. Connection Testing

Every profile should have:

"Test Connection"

Show an inline status:

✓ Connected
✓ Authentication successful
✓ Model available
✓ 342 ms

or:

✕ Connection failed
Reason: Connection refused

Do not expose API keys in errors.

---

# 32. Network Behaviour

Support:

* Wi-Fi
* mobile data
* LAN endpoints
* localhost
* remote servers
* HTTPS
* HTTP for explicitly configured local/LAN endpoints

Warn users when using unencrypted HTTP outside local/private networks.

Never silently downgrade HTTPS to HTTP.

Implement sensible timeouts.

Support cancellation.

---

# 33. Streaming

AI responses should stream when supported.

Display tokens progressively.

Allow:

* Stop generation
* Copy response
* Regenerate
* Continue
* Speak response
* Send response to another mode
* Save response

---

# 34. Chat History

Store conversations locally.

Each conversation should contain:

* title
* date
* connection profile
* model
* mode
* selected RAG collection
* messages
* token usage

Allow:

* rename
* delete
* export
* search
* duplicate
* continue

Provide automatic titles based on the first user message.

---

# 35. Activity Log

Create an activity screen showing:

* AI requests
* RAG searches
* document indexing
* STT
* TTS
* agent actions
* connection errors
* token usage

Do not log secrets.

Allow clearing activity history.

---

# 36. Settings

Create comprehensive settings categories:

### AI

* Default profile
* Default model
* Generation settings
* Streaming
* Context limits

### Connections

* Profiles
* Endpoints
* API keys
* Headers
* Connection testing

### RAG

* Chunk size
* Chunk overlap
* embedding model
* retrieval count
* hybrid search
* collections
* indexing behaviour

### Voice

* STT provider
* TTS provider
* language
* voice
* speech speed
* live mode
* microphone behaviour

### Local AI

* Models
* CPU threads
* GPU acceleration
* GPU layers
* context size
* memory limits

### Agent

* Permissions
* Trust level
* Enabled tools
* Confirmation behaviour

### Security

* Protect App
* biometric authentication
* PIN/password
* encrypted data
* automatic lock timeout
* clipboard protection

### Usage

* token limits
* warning thresholds
* statistics

### Appearance

* dark/light/system
* font size
* compact mode
* animations

---

# 37. Data Import/Export

Provide encrypted backup/export.

Allow exporting:

* settings
* connection profiles
* encrypted API keys
* chat history
* document metadata
* RAG database

Do not export secrets unencrypted.

For backups containing credentials, require authentication.

Allow importing backups.

Validate backup integrity before modifying existing data.

Never overwrite existing data without explicit confirmation.

---

# 38. Privacy

The application should operate locally by default.

No analytics.

No advertising.

No unnecessary tracking.

No uploading documents unless explicitly required by a selected provider/function.

Clearly indicate when data leaves the phone.

For every AI request show the selected provider/model.

For RAG show that only retrieved context is being sent to the remote model.

---

# 39. Error Handling

Handle:

* network unavailable
* endpoint unavailable
* invalid API key
* invalid model
* rate limits
* server errors
* malformed responses
* timeout
* cancellation
* insufficient storage
* insufficient RAM
* model loading failure
* microphone permission denied
* speech recognition unavailable
* TTS unavailable
* corrupted document
* unsupported document
* indexing failure

Errors must be user-friendly.

Never crash the application because an external service fails.

Use inline status UI rather than alert boxes wherever possible.

---

# 40. Accessibility

Support:

* Android font scaling
* screen readers
* high contrast
* large touch targets
* content descriptions
* keyboard navigation where applicable

Do not rely solely on colour to communicate state.

---

# 41. Performance

The application must remain responsive while:

* indexing large documents
* generating embeddings
* searching RAG
* loading local models
* performing STT
* performing TTS
* communicating with remote AI servers

Move expensive operations off the main thread.

Use coroutines and appropriate dispatchers.

Support cancellation.

Do not load entire large documents into memory unnecessarily.

Use streaming/file-backed processing where possible.

---

# 42. Background Processing

Use WorkManager for long-running persistent operations such as:

* document indexing
* embedding generation
* model preparation
* batch transcription

Display progress.

Allow cancellation.

Resume interrupted work where possible.

---

# 43. Document Viewer

Provide an internal document viewer where practical.

When a RAG source is clicked:

Open:

document.pdf
Page 14

Highlight the relevant retrieved text where technically feasible.

For text documents show the relevant section.

---

# 44. Search

Provide global search across:

* documents
* conversations
* RAG content
* transcripts

Search must be fast and local.

---

# 45. First Launch

First launch should have a simple setup wizard:

1. Welcome
2. Security choice
3. AI connection
4. API key if required
5. Model selection
6. Voice setup
7. Optional local model
8. Complete

Do not force cloud AI setup.

The user must be able to choose:

"Use local AI"

and skip cloud configuration.

---

# 46. Security Model

Implement a clear security boundary.

Never allow an AI model to:

* access arbitrary files without permission
* execute arbitrary shell commands
* silently send data to an external endpoint
* change security settings
* disable application protection
* retrieve API keys
* export secrets

The AI should only receive the minimum data required for the current task.

---

# 47. Architecture

Separate the system into logical modules/components:

AI provider abstraction
Connection profile manager
Model manager
Local inference engine
Document parser
Chunker
Embedding engine
Vector database
Hybrid search
RAG engine
Chat engine
STT engine
TTS engine
Voice session manager
Agent engine
Tool registry
Permission manager
Security manager
Usage/token manager
Chat history
Settings
Backup/export
UI

Avoid tightly coupling these systems.

The provider interface should make it possible to add another AI backend without rewriting the chat system.

---

# 48. AI Provider Interface

Create a common interface conceptually equivalent to:

AIProvider

Capabilities should include:

* chat
* streaming chat
* model listing
* embeddings
* cancellation
* health check

Not every provider will support every capability.

Expose capabilities dynamically.

For example:

Ollama
✓ chat
✓ streaming
✓ models
✓ embeddings

Some custom endpoint
✓ chat
✓ streaming
✕ embeddings

The application should adapt accordingly.

---

# 49. Embedding Architecture

Embeddings should preferably run locally.

Do not require the same provider used for chat to provide embeddings.

Allow:

* local embedding model
* remote embedding endpoint
* provider-specific embeddings

Store embedding model metadata with the index.

If the embedding model changes, correctly detect that an index may need rebuilding.

---

# 50. Voice Architecture

STT, AI and TTS must be independent providers.

Example:

Microphone
→ Google STT
→ OpenCode AI
→ Android TTS

Another configuration:

Microphone
→ local Whisper
→ Ollama
→ Android TTS

Another:

Microphone
→ Android STT
→ local model
→ Android TTS

Do not hard-code one pipeline.

---

# 51. Real-Time Voice State Machine

Implement explicit states:

IDLE
LISTENING
TRANSCRIBING
THINKING
SPEAKING
INTERRUPTED
ERROR

Transitions must be deterministic.

The UI must always reflect the actual state.

Prevent duplicate requests caused by rapid microphone presses.

---

# 52. Token/Cost Accounting

Record usage when available from the provider.

Track:

* prompt tokens
* completion tokens
* total tokens
* estimated tokens when provider does not report usage

Do not claim estimated usage is exact.

Allow optional estimated pricing configuration per model.

Show estimated cost only when pricing has been configured.

---

# 53. Customisation

Everything important should be configurable.

Do not hard-code:

* endpoint URLs
* ports
* models
* API keys
* token limits
* STT provider
* TTS provider
* embedding provider
* RAG collection
* local model
* voice
* automation permissions

Reasonable defaults are fine, but every important setting should be overrideable.

---

# 54. UI Requirements

The UI should feel like a modern AI application rather than an old Android utility.

Chat:

* message bubbles/cards
* streaming indicator
* model/profile selector
* RAG toggle
* agent toggle
* microphone button
* send button
* attachment button
* stop button

Documents:

* collections
* indexing progress
* file type icons
* search
* source status

Voice:

* large microphone
* live transcript
* AI response
* audio state
* stop/interruption

Settings:

* grouped cards
* search settings
* clear descriptions
* inline status

Use dark theme as the default.

---

# 55. Attachment Handling

From Chat allow attaching documents/files directly.

Options:

* Add to RAG
* Use once
* Transcribe
* Analyse
* Summarise
* Extract information

Do not automatically permanently index every attachment.

Ask through a clear inline choice where necessary.

---

# 56. Image Understanding

Where the selected AI model/provider supports image input:

Allow image attachments.

For local models that support vision, use the local model.

For remote models, clearly indicate that the image is being sent to the configured provider.

Never silently upload images.

---

# 57. Audio Understanding

Allow audio files to be:

* transcribed
* summarised
* added to RAG
* queried

Example:

User selects recording.wav.

The app can:

transcribe
→ index transcript
→ ask AI questions about it.

Show timestamp/source information where available.

---

# 58. Developer Requirements

Write clean maintainable code.

Use strong typing.

Avoid giant classes.

Avoid duplicated networking code.

Use dependency injection where appropriate.

Use repository/service abstractions.

Do not place API keys in source code.

Do not commit secrets.

Use secure Android storage.

Validate all user-controlled URLs, ports, headers and parameters.

Prevent SSRF-style abuse in any agent/network functionality.

Never execute arbitrary model-generated network requests without permission and validation.

---

# 59. Testing

Create tests for:

* RAG chunking
* retrieval
* hybrid ranking
* database operations
* profile management
* API authentication
* provider parsing
* token accounting
* security functions
* STT state handling
* TTS state handling
* agent permission handling

Also test:

* invalid endpoint
* invalid API key
* disconnected network
* empty response
* malformed server response
* large documents
* duplicate documents
* cancelled requests
* app backgrounding
* app restart
* locked application

---

# 60. Build Requirements

The project must build successfully.

Fix all compilation errors.

Fix all lint errors that represent real problems.

Do not leave TODO implementations for core functionality.

Do not replace functionality with fake/mock responses.

Do not claim a feature works unless it actually works.

Where Android/device limitations prevent a feature, implement the correct capability detection and graceful fallback.

---

# 61. Important Implementation Principle

Build the application as a **provider-independent AI platform**, not an OpenCode-specific application.

OpenCode should be an excellent first-class provider, but the architecture must support:

OpenCode
Ollama
OpenAI-compatible servers
Local models
Custom endpoints
future providers

Likewise:

STT must be provider-independent.

TTS must be provider-independent.

Embeddings must be provider-independent.

This is critical to the long-term design.

---

# 62. Final Acceptance Criteria

The finished application must allow a user to:

1. Install the APK.
2. Launch the app.
3. Protect it with biometric/PIN/password security.
4. Create an OpenCode connection profile.
5. Enter an API key securely.
6. Select a model.
7. Chat with the model.
8. Create an Ollama profile pointing to another machine.
9. Test the connection.
10. Select a remote Ollama model.
11. Import local documents.
12. Index those documents.
13. Ask RAG questions about them.
14. Receive source citations.
15. Speak a question instead of typing it.
16. See the transcription in the input box.
17. Edit the transcription.
18. Send it as a normal request.
19. Use continuous voice assistant mode.
20. Hear AI responses through TTS.
21. Interrupt TTS by speaking.
22. Use RAG through voice.
23. Use authorised agent tools through voice.
24. Import/use a mobile-compatible local AI model where supported.
25. Select CPU/GPU acceleration where supported.
26. Switch AI connection profiles instantly.
27. Configure custom endpoints and ports.
28. Configure separate API keys for every profile.
29. Configure token limits.
30. View usage statistics.
31. Encrypt sensitive application data.
32. Export/import an encrypted backup.
33. Use the application without any cloud AI by selecting local models.

The finished result should feel like a **complete private AI workstation in an Android application**, combining ChatGPT-style interaction, local RAG, configurable AI providers, mobile local inference, voice assistant functionality and controlled Android automation.

Prioritise correctness, security, modularity and real functionality over adding superficial features.


PROJECT LOCATION PATH /home/jay/Documents/Scripts/AI/OpenCode/Android-Apps/JNET-AI-Mobile-Assistant/

Use github workflows to build the app (and any update to it) and put finally release and any update for the app and place in  apk folder in the project location aswell as backup known working versions in Backup folder before doing major updates

Dont edit this prompt file

Never change anything in Backup folders (if it exists unlessyou backing up a current working version before making a major update or upgrade) but you can use them as a read-only reference if a mistake is made and you need to fix something or restore previously working versions

Save changes to file(s) in question

Then after files are added / edited then save any changes made to changes.txt

Implement persistent error handling and debugging throughout the project. Every failure, exception, or unexpected state should generate a clear error code, detailed debug output, and useful diagnostic information to help identify the exact cause quickly.

Do not remove debugging systems after issues are fixed — keep all error codes, logging, stack traces, validation checks, and diagnostic tools permanently integrated so that any future bugs, crashes, or unexpected behaviour can be traced and resolved efficiently.

Always use same key-store for each app made via github workflows so it can update correctly without requiring uninstallation

Save changes to changes.txt (create if not exists)

Tell me when ready to test (stay quiet after acknowledging you got the message / request / mission every time and stay quiet till its ready to test and respond only if fully complete  or if you need input from me or if I ask for an update)!

When giving final github release link (where applicable), make sure it points to the newest release but without the tag or filename so I can see the correct location without direct downloading the file as thats best practice!

Each app needs an About section showing
In about section it should say Made by jnetai.com 
The full version number (same as github release version tag) also add a Check for update button (so internet permissions required) to check latest release version (tag in full)
Add a Share App button so users can share the app.
 
Each update should use same key store so the app can update and not require uninstall of the app to update it.

Each app should have its own local folder and own github repository and own keystore that remains the same so it can update without uninstall 1st and be dark centered themed and allow space at bottom so buttons or elements at the bottom of the app should not be cut off, it should look professional.

App compatibility: apps needs to work on samsung s8 and onwards and google pixel 6 and onwards
full path to Downloads is /storage/emulated/0/Download/ (called Downloads as an alias in android)

In releases on github a meaningful name should be used for example Tetris.apk (no need for a debug version of any app or game for android just put the debug version as the main version!

Github api tokens / passwords etc can be found in /home/jay/Documents/Scripts/AI/openclaw/password-vault/

Prime Directive is always build on github never on the pi (do it remotely with github never locally)
If a check for update ever fails in the app it should fallover to just opening link to latest github repo for this app.

Build the releases via github actions / workflows (not locally ever on pi (remember your prime directive)) in there own repository (1 per app and auto incriment versions in case user wants to ever go back)

Start now with all in order no questions asked!


