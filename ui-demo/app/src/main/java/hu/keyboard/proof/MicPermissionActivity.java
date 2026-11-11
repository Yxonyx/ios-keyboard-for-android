// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/** The keyboard cannot show a permission dialog itself; this invisible screen asks once. */
public final class MicPermissionActivity extends Activity {
  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
      finish();
    else if (state == null) requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 1);
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
    Toast.makeText(
            this,
            granted
                ? "Mikrofon engedélyezve. Koppints újra a mikrofonra."
                : "Mikrofon nélkül nem lehet diktálni.",
            Toast.LENGTH_SHORT)
        .show();
    finish();
  }
}
