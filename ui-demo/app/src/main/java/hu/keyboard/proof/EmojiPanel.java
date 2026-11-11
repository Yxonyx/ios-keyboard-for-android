// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.app.*;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Reference-derived seven-column keyboard panel. Emoji artwork comes from the device font. */
public final class EmojiPanel {
  private static String normalize(String s) {
    return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
  }

  public static void show(Context c, KeyboardSettings settings, Consumer<String> insert) {
    show(c, settings, insert, null, () -> {});
  }

  public static void show(
      Context c,
      KeyboardSettings settings,
      Consumer<String> insert,
      Runnable erase,
      Runnable closed) {
    new Panel(c, settings, insert, erase, closed).show();
  }

  private static final class Panel extends Dialog {
    final Context context;
    final KeyboardSettings settings;
    final Consumer<String> insert;
    final Runnable erase, closed;
    final String[] names = {
      "Legutóbbi",
      "Arcok és emberek",
      "Állatok és természet",
      "Étel és ital",
      "Tevékenységek",
      "Utazás",
      "Tárgyak",
      "Jelek",
      "Zászlók"
    };
    LinearLayout grid, categories;
    EditText search;
    int category, page;
    float downX;
    int ink, bg, raised;

    Panel(Context c, KeyboardSettings s, Consumer<String> i, Runnable e, Runnable end) {
      super(c);
      context = c;
      settings = s;
      insert = i;
      erase = e;
      closed = end;
      category = Math.max(0, Math.min(8, s.number("emoji_category", 1)));
    }

    int dp(float n) {
      return Math.round(n * context.getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int color, int radius) {
      GradientDrawable d = new GradientDrawable();
      d.setColor(color);
      d.setCornerRadius(dp(radius));
      return d;
    }

    @Override
    public void onCreate(android.os.Bundle state) {
      super.onCreate(state);
      boolean dark =
          settings.text("theme", "system").equals("dark")
              || (settings.text("theme", "system").equals("system")
                  && (context.getResources().getConfiguration().uiMode & 48) == 32);
      ink = dark ? 0xffe5e6eb : 0xff575c65;
      bg = dark ? 0xff292b32 : 0xffe2e3e8;
      raised = dark ? 0xff3a3d45 : 0xffeff0f4;
      LinearLayout root = new LinearLayout(context);
      root.setOrientation(LinearLayout.VERTICAL);
      root.setPadding(dp(8), dp(8), dp(8), dp(8));
      root.setBackground(shape(bg, 28));
      root.setClipToOutline(true);
      root.setFocusableInTouchMode(true);
      LinearLayout searchRow = new LinearLayout(context);
      searchRow.setGravity(Gravity.CENTER_VERTICAL);
      searchRow.setPadding(dp(12), 0, dp(12), 0);
      searchRow.setBackground(shape(raised, 24));
      root.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(40)));
      CategoryIcon magnifier = new CategoryIcon(context, "search", ink);
      searchRow.addView(magnifier, new LinearLayout.LayoutParams(dp(20), dp(24)));
      search = new EditText(context);
      search.setSingleLine(true);
      search.setTextSize(16);
      search.setTextColor(ink);
      search.setHintTextColor(dark ? 0xffb0b3bb : 0xff737780);
      search.setHint("Emojik keresése");
      search.setContentDescription("Emojik keresése");
      search.setBackgroundColor(Color.TRANSPARENT);
      search.setPadding(dp(8), 0, 0, 0);
      searchRow.addView(search, new LinearLayout.LayoutParams(0, -1, 1));
      grid = new LinearLayout(context);
      grid.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, 0, 1);
      gl.setMargins(0, dp(8), 0, 0);
      root.addView(grid, gl);
      categories = new LinearLayout(context);
      categories.setGravity(Gravity.CENTER_VERTICAL);
      root.addView(categories, new LinearLayout.LayoutParams(-1, dp(44)));
      LinearLayout bottom = new LinearLayout(context);
      bottom.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      bottom.setPadding(dp(12), dp(8), dp(12), dp(12));
      root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(72)));
      TextView abc = new TextView(context);
      abc.setText("ABC");
      abc.setTextSize(17);
      abc.setTextColor(ink);
      abc.setGravity(Gravity.CENTER_VERTICAL);
      abc.setMinHeight(dp(44));
      abc.setMinWidth(dp(56));
      abc.setContentDescription("Vissza a betűkhöz");
      bottom.addView(abc);
      abc.setOnClickListener(v -> dismiss());
      View space = new View(context);
      bottom.addView(space, new LinearLayout.LayoutParams(0, 1, 1));
      CategoryIcon microphone = new CategoryIcon(context, "mic", ink);
      microphone.setContentDescription("Diktálás, még nem elérhető");
      bottom.addView(microphone, new LinearLayout.LayoutParams(dp(44), dp(44)));
      microphone.setOnClickListener(
          v -> Toast.makeText(context, "A diktálás még nem érhető el", Toast.LENGTH_SHORT).show());
      setContentView(root);
      setOnDismissListener(d -> closed.run());
      Window w = getWindow();
      if (w != null) {
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setGravity(Gravity.BOTTOM);
        w.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      }
      root.requestFocus();
      search.addTextChangedListener(
          new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            public void onTextChanged(CharSequence s, int st, int b, int c) {
              page = 0;
              render();
            }

            public void afterTextChanged(android.text.Editable e) {}
          });
      render();
    }

    @Override
    public void show() {
      super.show();
      getWindow()
          .setLayout(
              -1, (int) (context.getResources().getDisplayMetrics().widthPixels * 1164f / 1179f));
    }

    private int group(String[] item) {
      return Integer.parseInt(item[2]);
    }

    void render() {
      grid.removeAllViews();
      categories.removeAllViews();
      String query = normalize(search.getText().toString());
      List<String[]> filtered = new ArrayList<>();
      if (category == 0 && query.isEmpty()) {
        for (String value : settings.text("emoji_recent", "").split("\\|"))
          if (!value.isEmpty()) filtered.add(new String[] {value, "legutóbbi", ""});
      } else
        for (String[] item : EmojiCatalog.get(context).legacy(settings))
          if ((!query.isEmpty() && normalize(item[0] + " " + item[1]).contains(query))
              || (query.isEmpty() && group(item) == category)) filtered.add(item);
      int maxPage = Math.max(0, (filtered.size() - 1) / 28);
      page = Math.min(page, maxPage);
      for (int r = 0; r < 4; r++) {
        LinearLayout row = new LinearLayout(context);
        grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int col = 0; col < 7; col++) {
          int index = page * 28 + r * 7 + col;
          TextView cell = new TextView(context);
          cell.setGravity(Gravity.CENTER);
          cell.setTextSize(29);
          cell.setTextColor(Color.BLACK);
          cell.setTypeface(EmojiCatalog.font(context));
          cell.setIncludeFontPadding(false);
          row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
          if (index >= filtered.size()) continue;
          String[] item = filtered.get(index);
          String value = item[0];
          if (item[2].equals("Kéz") && !value.equals("🤝"))
            value = value.replace("️", "") + settings.text("skin", "");
          final String emoji = value;
          cell.setText(emoji);
          cell.setContentDescription(emoji + " " + item[1]);
          cell.setBackground(
              new RippleDrawable(ColorStateList.valueOf(0x15000000), null, shape(Color.WHITE, 8)));
          cell.setOnClickListener(
              v -> {
                insert.accept(emoji);
                LinkedHashSet<String> recent = new LinkedHashSet<>();
                recent.add(emoji);
                recent.addAll(Arrays.asList(settings.text("emoji_recent", "").split("\\|")));
                recent.remove("");
                List<String> values = new ArrayList<>(recent);
                settings
                    .prefs
                    .edit()
                    .putString(
                        "emoji_recent",
                        String.join("|", values.subList(0, Math.min(30, values.size()))))
                    .apply();
                if (settings.enabled("emoji_return", false)) dismiss();
              });
          cell.setOnTouchListener(
              (v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) downX = event.getX();
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                    && Math.abs(event.getX() - downX) > dp(36)) {
                  page = Math.max(0, Math.min(maxPage, page + (event.getX() < downX ? 1 : -1)));
                  render();
                  return true;
                }
                return false;
              });
        }
      }
      if (filtered.isEmpty()) {
        TextView empty = new TextView(context);
        empty.setText(query.isEmpty() ? "Még nincsenek legutóbbi emojik" : "Nincs találat");
        empty.setTextColor(ink);
        empty.setGravity(Gravity.CENTER);
        grid.removeAllViews();
        grid.addView(empty, new LinearLayout.LayoutParams(-1, -1));
      }
      for (int i = 0; i < names.length; i++) {
        final int selected = i;
        CategoryIcon icon = new CategoryIcon(context, "category" + i, ink);
        icon.setContentDescription(names[i]);
        if (category == i && query.isEmpty())
          icon.setBackground(shape(ink == 0xffe5e6eb ? 0xff444750 : 0xffced1d8, 18));
        icon.setSelected(category == i);
        categories.addView(icon, new LinearLayout.LayoutParams(0, dp(44), 1));
        icon.setOnClickListener(
            v -> {
              category = selected;
              settings.prefs.edit().putInt("emoji_category", selected).apply();
              page = 0;
              search.setText("");
              render();
            });
      }
      CategoryIcon backspace = new CategoryIcon(context, "delete", ink);
      backspace.setContentDescription("Törlés");
      categories.addView(backspace, new LinearLayout.LayoutParams(dp(40), dp(44)));
      backspace.setEnabled(erase != null);
      backspace.setAlpha(erase == null ? 0.45f : 1);
      backspace.setOnClickListener(
          v -> {
            if (erase != null) erase.run();
          });
    }
  }

  /** One outline family for category navigation, never emoji used as chrome. */
  public static final class CategoryIcon extends View {
    final String kind;
    final int color;
    final Paint p = new Paint(3);

    public CategoryIcon(Context c, String k, int ink) {
      super(c);
      kind = k;
      color = ink;
      setFocusable(true);
    }

    void line(Canvas c, float a, float b, float x, float y) {
      c.drawLine(a, b, x, y, p);
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float size =
          Math.min(
              Math.min(getWidth(), getHeight()), 24 * getResources().getDisplayMetrics().density);
      c.save();
      c.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
      c.scale(size / 24, size / 24);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.5f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      Path path = new Path();
      switch (kind) {
        case "search":
          c.drawCircle(10, 10, 6, p);
          line(c, 14.5f, 14.5f, 20, 20);
          break;
        case "mic":
          c.drawRoundRect(9, 3, 15, 15, 3, 3, p);
          path.addArc(5, 7, 19, 20, 0, 180);
          c.drawPath(path, p);
          line(c, 5, 10, 5, 13);
          line(c, 19, 10, 19, 13);
          line(c, 12, 20, 12, 23);
          line(c, 8, 23, 16, 23);
          break;
        case "delete":
          path.moveTo(3, 12);
          path.lineTo(8, 5);
          path.lineTo(22, 5);
          path.lineTo(22, 19);
          path.lineTo(8, 19);
          path.close();
          c.drawPath(path, p);
          line(c, 12, 9, 18, 15);
          line(c, 18, 9, 12, 15);
          break;
        case "category0":
          c.drawCircle(12, 12, 9, p);
          line(c, 12, 6, 12, 12);
          line(c, 12, 12, 8, 12);
          break;
        case "category1":
          c.drawCircle(12, 12, 9, p);
          c.drawCircle(9, 10, 0.6f, p);
          c.drawCircle(15, 10, 0.6f, p);
          path.addArc(7, 9, 17, 18, 20, 140);
          c.drawPath(path, p);
          break;
        case "category2":
          c.drawOval(7, 11, 17, 20, p);
          c.drawOval(3, 7, 7, 12, p);
          c.drawOval(8, 3, 12, 9, p);
          c.drawOval(14, 3, 18, 9, p);
          c.drawOval(19, 8, 22, 13, p);
          break;
        case "category3":
          path.moveTo(12, 7);
          path.cubicTo(2, 1, 1, 16, 7, 21);
          path.quadTo(10, 23, 12, 21);
          path.quadTo(17, 23, 20, 16);
          path.cubicTo(25, 5, 15, 3, 12, 7);
          c.drawPath(path, p);
          line(c, 12, 7, 14, 2);
          break;
        case "category4":
          c.drawCircle(12, 12, 9, p);
          path.moveTo(12, 7);
          path.lineTo(17, 11);
          path.lineTo(15, 17);
          path.lineTo(9, 17);
          path.lineTo(7, 11);
          path.close();
          c.drawPath(path, p);
          line(c, 12, 7, 12, 3);
          line(c, 17, 11, 21, 10);
          line(c, 15, 17, 18, 20);
          line(c, 9, 17, 6, 20);
          line(c, 7, 11, 3, 10);
          break;
        case "category5":
          c.drawRoundRect(3, 10, 21, 18, 2, 2, p);
          line(c, 5, 10, 8, 5);
          line(c, 8, 5, 17, 5);
          line(c, 17, 5, 20, 10);
          c.drawCircle(7, 18, 2, p);
          c.drawCircle(17, 18, 2, p);
          break;
        case "category6":
          c.drawOval(6, 2, 18, 15, p);
          line(c, 8, 13, 10, 19);
          line(c, 16, 13, 14, 19);
          line(c, 10, 19, 14, 19);
          line(c, 10, 22, 14, 22);
          break;
        case "category7":
          path.moveTo(12, 21);
          path.cubicTo(-5, 10, 6, -2, 12, 7);
          path.cubicTo(18, -2, 29, 10, 12, 21);
          c.drawPath(path, p);
          break;
        case "category8":
          line(c, 5, 3, 5, 22);
          path.moveTo(5, 4);
          path.cubicTo(11, 0, 14, 9, 21, 4);
          path.lineTo(21, 15);
          path.cubicTo(14, 20, 11, 11, 5, 15);
          c.drawPath(path, p);
          break;
      }
      c.restore();
    }
  }
}
