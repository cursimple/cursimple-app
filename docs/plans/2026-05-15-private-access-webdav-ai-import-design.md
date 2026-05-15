# Private Access, WebDAV, and AI Schedule Import Design

## Goal

Add three connected data-management features:

- a developer-only private-file access provider for file managers such as MT Manager,
- WebDAV settings and schedule backup/restore,
- AI image schedule import with user-provided API URL and key.

## Researched API Notes

Android's `FileProvider` is suited for sharing specific files through temporary `content://` grants. For browsing an app-owned directory from a file manager, Android's Storage Access Framework shape is a better match, so this design uses a custom `DocumentsProvider` guarded by a persisted developer setting.

For AI image input, current multimodal model APIs generally do not accept arbitrary image bytes directly inside the chat/responses JSON request. OpenAI-compatible APIs accept image URLs, base64 data URLs, or a prior Files API upload referenced by file id. Anthropic and Gemini follow similar URL/base64/file-upload patterns. This app will default to compressed image bytes encoded as a base64 data URL inside an OpenAI-compatible JSON request because it works with the widest set of user-provided API URLs and keys. Direct multipart image upload is not used for model inference in the first implementation.

## Architecture

### Preferences

Extend `UserPreferences` with:

- `privateFilesProviderEnabled: Boolean = false`
- `webDavUrl: String = "https://dav.jianguoyun.com/dav/"`
- `webDavUsername: String = ""`
- `webDavPassword: String = ""`
- `aiImportApiUrl: String = ""`
- `aiImportApiKey: String = ""`
- `aiImportModel: String = ""`

These fields live in the existing DataStore preferences repository and are reset by `resetAllSettings`.

### Settings UI

Add two settings destinations:

- WebDAV settings: URL, account, password, save, test connection.
- AI image import settings: API URL, API key, optional model, save.

The existing developer debug section gets a switch for private-file provider access. It defaults off and remains visible only while developer mode is enabled.

### Private File Provider

Add `PrivateFilesDocumentsProvider` in the app module. It exposes selected app-private roots only when `privateFilesProviderEnabled` is true:

- `filesDir`
- `cacheDir`

It supports directory listing, reading, writing, creating files/directories, deleting, renaming, and MIME detection where practical. Every document id resolves through canonical paths and must stay inside one of the allowed roots. When disabled, `queryRoots` and document operations return no accessible documents or throw `FileNotFoundException`.

This is intentionally a developer tool. It is not used as a normal app architecture path and is not a fallback for production data access.

### WebDAV

Add `WebDavClient` in the app module using OkHttp because OkHttp is already available through the existing Gradle version catalog and core-plugin usage. The client implements the small method set needed:

- `PROPFIND` depth 1 to list backups,
- `MKCOL` to ensure backup directory,
- `PUT` to upload backup bytes,
- `GET` to restore backup bytes,
- optional `DELETE` if the UI exposes delete later.

Schedule backups reuse `ScheduleSharePayload` as the payload format. The WebDAV feature uploads files under a fixed app folder, for example `CurSimple/backups/`, with names like `schedule-YYYYMMDD-HHMMSS.csv1`. Restore downloads a selected file, decodes it with `ScheduleShareCodec`, and uses the existing import apply path.

### AI Image Import

Add an AI import service:

- load image from Photo Picker or camera capture URI,
- decode and downscale while keeping text readable,
- encode JPEG/PNG to base64 data URL,
- call an OpenAI-compatible endpoint,
- require a strict JSON response shaped like `TermSchedule`/`CourseItem`,
- validate through the existing `validatePluginSchedule`,
- show the same confirmation dialog as QR/WebDAV import before overwriting data.

If API URL/key is missing, the import flow opens the AI settings dialog instead of starting a request.

### Import / Export UI

Keep the QR section intact. Add:

- WebDAV backup/restore actions, driven by settings stored in the settings page.
- AI image import actions: pick image and take photo. Missing AI configuration opens the settings dialog.

The settings page remains the source of truth for configuration. The import/export page contains actions, not permanent settings forms.

## Error Handling

- WebDAV config validation rejects blank URL/account/password for actions that require auth.
- WebDAV HTTP failures report status code and short message.
- AI import rejects missing config, unsupported images, oversized payloads after compression, invalid JSON, invalid schedule fields, and model responses without usable JSON.
- Private provider denies all access when disabled and validates canonical paths before every file operation.

## Testing

- Unit tests for preference defaults and reset behavior.
- Unit tests for WebDAV path joining/XML parsing where possible.
- Unit tests for AI response JSON extraction and schedule validation mapping.
- Build check with Gradle after implementation.

