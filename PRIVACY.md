# Privacy and data handling

## Typing

The keyboard application declares no `INTERNET` permission. Offline correction
runs locally using bundled read-only dictionaries. The application has no
analytics or account system. It does not intentionally persist a typing history,
passwords, audio or recognized speech. Text necessarily passes through memory and
the Android editor connection; the host application receives committed text and
has its own privacy practices.

Private/sensitive editors disable correction and clipboard suggestions. These
guards depend on the editor correctly declaring its input type and options.
Compatibility with every editor has not been established.

## Stored on the device

Preferences store settings and recent emoji. Dictionary assets are copied into
private app storage for native access. An optional imported emoji font is also
stored there. The clipboard suggestion feature is off by default; when enabled,
it reads the system clipboard while the keyboard is active and offers eligible
plain text. It does not keep a clipboard-history database. Android application
backup is disabled in the manifest. Clearing application storage or uninstalling
removes the application's private files and preferences.

## Optional speech recognition

Tapping the microphone invokes an installed Android speech recognizer, preferring
Google Speech Services or the Google application and otherwise using the system
default. Microphone permission is required. **The recognizer may record and upload
audio for processing.** The absence of `INTERNET` on the keyboard does not prevent
another application or service from sending audio. The recognizer provider's
privacy policy applies. Disable dictation in settings to avoid invoking it.
No offline speech recognition guarantee is made.

## Development and reports

The debug build has a synthetic engine benchmark receiver protected by Android's
`DUMP` permission; it is excluded from release builds. Reports should use invented
text. Do not attach private conversations, passwords, clipboard contents or audio
to public issues. See [SECURITY.md](SECURITY.md) for vulnerability reporting.
