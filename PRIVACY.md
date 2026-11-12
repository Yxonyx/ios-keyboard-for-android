# Privacy and data handling

**Typing is local. Optional dictation uses an external service.** This distinction
applies to the reviewed source version; it is not a promise that every APK using
the project name is trustworthy or that the software has no bugs.

| Feature | Processing and destination |
| --- | --- |
| Typing, accent choices, correction and emoji search | On the device; text is committed to the app in which you are typing |
| Settings, recent emoji, copied dictionaries, optional imported font | Private application storage |
| Optional clipboard suggestion | Reads the Android clipboard locally; off by default |
| Optional dictation | Installed speech-recognition provider, usually Google; audio may be uploaded after separate approval |
| GitHub repository | Hosts source code and project documentation; it is not a keyboard backend |

There is no developer-operated API, login, analytics service, advertising SDK,
automatic dictionary download, remote configuration or background GitHub
connection in the application. Reading source code or opening a license/source
link in a browser is separate from typing. The GitHub build workflow uses network
access to fetch development dependencies; that does not run on an installed phone.

## Android warnings: what they mean

Android displays a warning when enabling a third-party input method because a
keyboard can receive the text entered through it, potentially including sensitive
text. This is a warning about the capability being granted, not by itself a
malware finding. The local-only typing design and the source code can be inspected;
neither open source nor a repository name guarantees that an arbitrary downloaded
APK is safe.

An installation-from-unknown-sources prompt, an unrecognized/debug publisher,
a signature mismatch and a Play Protect malware warning are different issues.
Do not dismiss all of them as the normal keyboard warning. Check the exact message,
the APK source and the build/signature information. No production signing identity
or independently audited safety guarantee is claimed for the current preview.

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
default. Microphone permission and a separate first-use approval are required.
The first-use screen explains this before requesting microphone permission; accepting
the screen does not start a recording automatically. Tap the microphone again to
start. **The recognizer may record and upload
audio for processing.** The absence of `INTERNET` on the keyboard does not prevent
another application or service from sending audio. The recognizer provider's
privacy policy applies. Disable dictation in settings to avoid invoking it.
Approval can also be revoked in dictation settings, which stops an active
recognition session. If approval is declined, ordinary typing continues locally.
No offline speech recognition guarantee is made. See the Android
[SpeechRecognizer documentation](https://developer.android.com/reference/android/speech/SpeechRecognizer),
which explicitly describes remote audio processing as a possible implementation.

## Development and reports

The debug build has a synthetic engine benchmark receiver protected by Android's
`DUMP` permission; it is excluded from release builds. Reports should use invented
text. Do not attach private conversations, passwords, clipboard contents or audio
to public issues. See [SECURITY.md](SECURITY.md) for vulnerability reporting.
