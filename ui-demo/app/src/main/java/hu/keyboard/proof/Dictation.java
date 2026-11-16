// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.List;

/**
 * One utterance through the system-selected recognizer, with Google fallbacks.
 * The provider may upload audio. Explicit approval is required; this keyboard stores no recording.
 */
final class Dictation {
  interface Listener {
    default void ready() {}

    void status(String value);

    void partial(String text);

    void done(String text);

    void failed(int error);
  }

  private final Context context;
  private final Listener listener;
  private SpeechRecognizer recognizer;
  private boolean active;

  Dictation(Context context, Listener listener) {
    this.context = context;
    this.listener = listener;
  }

  boolean active() {
    return active;
  }

  static ComponentName service(Context context) {
    List<ResolveInfo> services =
        context
            .getPackageManager()
            .queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
    List<String> available = new ArrayList<>();
    for (ResolveInfo info : services)
      if (info.serviceInfo.enabled && info.serviceInfo.exported)
        available.add(new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name).flattenToString());
    String selected = SpeechEnginePolicy.select(configured(context), available);
    return selected == null ? null : ComponentName.unflattenFromString(selected);
  }

  private static String configured(Context context) {
    String value = android.provider.Settings.Secure.getString(
        context.getContentResolver(), "voice_recognition_service");
    ComponentName component = value == null ? null : ComponentName.unflattenFromString(value);
    return component == null ? null : component.flattenToString();
  }

  static boolean needsSetup(Context context) {
    ComponentName selected = service(context);
    if (selected == null) return false;
    try {
      boolean system = (context.getPackageManager().getApplicationInfo(selected.getPackageName(), 0).flags
          & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
      boolean systemChoice = selected.flattenToString().equals(configured(context));
      if (system || systemChoice) return false;
      return SpeechEnginePolicy.needsSetup(android.os.Build.VERSION.SDK_INT, false, false,
          selected.equals(onDeviceService()));
    } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
      return false;
    }
  }

  private static ComponentName onDeviceService() {
    // Mirrors the framework's privileged-provider check. Missing OEM configuration
    // is handled conservatively; availability alone does not identify this provider.
    android.content.res.Resources resources = android.content.res.Resources.getSystem();
    int id = resources.getIdentifier("config_defaultOnDeviceSpeechRecognitionService", "string", "android");
    if (id == 0) return null;
    String value = resources.getString(id);
    return value.isEmpty() ? null : ComponentName.unflattenFromString(value);
  }

  static void openSettings(Context context) {
    try {
      context.startActivity(new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (android.content.ActivityNotFoundException unavailable) {
      android.util.Log.w("KeyboardDictation", "System voice input settings are unavailable");
    }
  }

  static String providerName(Context context) {
    ComponentName selected = service(context);
    if (selected == null) return context.getString(R.string.system_default);
    try {
      return context.getPackageManager().getApplicationLabel(
          context.getPackageManager().getApplicationInfo(selected.getPackageName(), 0)).toString();
    } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
      return selected.getPackageName();
    }
  }

  static boolean available(Context context) {
    return service(context) != null || SpeechRecognizer.isRecognitionAvailable(context);
  }

  /** Returns false when no recognizer is installed. */
  /** @param tag a BCP 47 locale such as hu-HU */
  boolean start(String tag) {
    if (!new KeyboardSettings(context).enabled(MicPermissionActivity.CONSENT_KEY, false))
      throw new IllegalStateException("External speech recognition has not been approved");
    cancel();
    ComponentName component = service(context);
    if (component == null && !SpeechRecognizer.isRecognitionAvailable(context)) return false;
    recognizer =
        component == null
            ? SpeechRecognizer.createSpeechRecognizer(context)
            : SpeechRecognizer.createSpeechRecognizer(context, component);
    recognizer.setRecognitionListener(new Callbacks(recognizer));
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag);
    intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
    intent.putExtra(
        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
    intent.putExtra(
        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
    android.util.Log.i("KeyboardDictation", "Starting provider="
        + (component == null ? "system-default" : component.flattenToShortString()) + " language=" + tag);
    active = true;
    listener.status(context.getString(R.string.connecting));
    recognizer.startListening(intent);
    return true;
  }

  /** Stops recording; the final text still arrives through {@link Listener#done}. */
  void stop() {
    if (recognizer != null && active) {
      recognizer.stopListening();
      listener.status(context.getString(R.string.processing));
    }
  }

  void cancel() {
    active = false;
    if (recognizer != null) {
      recognizer.destroy();
      recognizer = null;
    }
  }

  private static String first(Bundle results) {
    ArrayList<String> values =
        results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    return values == null || values.isEmpty() || values.get(0) == null ? "" : values.get(0);
  }

  private final class Callbacks implements RecognitionListener {
    private final SpeechRecognizer owner;
    private boolean readyNotified;

    Callbacks(SpeechRecognizer owner) {
      this.owner = owner;
    }

    private boolean current() {
      return owner == recognizer && active;
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
      android.util.Log.i("KeyboardDictation", "Ready for speech; current=" + current());
      if (!current()) return;
      if (!readyNotified) {
        readyNotified = true;
        listener.ready();
      }
      listener.status(context.getString(R.string.listening));
    }

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rms) {}

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
      if (current()) listener.status(context.getString(R.string.processing));
    }

    @Override
    public void onError(int error) {
      android.util.Log.w("KeyboardDictation", "Recognition error=" + error + "; current=" + current());
      if (!current()) return;
      cancel();
      listener.failed(error);
    }

    @Override
    public void onResults(Bundle results) {
      android.util.Log.i("KeyboardDictation", "Final result received; current=" + current());
      if (!current()) return;
      String text = first(results);
      cancel();
      listener.done(text);
    }

    @Override
    public void onPartialResults(Bundle results) {
      android.util.Log.i("KeyboardDictation", "Partial result received; current=" + current());
      if (current()) listener.partial(first(results));
    }

    @Override
    public void onEvent(int type, Bundle params) {}
  }
}
