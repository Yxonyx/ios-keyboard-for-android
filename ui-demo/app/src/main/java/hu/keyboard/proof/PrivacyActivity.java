// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** Offline explanation of local typing, external speech and Android's IME warning. */
public final class PrivacyActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setTitle(R.string.privacy_title);
    TextView text = new TextView(this);
    int padding = Math.round(24 * getResources().getDisplayMetrics().density);
    text.setPadding(padding, padding, padding, padding);
    text.setTextSize(17);
    text.setTextIsSelectable(true);
    text.setText(getString(R.string.privacy_title) + "\n\n" + getString(R.string.privacy_summary));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(text);
    setContentView(scroll);
  }
}
