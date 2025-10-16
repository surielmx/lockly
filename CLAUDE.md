# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lockly is an Android application that provides end-to-end encrypted file storage with cloud synchronization. Users encrypt files locally using device-specific encryption keys, upload them to cloud storage, and can decrypt them on any device using a master password.

## Build Commands

```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

### Core Security Model

**Dual Encryption System:**
- **Local Encryption (Device-Specific):** Files are encrypted using Google Tink AEAD with a device-specific master key stored in Android Keystore (managed by `KeystoreHelper`). This key never leaves the device.
- **User Identity (Cross-Device):** A deterministic User ID is generated from the master password using PBKDF2 (see `UserIdGenerator`). This same ID allows accessing cloud files from any device with the correct password.

**Critical Security Components:**
- `VaultManager`: Central encryption/decryption operations
- `KeystoreHelper`: Android Keystore integration for device-specific keys
- `UserIdGenerator`: Deterministic user ID generation from password
- Password is hashed with SHA-256 and stored in `EncryptedSharedPreferences`

### File Processing Pipeline

**Encryption & Upload Flow:**
1. User selects files in `MainScreen` (files tab)
2. `MainViewModel.encryptAndUploadFiles()` enqueues WorkManager chain
3. `EncryptWorker`: Encrypts file using `VaultManager.importAndEncryptFile()`, preserving folder structure from `/storage/emulated/0` to vault directory
4. `FileUploadWorker`: Requests pre-signed URL from API, uploads encrypted file, shows retry option on failure
5. Upon success, shows deletion confirmation dialog for original file

**Decryption & Access Flow:**
1. User taps encrypted file in vault tab
2. `MainViewModel.decryptAndOpenFile()` decrypts to `/storage/emulated/0/Documents/Lockly/` (public temp dir)
3. File marked as "in use" and shown with unlock icon
4. User can delete temp file via UI when done

### UI Architecture

**Jetpack Compose with MVVM:**
- `MainActivity`: Single activity, manages permissions and lifecycle
- `MainViewModel`: Central state management, file operations, cloud sync
- `MainScreen`: Tabbed interface (Files/Vault) with bottom navigation
- `FileExplorerScreen`: Reusable component for both unencrypted and vault file browsing

**Key UI States:**
- Selection mode for multi-file encryption
- "In use" tracking for decrypted files
- Recently encrypted visual indicators
- Cloud upload status per file
- Missing cloud files detection and download

### API Integration

**Backend Communication (`LocklyApiService`):**
- `GET /api/files/{userId}`: List user's cloud files
- `POST /api/upload-url`: Get pre-signed S3 upload URL
- `POST /api/download-url`: Get pre-signed S3 download URL
- Direct file upload/download via pre-signed URLs

**Configuration:** Update `BASE_URL` in `ApiClient.kt` (currently `http://192.168.1.14:3000/`)

### Data Flow

**State Management:**
- All UI state in `MainViewModel` using StateFlow/SharedFlow
- File lists auto-refresh after operations
- Cloud sync runs on initialization and can be triggered manually
- WorkManager observers update UI in real-time

**Storage Locations:**
- Vault: `context.filesDir/vault/` (private, mirroring original folder structure)
- Temp decrypted files: `/storage/emulated/0/Documents/Lockly/` (public, user-accessible)
- WorkManager cache: `context.cacheDir/` (temporary encrypted files)

## Important Implementation Notes

### Navigation

The `MainViewModel.navigateBack(isVault: Boolean)` function requires a boolean parameter indicating which tab is active (true for vault, false for files). Always pass the current tab state when calling this function.

### File Operations

- Encrypted files have `.enc` extension added
- Display names strip `.enc` in UI
- Folder hierarchy is preserved during encryption relative to `/storage/emulated/0`
- File filtering excludes hidden files (starting with `.`), system directories (`android`, `com.*`), and the Lockly temp directory

### WorkManager Chain

The encryption and upload process uses a chained WorkManager setup:
1. `EncryptWorker` produces encrypted file path as output
2. `FileUploadWorker` consumes that path as input
3. Both workers run as foreground services with notifications
4. Upload failures show retry action in notification via `RetryUploadReceiver`

### Permissions

App requires `MANAGE_EXTERNAL_STORAGE` permission on Android R+ (API 30+). Permission handling is in `MainActivity` with `PermissionRequestScreen` shown when not granted.

## Code Patterns

- Use Kotlin coroutines with `viewModelScope` for async operations
- All file operations include proper error handling with user-facing snackbar messages
- WorkManager for background encryption/upload with observable progress
- Compose state hoisting: state in ViewModel, events passed as callbacks
- Network calls wrapped in try-catch with detailed logging (tag-based: `TAG`, `SYNC_TAG`)
