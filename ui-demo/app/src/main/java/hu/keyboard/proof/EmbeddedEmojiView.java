// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Emoji panel embedded in the IME window: no dialog tokens or nested system keyboard. */
public final class EmbeddedEmojiView extends LinearLayout {
  private final KeyboardSettings settings;
  private final EmojiCatalog catalog;
  private final Consumer<String> insert;
  private final Runnable erase, letters, search;
  private String query;
  private TextView findView;
  private final LinearLayout grid, categories;
  private int category, page;
  private float downX;
  private int ink;
  private EmojiCatalog.Entry choosing;
  private final android.graphics.Typeface emojiFont;
  private final String[] names = {
    getContext().getString(R.string.emoji_recent),
    getContext().getString(R.string.emoji_people),
    getContext().getString(R.string.emoji_nature),
    getContext().getString(R.string.emoji_food),
    getContext().getString(R.string.emoji_activities),
    getContext().getString(R.string.emoji_travel),
    getContext().getString(R.string.emoji_objects),
    getContext().getString(R.string.emoji_symbols),
    getContext().getString(R.string.emoji_flags)
  };

  public EmbeddedEmojiView(
      Context context,
      KeyboardSettings prefs,
      String text,
      Consumer<String> commit,
      Runnable delete,
      Runnable back,
      Runnable find,
      Runnable dictate) {
    super(context);
    settings = prefs;
    catalog = EmojiCatalog.get(context);
    emojiFont = EmojiCatalog.font(context);
    query = text;
    insert = commit;
    erase = delete;
    letters = back;
    search = find;
    category = Math.max(0, Math.min(8, settings.number("emoji_category", 1)));
    setOrientation(VERTICAL);
    boolean dark =
        settings.text("theme", "system").equals("dark")
            || (settings.text("theme", "system").equals("system")
                && (getResources().getConfiguration().uiMode & 48) == 32);
    ink = dark ? 0xffeceef4 : 0xff575c65;
    setPadding(dp(8), dp(8), dp(8), dp(8));
    setBackground(shape(dark ? 0xc5292b2f : 0xc7e3e3e5, 28));
    findView = text(query.isEmpty() ? getContext().getString(R.string.search_emoji) : query, 16, ink);
    findView.setGravity(Gravity.CENTER_VERTICAL);
    findView.setPadding(dp(16), 0, dp(16), 0);
    findView.setBackground(shape(dark ? 0x80747578 : 0xd9f3f3f5, 24));
    findView.setContentDescription(getContext().getString(R.string.search_emoji));
    findView.setOnClickListener(v -> search.run());
    addView(findView, new LayoutParams(-1, dp(40)));
    grid = new LinearLayout(context);
    grid.setOrientation(VERTICAL);
    LayoutParams gl = new LayoutParams(-1, 0, 1);
    gl.setMargins(0, dp(8), 0, 0);
    addView(grid, gl);
    categories = new LinearLayout(context);
    categories.setGravity(Gravity.CENTER_VERTICAL);
    addView(categories, new LayoutParams(-1, dp(44)));
    LinearLayout bottom = new LinearLayout(context);
    bottom.setGravity(Gravity.CENTER_VERTICAL);
    bottom.setPadding(dp(12), 0, dp(12), 0);
    addView(bottom, new LayoutParams(-1, dp(48)));
    TextView abc = text("ABC", 17, ink);
    abc.setGravity(Gravity.CENTER_VERTICAL);
    abc.setMinWidth(dp(56));
    abc.setMinHeight(dp(44));
    abc.setOnClickListener(v -> letters.run());
    bottom.addView(abc);
    TextView position = text("", 11, ink);
    position.setGravity(Gravity.CENTER);
    bottom.addView(position, new LayoutParams(0, -2, 1));
    position.setTag("page");
    EmojiPanel.CategoryIcon microphone = new EmojiPanel.CategoryIcon(context, "mic", ink);
    microphone.setContentDescription(getContext().getString(R.string.dictation));
    microphone.setOnClickListener(v -> dictate.run());
    bottom.addView(microphone, new LayoutParams(dp(44), dp(44)));
    render();
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(getContext());
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setIncludeFontPadding(false);
    return t;
  }

  private void choose(EmojiCatalog.Entry entry) {
    insert.accept(entry.emoji);
    catalog.remember(settings, entry.emoji);
  }

  private void render() {
    grid.removeAllViews();
    categories.removeAllViews();
    List<EmojiCatalog.Entry> items =
        choosing == null ? catalog.filter(settings, category, query, settings.language()) : catalog.variants(choosing);
    int pages = Math.max(1, (items.size() + 27) / 28);
    page = Math.min(page, pages - 1);
    TextView position = findViewWithTag("page");
    if (position != null)
      position.setText(choosing != null ? getContext().getString(R.string.skin_variants) : (page + 1) + " / " + pages);
    for (int r = 0; r < 4; r++) {
      LinearLayout row = new LinearLayout(getContext());
      grid.addView(row, new LayoutParams(-1, 0, 1));
      for (int col = 0; col < 7; col++) {
        int index = page * 28 + r * 7 + col;
        TextView cell = text("", 29, Color.BLACK);
        cell.setTypeface(emojiFont);
        cell.setGravity(Gravity.CENTER);
        row.addView(cell, new LayoutParams(0, -1, 1));
        cell.setOnTouchListener(
            (v, event) -> {
              if (event.getActionMasked() == MotionEvent.ACTION_DOWN) downX = event.getX();
              if (event.getActionMasked() == MotionEvent.ACTION_UP
                  && Math.abs(event.getX() - downX) > dp(32)) {
                page = Math.max(0, Math.min(pages - 1, page + (event.getX() < downX ? 1 : -1)));
                render();
                return true;
              }
              return false;
            });
        cell.setClickable(true);
        if (index >= items.size()) continue;
        EmojiCatalog.Entry item = items.get(index);
        cell.setText(item.emoji);
        cell.setContentDescription(item.emoji + " " + item.keywords(settings.language()));
        cell.setOnClickListener(v -> choose(item));
        if (choosing == null && catalog.variants(item).size() > 1)
          cell.setOnLongClickListener(
              v -> {
                choosing = item;
                page = 0;
                render();
                return true;
              });
      }
    }
    if (items.isEmpty()) {
      grid.removeAllViews();
      TextView empty =
          text(query.isEmpty() ? getContext().getString(R.string.no_recent_emoji) : getContext().getString(R.string.no_results), 14, ink);
      empty.setGravity(Gravity.CENTER);
      grid.addView(empty, new LayoutParams(-1, -1));
    }
    for (int i = 0; i < names.length; i++) {
      final int selected = i;
      EmojiPanel.CategoryIcon icon = new EmojiPanel.CategoryIcon(getContext(), "category" + i, ink);
      icon.setContentDescription(names[i]);
      icon.setSelected(category == i && query.isEmpty());
      if (category == i && query.isEmpty()) icon.setBackground(shape(0x20575c65, 22));
      icon.setOnClickListener(
          v -> {
            category = selected;
            query = "";
            findView.setText(getContext().getString(R.string.search_emoji));
            choosing = null;
            page = 0;
            settings.prefs.edit().putInt("emoji_category", selected).apply();
            render();
          });
      categories.addView(icon, new LayoutParams(0, dp(44), 1));
    }
    EmojiPanel.CategoryIcon delete = new EmojiPanel.CategoryIcon(getContext(), "delete", ink);
    delete.setContentDescription(getContext().getString(R.string.delete));
    delete.setOnClickListener(v -> erase.run());
    categories.addView(delete, new LayoutParams(dp(40), dp(44)));
  }
}
