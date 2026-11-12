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
 * One utterance through an installed Android recognizer, preferring Google when available.
 * The provider may upload audio. Explicit approval is required; this keyboard stores no recording.
 */
final class Dictation {
  interface Listener {
    void status(String value);

    void partial(String text);

    void done(String text);

    void failed(int error);
  }

  /** Google's speech services first; the Google app; then whatever the system default is. */
  private static final String[] PREFERRED = {
    "com.google.android.tts", "com.google.android.googlequicksearchbox"
  };

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
    for (String wanted : PREFERRED)
      for (ResolveInfo info : services)
        if (info.serviceInfo.packageName.equals(wanted))
          return new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
    return null;
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

    Callbacks(SpeechRecognizer owner) {
      this.owner = owner;
    }

    private boolean current() {
      return owner == recognizer && active;
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
      if (current()) listener.status(context.getString(R.string.listening));
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
      if (!current()) return;
      cancel();
      listener.failed(error);
    }

    @Override
    public void onResults(Bundle results) {
      if (!current()) return;
      String text = first(results);
      cancel();
      listener.done(text);
    }

    @Override
    public void onPartialResults(Bundle results) {
      if (current()) listener.partial(first(results));
    }

    @Override
    public void onEvent(int type, Bundle params) {}
  }
}
