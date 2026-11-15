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
    heading.setText(getString(R.string.try_keyboard));
    heading.setTextSize(24);
    content.addView(heading);
    TextView note = new TextView(this);
    note.setText(getString(R.string.typing_test_note));
    content.addView(note);
    field(
        content,
        getString(R.string.message),
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        EditorInfo.IME_ACTION_NONE,
        4);
    field(content, getString(R.string.search), InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH, 1);
    field(
        content,
        getString(R.string.number),
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
        EditorInfo.IME_ACTION_NEXT,
        1);
    field(
        content,
        getString(R.string.password_test),
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
    field.setId(action == EditorInfo.IME_ACTION_SEARCH ? R.id.test_search
        : action == EditorInfo.IME_ACTION_NEXT ? R.id.test_number
        : action == EditorInfo.IME_ACTION_DONE ? R.id.test_password : R.id.test_message);
    field.setInputType(type);
    field.setImeOptions(action);
    field.setMinLines(lines);
    field.setMaxLines(lines);
    parent.addView(field);
    if (action == EditorInfo.IME_ACTION_SEARCH)
      field.setOnEditorActionListener(
          (view, id, event) -> {
            Toast.makeText(this, getString(R.string.search_action_works), Toast.LENGTH_SHORT).show();
            return true;
          });
  }
}
