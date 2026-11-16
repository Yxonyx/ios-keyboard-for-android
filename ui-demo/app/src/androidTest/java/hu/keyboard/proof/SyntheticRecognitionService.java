// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.content.*;
import android.os.Bundle;
import android.speech.*;
import java.util.ArrayList;
import java.util.List;

/** Disposable emulator fixture: delivers controlled partial/final text without opening the mic. */
public final class SyntheticRecognitionService extends RecognitionService {
  static final String FINISH = "hu.keyboard.proof.test.FINISH_SPEECH";
  private Callback callback;
  private final BroadcastReceiver finish = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      if (callback == null) return;
      try { callback.results(result("próba beszéd")); }
      catch (android.os.RemoteException disconnected) { /* Test already closed its editor. */ }
      callback = null;
    }
  };

  private static Bundle result(String text) {
    Bundle result = new Bundle();
    result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(List.of(text)));
    return result;
  }

  @Override public void onCreate() {
    super.onCreate();
    registerReceiver(finish, new IntentFilter(FINISH), Context.RECEIVER_EXPORTED);
  }

  @Override protected void onStartListening(Intent intent, Callback listener) {
    callback = listener;
    try {
      if (!intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)) {
        listener.error(SpeechRecognizer.ERROR_CLIENT);
        return;
      }
      listener.readyForSpeech(new Bundle());
      listener.partialResults(result("próba"));
    } catch (android.os.RemoteException disconnected) { callback = null; }
  }

  @Override protected void onStopListening(Callback listener) {}
  @Override protected void onCancel(Callback listener) { callback = null; }
  @Override public void onDestroy() { unregisterReceiver(finish); super.onDestroy(); }
}
