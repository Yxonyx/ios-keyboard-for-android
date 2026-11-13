// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/** Explicitly disclose the external recognizer before requesting microphone access. */
public final class MicPermissionActivity extends Activity {
  static final String CONSENT_KEY = "external_speech_consent_v1";

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    KeyboardSettings settings = new KeyboardSettings(this);
    if (!settings.enabled(CONSENT_KEY, false)) {
      new AlertDialog.Builder(this)
          .setTitle(R.string.speech_consent_title)
          .setMessage(R.string.speech_consent_message)
          .setPositiveButton(R.string.speech_consent_allow, (dialog, which) -> {
            settings.prefs.edit().putBoolean(CONSENT_KEY, true).apply();
            askPermission();
          })
          .setNegativeButton(R.string.speech_consent_decline, (dialog, which) -> finish())
          .setOnCancelListener(dialog -> finish())
          .show();
    } else if (state == null) askPermission();
  }

  private void askPermission() {
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
      ready();
    else requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 1);
  }

  private void ready() {
    Toast.makeText(this, R.string.speech_ready, Toast.LENGTH_SHORT).show();
    finish();
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
    Toast.makeText(
            this,
            granted
                ? getString(R.string.microphone_granted)
                : getString(R.string.microphone_denied),
            Toast.LENGTH_SHORT)
        .show();
    finish();
  }
}
