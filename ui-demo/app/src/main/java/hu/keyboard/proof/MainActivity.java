// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/** Standalone visual proof. Does not replace the user's current input method. */
public final class MainActivity extends Activity {
  private KeyboardProofView keyboard;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setDecorFitsSystemWindows(false);
    keyboard = new KeyboardProofView(this);
    setContentView(keyboard);
    keyboard.post(this::hideSystemBars);
    applyIntent(getIntent());
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (keyboard != null) keyboard.invalidate();
  }

  private void hideSystemBars() {
    WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
    if (controller != null) {
      controller.hide(WindowInsets.Type.systemBars());
      controller.setSystemBarsBehavior(
          WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (focused) hideSystemBars();
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    applyIntent(intent);
  }

  private void applyIntent(Intent intent) {
    keyboard.showPreview(intent.getStringExtra("preview"));
  }
}
