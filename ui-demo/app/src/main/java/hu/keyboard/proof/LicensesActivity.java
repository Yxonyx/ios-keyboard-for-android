// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;

/** License texts travel with the APK and can be read without a network connection. */
public final class LicensesActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    TextView text = new TextView(this);
    text.setTextSize(14);
    int padding = Math.round(20 * getResources().getDisplayMetrics().density);
    text.setPadding(padding, padding, padding, padding);
    text.setTextIsSelectable(true);
    text.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
    text.setMovementMethod(LinkMovementMethod.getInstance());
    StringBuilder contents = new StringBuilder();
    try {
      for (String name : new String[] {
        "NOTICE.txt", "GPL-3.0-only.txt", "Apache-2.0.txt", "OFL-1.1.txt", "Unicode-3.0.txt"
      }) {
        try (var input = getAssets().open("licenses/" + name)) {
          contents.append(name).append("\n\n")
              .append(new String(input.readAllBytes(), StandardCharsets.UTF_8)).append("\n\n");
        }
      }
    } catch (java.io.IOException error) {
      throw new IllegalStateException("Missing bundled license", error);
    }
    text.setText(contents);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(text);
    setContentView(scroll);
  }
}
