// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

public final class TypingActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    ScrollView scroll = new ScrollView(this);
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (24 * getResources().getDisplayMetrics().density);
    content.setPadding(pad, pad * 2, pad, pad);
    scroll.addView(content);
    TextView heading = new TextView(this);
    heading.setText("Billentyűzet kipróbálása");
    heading.setTextSize(24);
    content.addView(heading);
    TextView note = new TextView(this);
    note.setText("Valódi Android szövegmezők. Az ide beírt szöveget nem mentjük.");
    content.addView(note);
    field(
        content,
        "Üzenet",
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        EditorInfo.IME_ACTION_NONE,
        4);
    field(content, "Keresés", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH, 1);
    field(
        content,
        "Szám",
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
        EditorInfo.IME_ACTION_NEXT,
        1);
    field(
        content,
        "Jelszópróba",
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
        EditorInfo.IME_ACTION_DONE,
        1);
    setContentView(scroll);
  }

  private void field(LinearLayout parent, String name, int type, int action, int lines) {
    TextView label = new TextView(this);
    label.setText(name);
    label.setPadding(0, 24, 0, 8);
    parent.addView(label);
    EditText field = new EditText(this);
    field.setContentDescription(name);
    field.setInputType(type);
    field.setImeOptions(action);
    field.setMinLines(lines);
    field.setMaxLines(lines);
    parent.addView(field);
    if (name.equals("Keresés"))
      field.setOnEditorActionListener(
          (view, id, event) -> {
            Toast.makeText(this, "Keresés művelet működik", Toast.LENGTH_SHORT).show();
            return true;
          });
  }
}
