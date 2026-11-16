# Dictation and live text

The keyboard uses Android SpeechRecognizer, with the active typing language's
BCP 47 tag (Hungarian: `hu-HU`). The configured system recognizer takes precedence;
Google Speech Services and the Google app are fallback candidates. The keyboard
has no Internet permission; an external recognizer can upload audio after the
separate in-app consent and microphone permission are granted.

Partial recognition replaces composing text directly in the active editor. Final
recognition commits that composition rather than inserting a second copy. A
provider can send zero, one or multiple partial results, so word-by-word latency
cannot be promised for every provider or language. See the Android
[RecognitionListener contract](https://developer.android.com/reference/android/speech/RecognitionListener).

## Start feedback

A single short native haptic tap is requested when the recognizer reports that it
is ready to listen, rather than at the initial button press. It follows the
keyboard's existing haptic setting and Android system settings. Devices without a
vibration motor or with haptics disabled may provide no vibration; that does not
mean speech capture failed. A ready callback alone is also not proof of audible
input, as the diagnosis below demonstrates.

## Listening but no text

Permission approval and a "Listening" callback do not prove that the recognizer is
receiving audible microphone input. Android can silence an audio capture because
of its microphone-sharing or while-in-use permission policy.

On the tested Xiaomi / Android 16 phone, Google Speech Services returned NO_MATCH
while the system audio monitor marked its capture `silenced`. The system had no
configured default speech recognizer. Selecting the same Google service as the
system recognizer changed its capture to `silenced:false`. Temporarily disabling
Xiaomi environmental sound recognition did not resolve the problem, so that
accessibility setting was restored. This observation does not justify disabling
accessibility services in general.

The Android 15 framework grants capability-carrying bindings to a default,
on-device or preinstalled recognition service. A downloaded fallback which has
none of these roles can consequently start but receive silent audio. The app now
respects the configured provider and offers system voice-input setup for the
known unprivileged-fallback case. It does not write protected settings itself or
silently broaden microphone permission. OEM settings screens can differ.

- Open keyboard settings → Dictation → Recognizer to open system voice-input settings.
- Select a speech provider, grant the requested microphone access, then retry.
- If Android has no suitable provider-selection UI, the ROM's voice-input setup
  needs manufacturer-specific instructions; the keyboard does not have permission
  to change secure system settings on an ordinary installation.
- Check microphone privacy and competing audio capture if the problem remains.
  "No speech heard" alone is not proof of a network fault.

Framework references: [provider privilege selection](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/speech/SpeechRecognitionManagerServiceImpl.java),
[binding flags](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/speech/RemoteSpeechRecognitionService.java),
[audio sharing](https://developer.android.com/media/platform/sharing-audio-input).

## Who this was checked for

The user confirmed that dictation worked on the Xiaomi / Android 16 test phone
after the default-recognizer correction. Lifecycle-only logs also confirmed Google
partial-result and final-result delivery; recognized text was not logged. This is
a device-specific confirmation,
not a guarantee for all Android phones. Samsung, Pixel, other OEMs, Bluetooth
microphones and alternate recognizers have not all been physically validated.
A compatible provider must support the selected language; it may require downloaded
models or network access. Partial-result frequency and latency are provider-specific.
The app's Android 13 minimum does not itself establish dictation compatibility.

## Validation boundary

The synthetic emulator engine records no audio. Its integration test checks the
real IME/editor composing span before and after controlled partial/final results.
This verifies the insertion path; it does not measure Google's recognition quality,
latency or the phone user's actual spoken utterance. The physical audio-routing
check establishes that the Google capture is no longer silenced, not that every
future utterance will be recognized.
