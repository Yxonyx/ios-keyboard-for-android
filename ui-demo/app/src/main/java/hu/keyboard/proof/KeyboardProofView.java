// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Native Canvas keyboard. Layouts are generated from row rules; labels use the Inter font. */
public final class KeyboardProofView extends View {
  private static final float W = 1179, H = 2556, PANEL_TOP = 1650, STRIP = 96;
  private static final int BOARD = Color.rgb(226, 227, 232);
  private static final Set<String> ICON_KEYS = Set.of("shift", "delete", "return", "space");
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Map<String, List<Key>> states = new HashMap<>();
  private final KeyboardSettings settings;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener
      settingsListener = (prefs, key) -> post(this::refreshSettings);

  public interface Output {
    default void key(String value, int x, int y) {
      text(value);
    }

    void text(String value);

    void delete();

    void enter();

    void emoji();

    void settings();

    void next();

    void cursor(int direction);

    void popup(boolean shown);

    default void deleteWord() {
      delete();
    }

    /** A suggestion strip slot was tapped; null pastes the offered clipboard text. */
    default void pick(String value) {}

    /** Re-reads the editor's capitalization after a held key that suppressed it. */
    default void shiftCheck() {}

    /** The microphone key: starts dictation, or stops the running one. */
    default void dictate() {}
  }

  private Output output;
  private boolean imeMode, privateEditor, numericEditor, stripShown, editorHasText;
  private String enterLabel = "";
  private float imeBoardPixels;
  private CompositionSession.Suggestions suggestions = CompositionSession.Suggestions.NONE;
  private String clipPreview, dictation;
  // True while the window blurs what is behind it; the tint then can be lighter (frosted glass).
  private boolean blurred;
  private int stripSlot = -1, deleteRepeats;

  /** Reserves the strip area above the keys; accent popups and key previews draw inside it. */
  public void setSuggestionStrip(boolean shown) {
    stripShown = shown;
    invalidate();
  }

  /** The strip shows once the field holds text (or a clip is offered), not in an empty field. */
  public boolean stripVisible() {
    if (!imeMode || privateEditor) return false;
    return dictation != null
        || stripShown
            && (editorHasText || !suggestions.typed().isEmpty() || clipPreview != null);
  }

  public void setBlurred(boolean value) {
    if (blurred != value) {
      blurred = value;
      invalidate();
    }
  }

  /** Dictation status or live text in the strip; null when not dictating. */
  public void setDictation(String value) {
    dictation = value;
    invalidate();
  }

  public void setEditorHasText(boolean value) {
    if (editorHasText != value) {
      editorHasText = value;
      invalidate();
    }
  }

  public void showSuggestions(CompositionSession.Suggestions value, String clip) {
    suggestions = value == null ? CompositionSession.Suggestions.NONE : value;
    clipPreview = clip;
    invalidate();
  }

  /** Applies the editor's capitalization mode unless Caps Lock or a gesture is active. */
  public void autoShift(boolean capitals) {
    if (caps || !page.equals("letters") || pressed != null || popupKey != null) return;
    if (upper != capitals) {
      upper = capitals;
      invalidate();
    }
  }

  public void setImeBoardPixels(float pixels) {
    imeBoardPixels = pixels;
    invalidate();
  }

  private float spaceX, touchX, touchY;
  private int activePointer = -1;

  public void setOutput(Output value) {
    output = value;
    imeMode = value != null;
    setContentDescription(getContext().getString(R.string.keyboard_description));
  }

  public void startEditor(boolean capitals, boolean numbers, boolean sensitive, String action) {
    handler.removeCallbacksAndMessages(null);
    pressed = null;
    popupKey = null;
    input.setLength(0);
    upper = capitals;
    caps = false;
    privateEditor = sensitive;
    numericEditor = numbers;
    stripSlot = -1;
    suggestions = CompositionSession.Suggestions.NONE;
    enterLabel = action;
    page = numbers ? "numbers" : "letters";
    invalidate();
  }

  private void emit(String value) {
    if (imeMode) output.key(value, Math.round(touchX), Math.round(touchY - PANEL_TOP));
    else input.append(value);
  }

  private boolean drawingBoard, emojiMode, slidingText;
  private float boardHeight = 1;
  private final Typeface font;
  private final StringBuilder input = new StringBuilder();
  private String page = "letters", popupKey;
  private Key pressed;
  private int popupSelection;
  private boolean upper = true, caps = false, longPressed = false;
  private float scale = 1, shiftY = 0;
  private long lastShift;
  private static final String[] CONTROLS = {"ABC", "123", "#+=", "Á", "É", "Ő", "Ű"};

  private static final class Key {
    final String label;
    final RectF rect;

    Key(JSONObject obj) throws JSONException {
      label = obj.getString("label");
      rect = rect(obj.getJSONArray("rect"));
    }

    /** A key whose label is drawn with the font. */
    Key(String label, RectF rect) {
      this.label = label;
      this.rect = rect;
    }
  }

  private static final class Popup {
    final RectF rect;
    final List<Key> cells = new ArrayList<>();
    final int initial;

    /** Accent choices are laid out from the active key and its language definition. */
    Popup(Key key, String variants) {
      List<String> labels = new ArrayList<>();
      labels.add(key.label);
      variants.codePoints().forEach(cp -> labels.add(new String(Character.toChars(cp))));
      while (labels.size() > 9) labels.remove(labels.size() - 1);
      boolean rightward = key.rect.centerX() < W / 2;
      if (!rightward) Collections.reverse(labels);
      float width = 56 + labels.size() * 110;
      float left =
          rightward ? key.rect.centerX() - 83 : key.rect.centerX() + 83 - width;
      left = Math.max(20, Math.min(W - 20 - width, left));
      rect = new RectF(left, 1514, left + width, 1720);
      initial = rightward ? 0 : labels.size() - 1;
      for (int i = 0; i < labels.size(); i++)
        cells.add(
            new Key(labels.get(i), new RectF(left + 28 + i * 110, 1544, left + 138 + i * 110, 1690)));
    }
  }

  private static RectF rect(JSONArray a) throws JSONException {
    return new RectF(
        (float) a.getDouble(0),
        (float) a.getDouble(1),
        (float) a.getDouble(2),
        (float) a.getDouble(3));
  }

  public KeyboardProofView(Context context) {
    super(context);
    settings = new KeyboardSettings(context);
    font =
        new Typeface.Builder(context.getAssets(), "fonts/Inter.ttf")
            .setFontVariationSettings("'wght' 400, 'opsz' 28")
            .build();
    try {
      String json =
          new String(
              context.getAssets().open("geometry.json").readAllBytes(), StandardCharsets.UTF_8);
      JSONObject data = new JSONObject(json);
      JSONObject layouts = data.getJSONObject("states");
      for (String state : List.of("letters", "numbers", "symbols")) {
        JSONArray arr = layouts.getJSONArray(state);
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) keys.add(new Key(arr.getJSONObject(i)));
        states.put(state, keys);
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid generated keyboard geometry", ex);
    }
    setFocusable(true);
    setContentDescription("iOS Keyboard for Android, interactive keyboard preview");
  }

  public void showPreview(String name) {
    handler.removeCallbacksAndMessages(null);
    popupKey = null;
    pressed = null;
    upper = true;
    caps = false;
    input.setLength(0);
    page = name != null && (name.equals("numbers") || name.equals("symbols")) ? name : "letters";
    if (name != null && name.startsWith("accent-"))
      openPopup(name.substring(7).toUpperCase(Locale.ROOT));
    invalidate();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    scale = getWidth() / W;
    shiftY = getHeight() / scale - H;
    boardHeight = imeMode ? imeBoardPixels / (906f * scale) : settings.number("height", 100) / 100f;
    if (imeMode) c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    else c.drawColor(Color.rgb(252, 252, 252));
    c.save();
    c.scale(scale, scale);
    if (!imeMode) drawHost(c);
    c.translate(0, shiftY + H * (1 - boardHeight));
    c.scale(1, boardHeight);
    drawingBoard = true;

    drawBoard(c);
    if (popupKey != null) drawPopup(c);
    drawingBoard = false;
    c.restore();
  }

  public void refreshSettings() {
    handler.removeCallbacksAndMessages(null);
    pressed = null;
    popupKey = null;
    invalidate();
  }

  private boolean isDark() {
    String theme = settings.text("theme", "system");
    return theme.equals("dark")
        || (theme.equals("system")
            && (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES);
  }

  private String layoutKey = "";
  private List<Key> letters = List.of();
  private final Map<String, Popup> languagePopups = new HashMap<>();

  private static final Map<String, Map<String, String>> MOVES =
      Map.of(
          "QWERTZ", Map.of(),
          "QWERTY", Map.of("Z", "Y", "Y", "Z"),
          "AZERTY", Map.of("Q", "A", "W", "Z", "Z", "Y", "A", "Q", "Y", "W", "M", "'"));

  /** Applies the active language to the generated base rows and builds accent choices. */
  private void ensureLayout() {
    Languages.Language language = settings.current();
    String layout = settings.layout(), wanted = language.code() + layout;
    if (wanted.equals(layoutKey)) return;
    layoutKey = wanted;
    Map<String, String> moves = MOVES.get(layout);
    String extra = layout.equals("AZERTY") ? "M" : language.extra();
    List<Key> baseRows = states.get("letters"), result = new ArrayList<>(), top = new ArrayList<>();
    List<Key> middle = new ArrayList<>();
    for (Key k : baseRows) {
      if (k.rect.top < 1800) top.add(k);
      else if (k.rect.top < 1950) middle.add(k);
    }
    for (Key k : baseRows) {
      String label = moves.getOrDefault(k.label, k.label);
      if (extra != null && middle.contains(k)) continue;
      result.add(label.equals(k.label) ? k : new Key(label, k.rect));
    }
    if (extra != null) {
      for (int i = 0; i < 10; i++) {
        String label = i < middle.size()
            ? moves.getOrDefault(middle.get(i).label, middle.get(i).label)
            : extra;
        RectF rect =
            new RectF(top.get(i).rect.left, middle.get(0).rect.top, top.get(i).rect.right,
                middle.get(0).rect.bottom);
        result.add(new Key(label, rect));
      }
    }
    letters = result;
    languagePopups.clear();
    Map<String, String> accents = language.accentMap();
    for (Key k : letters) {
      String variants = accents.get(k.label);
      if (variants != null) languagePopups.put(k.label, new Popup(k, variants));
    }
  }

  private List<Key> keys(String name) {
    if (!name.equals("letters")) return states.get(name);
    ensureLayout();
    return letters;
  }

  private Map<String, Popup> popups() {
    ensureLayout();
    return languagePopups;
  }

  private Locale locale() {
    return settings.current().locale();
  }

  private void fill(int color) {
    paint.reset();
    paint.setAntiAlias(true);
    paint.setSubpixelText(true);
    if (drawingBoard && isDark()) {
      if (color == Color.BLACK) color = Color.WHITE;
      else if (color == Color.WHITE) color = 0xe0444548;
      else if (color == BOARD) color = blurred ? 0x9c292b2f : 0xf0292b2f;
      else if (color == Color.rgb(183, 184, 190)) color = 0x665a5b5e;
      else if (color == Color.rgb(230, 231, 235)) color = 0xa088898c;
      else if (color == Color.rgb(211, 212, 218)) color = 0x716b6c6f;
      else if (color == Color.rgb(230, 230, 234)) color = 0x9278797c;
    } else if (drawingBoard && color == BOARD) color = blurred ? 0xa6e3e3e5 : 0xf2e3e3e5;
    paint.setColor(color);
    paint.setStyle(Paint.Style.FILL);
    paint.setTextAlign(Paint.Align.LEFT);
  }

  private void text(Canvas c, String value, float x, float baseline, float size, int color) {
    fill(color);
    paint.setTypeface(font);
    paint.setTextSize(size);
    c.drawText(value, x, baseline, paint);
  }

  private void drawHost(Canvas c) {
    float top =
        Math.max(
            140,
            getRootWindowInsets() == null
                ? 140
                : getRootWindowInsets().getDisplayCutout() == null
                    ? 140
                    : getRootWindowInsets().getDisplayCutout().getSafeInsetTop() / scale + 80);
    text(c, "iOS Keyboard for Android", 56, top + 35, 65, Color.BLACK);
    text(c, getContext().getString(R.string.interactive_preview), 58, top + 102, 32, Color.rgb(112, 112, 120));
    for (int i = 0; i < CONTROLS.length; i++) {
      RectF r = controlRect(i);
      boolean active =
          (i == 0 && page.equals("letters") && popupKey == null)
              || (i == 1 && page.equals("numbers"))
              || (i == 2 && page.equals("symbols"))
              || (popupKey != null
                  && i >= 3
                  && "AE OU".contains(popupKey)
                  && controlPopup(i).equals(popupKey));
      fill(active ? Color.BLACK : Color.rgb(235, 235, 239));
      c.drawRoundRect(r, 31, 31, paint);
      fill(active ? Color.WHITE : Color.BLACK);
      paint.setTypeface(font);
      paint.setTextSize(35);
      paint.setTextAlign(Paint.Align.CENTER);
      c.drawText(CONTROLS[i], r.centerX(), r.centerY() + 12, paint);
    }
    text(c, getContext().getString(R.string.preview_instruction), 58, 605, 31, Color.rgb(108, 108, 118));
    text(
        c,
        "⚙ " + getContext().getString(R.string.settings) + " · "
            + settings.language().toUpperCase(Locale.ROOT)
            + " · "
            + settings.layout(),
        58,
        710,
        34,
        Color.rgb(20, 95, 195));
    float composerY = (emojiMode ? 1090 : 1320) + shiftY;
    fill(Color.WHITE);
    c.drawRoundRect(new RectF(36, composerY, 1143, composerY + 180), 48, 48, paint);
    fill(Color.rgb(220, 220, 226));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1.5f);
    c.drawRoundRect(new RectF(36, composerY, 1143, composerY + 180), 48, 48, paint);
    String value = input.length() == 0 ? getContext().getString(R.string.type_here) : input.toString();
    fill(input.length() == 0 ? Color.rgb(150, 150, 158) : Color.BLACK);
    paint.setTypeface(font);
    paint.setTextSize(43);
    c.save();
    c.clipRect(74, composerY + 16, 1106, composerY + 164);
    String[] lines = value.split("\n", -1);
    c.drawText(lines[lines.length - 1], 78, composerY + 107, paint);
    float end = 78 + paint.measureText(lines[lines.length - 1]);
    fill(Color.rgb(0, 174, 239));
    c.drawRoundRect(
        new RectF(
            Math.min(end + 5, 1100), composerY + 57, Math.min(end + 8, 1103), composerY + 113),
        1,
        1,
        paint);
    c.restore();
  }

  private RectF controlRect(int i) {
    return new RectF(56 + i * 154, 390, 194 + i * 154, 478);
  }

  private String controlPopup(int i) {
    return new String[] {"A", "E", "O", "U"}[i - 3];
  }

  private void drawBoard(Canvas c) {
    // Sampled outline coordinates, in the original image's 1179px space.
    float t = 1652 - (stripVisible() ? STRIP : 0);
    Path p = new Path();
    p.moveTo(82, t);
    p.cubicTo(48, t, 0, t + 18, 0, t + 108);
    p.lineTo(0, 2348);
    p.cubicTo(0, 2476, 40, 2530, 137, 2556);
    p.lineTo(1042, 2556);
    p.cubicTo(1139, 2530, 1179, 2476, 1179, 2348);
    p.lineTo(1179, t + 108);
    p.cubicTo(1179, t + 18, 1131, t, 1097, t);
    p.close();
    boolean dark = isDark();
    fill(BOARD);
    c.drawPath(p, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dark ? 1.1f : 1.5f);
    paint.setColor(dark ? 0x6656575a : 0x66a7a7aa);
    c.drawPath(p, paint);
    // Fine white bevel along the top rim.
    Path rim = new Path();
    rim.moveTo(2, t + 106);
    rim.cubicTo(2, t + 21, 48, t + 3, 82, t + 3);
    rim.lineTo(1097, t + 3);
    rim.cubicTo(1130, t + 3, 1177, t + 21, 1177, t + 106);
    paint.setColor(dark ? 0x556e7073 : 0x99ffffff);
    paint.setStrokeWidth(dark ? 1.6f : 2);
    c.drawPath(rim, paint);
    if (stripVisible()) drawStrip(c, dark);
    for (Key k : keys(page)) {
      boolean down = pressed == k || (popupKey != null && k.label.equals(popupKey));
      boolean utility =
          k.label.equals("shift")
              || k.label.equals("delete")
              || k.label.equals("123")
              || k.label.equals("#+=")
              || k.label.equals("return")
              || k.label.equals("space");
      int keyColor =
          dark
              ? (down ? 0xa088898c : (utility ? 0x80747578 : 0x706c6d70))
              : (down ? 0xe6d9d9dc : blurred ? 0xc8ffffff : 0xf0ffffff);
      fill(keyColor);
      c.drawRoundRect(k.rect, 26, 26, paint);
      if (down) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.7f);
        paint.setColor(dark ? 0x818b8c8f : 0x99c7c7ca);
        c.drawRoundRect(k.rect, 26, 26, paint);
      }
      if (ICON_KEYS.contains(k.label)) {
        if (!k.label.equals("space")) drawKeyIcon(c, k);
      } else drawKeyLabel(c, k);
    }
    if (settings.enabled("emoji", true) && !privateEditor) drawEmoji(c);
    drawMicrophone(c);
    if (pressed != null && popupKey == null) drawKeyPreview(c, pressed, dark);
  }

  private String[] stripSlots() {
    if (privateEditor) return new String[3];
    if (dictation != null) return new String[] {null, dictation, null};
    if (suggestions.typed().isEmpty())
      return new String[] {null, clipPreview == null ? null : getContext().getString(R.string.paste_preview, clipPreview), null};
    List<String> rest = new ArrayList<>(suggestions.others());
    String middle =
        suggestions.correction() != null
            ? suggestions.correction()
            : rest.isEmpty() ? null : rest.remove(0);
    return new String[] {
      "„" + suggestions.typed() + "”", middle, rest.isEmpty() ? null : rest.get(0)
    };
  }

  private void drawStrip(Canvas c, boolean dark) {
    // The strip also uses the gap above the first key row (keys start at 1728).
    float top = 1652 - STRIP + 4, bottom = 1712, slot = W / 3;
    String[] slots = stripSlots();
    boolean idleClip = (dictation != null || suggestions.typed().isEmpty()) && slots[1] != null;
    for (int i = 0; i < 3; i++) {
      if (slots[i] == null) continue;
      RectF cell =
          idleClip
              ? new RectF(150, top + 8, W - 150, bottom - 8)
              : new RectF(i * slot + 12, top + 8, (i + 1) * slot - 12, bottom - 8);
      boolean highlight =
          i == stripSlot || (i == 1 && (dictation != null || suggestions.correction() != null));
      if (highlight) {
        fill(dark ? 0x80747578 : 0xe0ffffff);
        if (i == stripSlot) fill(dark ? 0xa088898c : 0xe6d9d9dc);
        c.drawRoundRect(cell, 30, 30, paint);
      }
      fill(Color.BLACK);
      paint.setTypeface(font);
      float size = 46;
      paint.setTextSize(size);
      float room = cell.width() - 36;
      String value = slots[i];
      while (paint.measureText(value) > room && size > 30) paint.setTextSize(size -= 2);
      if (paint.measureText(value) > room) {
        while (value.length() > 1 && paint.measureText(value + "…") > room)
          value = value.substring(0, value.length() - 1);
        value += "…";
      }
      paint.setTextAlign(Paint.Align.CENTER);
      c.drawText(value, cell.centerX(), cell.centerY() + size * 0.36f, paint);
    }
    if (!idleClip)
      for (int i = 1; i < 3; i++) {
        boolean besideHighlight =
            (suggestions.correction() != null && (i == 1 || i == 2))
                || stripSlot == i
                || stripSlot == i - 1;
        if (besideHighlight) continue;
        fill(dark ? 0x665a5b5e : 0xffbfc0c5);
        c.drawRect(i * slot - 1, top + 34, i * slot + 1, bottom - 34, paint);
      }
  }

  private int stripSlotAt(float x, float y) {
    if (!stripVisible() || y < 1652 - STRIP || y >= 1696) return -1;
    String[] slots = stripSlots();
    if (dictation != null || suggestions.typed().isEmpty()) return slots[1] == null ? -1 : 1;
    int i = Math.max(0, Math.min(2, (int) (x / (W / 3))));
    return slots[i] == null ? -1 : i;
  }

  private void selectStripSlot(int i) {
    if (dictation != null) {
      output.dictate();
      return;
    }
    if (suggestions.typed().isEmpty()) {
      output.pick(null);
      return;
    }
    if (i == 0) output.pick(suggestions.typed());
    else {
      String value = stripSlots()[i];
      if (value != null) output.pick(value);
    }
  }

  /** iPhone-style callout above a pressed character key. */
  private void drawKeyPreview(Canvas c, Key k, boolean dark) {
    if (privateEditor || !settings.enabled("key_preview", true)) return;
    String label = k.label;
    if (ICON_KEYS.contains(k.label) || label.length() > 2 || List.of(CONTROLS).contains(label))
      return;
    if (page.equals("letters") && !upper) label = label.toLowerCase(locale());
    float keyHeight = k.rect.height(), headTop = k.rect.top - keyHeight * 1.18f;
    float limit = 1652 - (imeMode && stripShown ? STRIP : 0);
    if (headTop < limit) return;
    float half = k.rect.width() * 0.78f;
    float left = Math.max(4, k.rect.centerX() - half), right = Math.min(W - 4, left + 2 * half);
    left = right - 2 * half;
    float headBottom = k.rect.top + keyHeight * 0.06f;
    Path p = new Path();
    p.addRoundRect(new RectF(left, headTop, right, headBottom), 34, 34, Path.Direction.CW);
    Path neck = new Path();
    neck.moveTo(left + 6, headBottom - 30);
    neck.lineTo(right - 6, headBottom - 30);
    neck.cubicTo(
        right - 6, headBottom + 10, k.rect.right, headBottom + 6, k.rect.right, k.rect.top + 40);
    neck.lineTo(k.rect.right, k.rect.bottom - 26);
    neck.quadTo(k.rect.right, k.rect.bottom, k.rect.right - 26, k.rect.bottom);
    neck.lineTo(k.rect.left + 26, k.rect.bottom);
    neck.quadTo(k.rect.left, k.rect.bottom, k.rect.left, k.rect.bottom - 26);
    neck.lineTo(k.rect.left, k.rect.top + 40);
    neck.cubicTo(k.rect.left, headBottom + 6, left + 6, headBottom + 10, left + 6, headBottom - 30);
    neck.close();
    p.op(neck, Path.Op.UNION);
    fill(dark ? 0xff6c6d70 : Color.WHITE);
    paint.setShadowLayer(16, 0, 4, dark ? 0x66000000 : 0x33000000);
    c.drawPath(p, paint);
    paint.clearShadowLayer();
    fill(Color.BLACK);
    paint.setTypeface(font);
    paint.setTextSize(108);
    paint.setTextAlign(Paint.Align.CENTER);
    c.drawText(label, (left + right) / 2, headTop + (headBottom - headTop) * 0.5f + 38, paint);
  }

  private void drawKeyLabel(Canvas c, Key k) {
    String label = k.label;
    if (page.equals("letters") && label.length() == 1 && !upper)
      label = label.toLowerCase(locale());
    fill(Color.BLACK);
    paint.setTypeface(font);
    paint.setTextSize(label.length() > 2 ? 42 : 64);
    paint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    c.drawText(label, k.rect.centerX(), k.rect.centerY() - (metrics.ascent + metrics.descent) / 2, paint);
  }

  private void stroke(float width) {
    fill(Color.BLACK);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(width);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
  }

  private void drawKeyIcon(Canvas c, Key k) {
    c.save();
    c.translate(k.rect.centerX(), k.rect.centerY());
    switch (k.label) {
      case "shift":
        {
          Path p = new Path();
          p.moveTo(-30, -2);
          p.lineTo(0, -26);
          p.lineTo(30, -2);
          p.quadTo(33, 2, 27, 3);
          p.lineTo(11, 3);
          p.lineTo(11, 22);
          p.quadTo(11, 26, 7, 26);
          p.lineTo(-7, 26);
          p.quadTo(-11, 26, -11, 22);
          p.lineTo(-11, 3);
          p.lineTo(-27, 3);
          p.quadTo(-33, 2, -30, -2);
          p.close();
          if (upper) {
            fill(Color.BLACK);
            c.drawPath(p, paint);
          } else {
            stroke(4);
            c.drawPath(p, paint);
          }
          if (caps) {
            fill(Color.BLACK);
            c.drawRoundRect(new RectF(-12, 32, 12, 37), 2, 2, paint);
          }
          break;
        }
      case "delete":
        {
          Path p = new Path();
          p.moveTo(-30, 0);
          p.lineTo(-12, -22);
          p.quadTo(-9, -25, -5, -25);
          p.lineTo(24, -25);
          p.quadTo(31, -25, 31, -18);
          p.lineTo(31, 18);
          p.quadTo(31, 25, 24, 25);
          p.lineTo(-5, 25);
          p.quadTo(-9, 25, -12, 22);
          p.close();
          stroke(4.5f);
          c.drawPath(p, paint);
          c.drawLine(-3, -9, 14, 9, paint);
          c.drawLine(-3, 9, 14, -9, paint);
          break;
        }
      case "return":
        {
          if (imeMode && !enterLabel.isEmpty()) {
            fill(Color.BLACK);
            paint.setTypeface(font);
            paint.setTextSize(36);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(enterLabel, 0, 12, paint);
            break;
          }
          stroke(4.5f);
          Path p = new Path();
          p.moveTo(1, -26);
          p.lineTo(22, -26);
          p.quadTo(30, -26, 30, -18);
          p.lineTo(30, -3);
          p.quadTo(30, 5, 22, 5);
          p.lineTo(-30, 5);
          c.drawPath(p, paint);
          c.drawLine(-30, 5, -12, -13, paint);
          c.drawLine(-30, 5, -12, 23, paint);
          break;
        }
    }
    c.restore();
  }

  private void drawEmoji(Canvas c) {
    c.save();
    c.translate(127.5f, 2436);
    stroke(4.7f);
    c.drawCircle(0, 0, 37.5f, paint);
    fill(Color.BLACK);
    c.drawOval(new RectF(-18, -15, -8, -3), paint);
    c.drawOval(new RectF(8, -15, 18, -3), paint);
    stroke(4.4f);
    Path p = new Path();
    p.moveTo(-24, 7);
    p.quadTo(0, 20, 24, 7);
    p.quadTo(21, 32, 0, 31);
    p.quadTo(-21, 30, -24, 7);
    c.drawPath(p, paint);
    c.drawArc(new RectF(-24, -6, 24, 27), 12, 156, false, paint);
    c.restore();
  }

  private void drawMicrophone(Canvas c) {
    if (dictation != null) {
      fill(0xffff3b30);
      c.drawCircle(1051, 2433, 56, paint);
    }
    c.save();
    c.translate(1051, 2433);
    stroke(4.8f);
    c.drawRoundRect(new RectF(-12, -39, 12, 14), 13, 13, paint);
    Path p = new Path();
    p.moveTo(-25, -7);
    p.lineTo(-25, 6);
    p.cubicTo(-25, 42, 25, 42, 25, 6);
    p.lineTo(25, -7);
    c.drawPath(p, paint);
    c.drawLine(0, 31, 0, 40, paint);
    c.drawLine(-17, 40, 17, 40, paint);
    c.restore();
  }

  private void drawPopup(Canvas c) {
    Popup popup = popups().get(popupKey);
    if (popup == null) return;
    fill(Color.WHITE);
    paint.setShadowLayer(14, 0, 3, 0x07000000);
    c.drawRoundRect(popup.rect, 58, 58, paint);
    paint.clearShadowLayer();
    // The original has a faint cyan reflection near its upper-right edge.
    fill(Color.WHITE);
    paint.setShader(
        new LinearGradient(
            popup.rect.right - 230,
            popup.rect.top,
            popup.rect.right,
            popup.rect.top + 180,
            new int[] {0x00e7ffff, 0x66e7ffff, 0x00e7ffff},
            null,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(popup.rect, 58, 58, paint);
    paint.setShader(null);
    for (int i = 0; i < popup.cells.size(); i++) {
      Key cell = popup.cells.get(i);
      if (i == popupSelection) {
        fill(Color.rgb(230, 230, 234));
        c.drawRoundRect(cell.rect, 32, 32, paint);
      }
      String value = upper ? cell.label : cell.label.toLowerCase(locale());
      fill(Color.BLACK);
      paint.setTypeface(font);
      paint.setTextSize(64);
      paint.setTextAlign(Paint.Align.CENTER);
      Paint.FontMetrics metrics = paint.getFontMetrics();
      c.drawText(value, cell.rect.centerX(), cell.rect.centerY() - (metrics.ascent + metrics.descent) / 2, paint);
    }
  }

  private void openPopup(String key) {
    if (!popups().containsKey(key)) return;
    popupKey = key;
    popupSelection = popups().get(key).initial;
    longPressed = true;
    if (imeMode) output.popup(true);
    invalidate();
  }

  /** Cover row gaps with deterministic nearest-key targets, independently of the drawn caps. */
  public int[][] correctionLayout() {
    List<Integer> codes = new ArrayList<>(), rectangles = new ArrayList<>();
    for (Key k : keys("letters")) {
      String label = k.label;
      if (label.equals("space")) label = " ";
      if (label.length() != 1) continue;
      codes.add((int) Character.toLowerCase(label.charAt(0)));
      Collections.addAll(
          rectangles,
          Math.round(k.rect.left),
          Math.round(k.rect.top - PANEL_TOP),
          Math.round(k.rect.width()),
          Math.round(k.rect.height()));
    }
    return new int[][] {
      codes.stream().mapToInt(Integer::intValue).toArray(),
      rectangles.stream().mapToInt(Integer::intValue).toArray()
    };
  }

  private Key hit(float x, float y) {
    if (x < 0 || x > W || y < 1696 || y >= 2355) return null;
    List<Key> keys = keys(page);
    float rowY = 0, bestY = Float.MAX_VALUE;
    for (Key key : keys) {
      float dy = Math.abs(y - key.rect.centerY());
      if (dy < bestY) {
        bestY = dy;
        rowY = key.rect.centerY();
      }
    }
    Key nearest = null;
    float bestEdge = Float.MAX_VALUE, bestCenter = Float.MAX_VALUE;
    for (Key key : keys) {
      if (Math.abs(key.rect.centerY() - rowY) > 1) continue;
      float edge = Math.max(0, Math.max(key.rect.left - x, x - key.rect.right));
      float center = Math.abs(x - key.rect.centerX());
      if (edge < bestEdge || (edge == bestEdge && center < bestCenter)) {
        nearest = key;
        bestEdge = edge;
        bestCenter = center;
      }
    }
    return nearest;
  }

  private void scheduleAccent(Key key) {
    if (key != null && popups().containsKey(key.label))
      handler.postDelayed(
          () -> {
            if (pressed == key && !longPressed) openPopup(key.label);
          },
          settings.number("long_press", 360));
  }

  private final Runnable repeatDelete =
      new Runnable() {
        @Override
        public void run() {
          if (pressed != null && pressed.label.equals("delete")) {
            boolean words = imeMode && !privateEditor && ++deleteRepeats > 14;
            if (words) output.deleteWord();
            else erase();
            int delay = settings.number("delete_repeat", 65);
            handler.postDelayed(this, words ? Math.max(delay, 190) : delay);
          }
        }
      };

  private void erase() {
    if (imeMode) {
      output.delete();
      return;
    }
    if (input.length() > 0) {
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\X").matcher(input);
      int start = 0;
      while (matcher.find()) start = matcher.start();
      input.delete(start, input.length());
    }
    updateDescription();
    invalidate();
  }

  private void updateDescription() {
    setContentDescription(
        imeMode ? getContext().getString(R.string.keyboard_description) : getContext().getString(R.string.preview_input_description, input));
  }

  private void commit(Key key) {
    switch (key.label) {
      case "shift":
        {
          long now = System.currentTimeMillis();
          if (now - lastShift < settings.number("shift_double", 330)) {
            caps = true;
            upper = true;
          } else {
            upper = !upper;
            caps = false;
          }
          lastShift = now;
          break;
        }
      case "delete":
        break; // initial deletion happens on DOWN, then repeat
      case "space":
        emit(" ");
        if (!numericEditor && !page.equals("letters")) page = "letters";
        break;
      case "return":
        if (imeMode) output.enter();
        else emit("\n");
        upper = true;
        break;
      case "123":
        page = "numbers";
        break;
      case "#+=":
        page = "symbols";
        break;
      case "ABC":
        page = "letters";
        break;
      default:
        emit(
            upper || !page.equals("letters")
                ? key.label
                : key.label.toLowerCase(locale()));
        if (!caps) upper = false;
    }
    updateDescription();
    invalidate();
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    int action = e.getActionMasked(), index = e.getActionIndex();
    if (action == MotionEvent.ACTION_MOVE) {
      index = e.findPointerIndex(activePointer);
      if (index < 0) return true;
    }
    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      if (popupKey != null) return true;
      handler.removeCallbacksAndMessages(null);
      if (pressed != null && !longPressed) commit(pressed);
      pressed = null;
      action = MotionEvent.ACTION_DOWN;
    } else if (action == MotionEvent.ACTION_POINTER_UP) {
      if (e.getPointerId(index) != activePointer) return true;
      action = MotionEvent.ACTION_UP;
    }
    if (action == MotionEvent.ACTION_UP
        && activePointer >= 0
        && e.getPointerId(index) != activePointer) return true;
    float x = e.getX(index) / scale,
        screenY = e.getY(index) / scale,
        y = (screenY - shiftY - H * (1 - boardHeight)) / boardHeight;
    touchX = x;
    touchY = y;
    if (action == MotionEvent.ACTION_DOWN) {
      activePointer = e.getPointerId(index);
      if (!imeMode && screenY >= 650 && screenY <= 750) {
        getContext()
            .startActivity(new android.content.Intent(getContext(), SettingsActivity.class));
        return true;
      }
      if (!imeMode && screenY >= 390 && screenY <= 478) {
        for (int i = 0; i < CONTROLS.length; i++)
          if (controlRect(i).contains(x, screenY)) {
            if (i < 3) showPreview(new String[] {"letters", "numbers", "symbols"}[i]);
            else showPreview("accent-" + controlPopup(i).toLowerCase(Locale.ROOT));
            performClick();
            return true;
          }
      }

      if (popupKey != null) {
        updatePopupSelection(x);
        invalidate();
        return true;
      }
      stripSlot = stripSlotAt(x, y);
      if (stripSlot >= 0) {
        pressed = null;
        if (settings.enabled("haptic", true))
          performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        invalidate();
        return true;
      }
      if (y >= 2370) {
        if (x > 970 && imeMode) output.dictate();
        else if (x > 970)
          Toast.makeText(
                  getContext(), getContext().getString(R.string.preview_no_dictation), Toast.LENGTH_SHORT)
              .show();
        else if (x < 220 && settings.enabled("emoji", true) && !privateEditor) {
          if (imeMode) {
            output.emoji();
            return true;
          }
          emojiMode = true;
          invalidate();
          EmojiPanel.show(
              getContext(),
              settings,
              value -> {
                input.append(value);
                updateDescription();
                invalidate();
              },
              this::erase,
              () -> {
                emojiMode = false;
                invalidate();
              });
        }
        return true;
      }
      pressed = hit(x, y);
      longPressed = false;
      spaceX = x;
      slidingText = pressed != null && pressed.label.length() == 1;
      if (pressed != null) {
        if (settings.enabled("haptic", true))
          performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (settings.enabled("sound", false))
          ((android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE))
              .playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.3f);
        if (pressed.label.equals("delete")) {
          deleteRepeats = 0;
          erase();
          handler.postDelayed(repeatDelete, settings.number("delete_delay", 380));
        } else if (imeMode && pressed.label.equals("space"))
          handler.postDelayed(
              () -> {
                if (pressed != null && pressed.label.equals("space") && !longPressed) {
                  longPressed = true;
                  output.next();
                }
              },
              Math.max(450, settings.number("long_press", 360)));
        else scheduleAccent(pressed);
        invalidate();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_MOVE) {
      if (stripSlot >= 0) {
        if (stripSlotAt(x, y) != stripSlot) {
          stripSlot = -1;
          invalidate();
        }
        return true;
      }
      if (popupKey != null) {
        updatePopupSelection(x);
        invalidate();
      } else if (imeMode
          && pressed != null
          && pressed.label.equals("space")
          && Math.abs(x - spaceX) > 42) {
        int direction = x > spaceX ? 1 : -1;
        output.cursor(direction);
        spaceX = x;
        longPressed = true;
      } else if (slidingText && !longPressed) {
        // Keep slight drift on the same key; allow intentional correction to its neighbour.
        if (pressed == null
            || x < pressed.rect.left - 22
            || x > pressed.rect.right + 22
            || y < pressed.rect.top - 28
            || y > pressed.rect.bottom + 28) {
          Key next = hit(x, y);
          if (next != null && next.label.length() != 1) next = null;
          if (next != pressed) {
            handler.removeCallbacksAndMessages(null);
            pressed = next;
            scheduleAccent(next);
            invalidate();
          }
        }
      } else if (pressed != null
          && (x < pressed.rect.left - 30
              || x > pressed.rect.right + 30
              || y < pressed.rect.top - 35
              || y > pressed.rect.bottom + 35)) {
        handler.removeCallbacksAndMessages(null);
        pressed = null;
        invalidate();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      handler.removeCallbacksAndMessages(null);
      if (stripSlot >= 0) {
        int slot = stripSlot;
        stripSlot = -1;
        activePointer = -1;
        selectStripSlot(slot);
        invalidate();
        return true;
      }
      if (popupKey != null) {
        Popup popup = popups().get(popupKey);
        String value = popup.cells.get(popupSelection).label;
        emit(upper ? value : value.toLowerCase(locale()));
        popupKey = null;
        if (imeMode) output.popup(false);
        if (!caps) upper = false;
        updateDescription();
      } else if (pressed != null && !longPressed) commit(pressed);
      boolean deleted = pressed != null && pressed.label.equals("delete");
      pressed = null;
      activePointer = -1;
      if (imeMode && deleted) output.shiftCheck();
      performClick();
      invalidate();
      return true;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      handler.removeCallbacksAndMessages(null);
      stripSlot = -1;
      pressed = null;
      activePointer = -1;
      popupKey = null;
      if (imeMode) output.popup(false);
      invalidate();
      return true;
    }
    return true;
  }

  private void updatePopupSelection(float x) {
    Popup popup = popups().get(popupKey);
    float best = Float.MAX_VALUE;
    for (int i = 0; i < popup.cells.size(); i++) {
      float distance = Math.abs(popup.cells.get(i).rect.centerX() - x);
      if (distance < best) {
        best = distance;
        popupSelection = i;
      }
    }
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener);
  }

  @Override
  protected void onDetachedFromWindow() {
    settings.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener);
    handler.removeCallbacksAndMessages(null);
    super.onDetachedFromWindow();
  }
}
