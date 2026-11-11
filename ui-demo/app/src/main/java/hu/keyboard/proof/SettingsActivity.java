// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.app.*;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Native settings: one overview, focused detail screens and immediate persistent changes. */
public final class SettingsActivity extends Activity {
  // Palette, chosen per build() from the system light/dark mode.
  private int INK, SECONDARY, GROUND, RULE, ACCENT, CARD, RAISED, TRACK, RIPPLE;
  private boolean dark;
  private KeyboardSettings settings;
  private LinearLayout content, group;
  private Typeface regular, medium;
  private String screen = "home";
  private int scrollPosition;
  private ScrollView scroll;

  @Override
  protected void onResume() {
    super.onResume();
    if (settings != null && (screen.equals("home") || screen.equals("setup"))) {
      if (scroll != null) scrollPosition = scroll.getScrollY();
      build();
    }
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getOnBackInvokedDispatcher()
        .registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
    settings = new KeyboardSettings(this);
    regular =
        new Typeface.Builder(getAssets(), "fonts/Inter.ttf")
            .setFontVariationSettings("'wght' 400")
            .build();
    medium =
        new Typeface.Builder(getAssets(), "fonts/Inter.ttf")
            .setFontVariationSettings("'wght' 500")
            .build();
    if (state != null) {
      screen = state.getString("screen", "home");
      scrollPosition = state.getInt("scroll", 0);
    }
    build();
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putString("screen", screen);
    state.putInt("scroll", scroll.getScrollY());
  }

  private void navigateBack() {
    if (!screen.equals("home")) {
      screen = "home";
      scrollPosition = 0;
      build();
    } else finish();
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

  private android.graphics.drawable.Drawable press(int color, int radius) {
    return new RippleDrawable(
        ColorStateList.valueOf(RIPPLE), shape(color, radius), shape(0xffffffff, radius));
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private TextView text(String value, int size, int color, boolean strong) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setTypeface(strong ? medium : regular);
    v.setIncludeFontPadding(false);
    v.setLineSpacing(dp(3), 1);
    return v;
  }

  private void addText(
      LinearLayout parent, String value, int size, int color, boolean strong, int top, int bottom) {
    TextView v = text(value, size, color, strong);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(top), 0, dp(bottom));
    parent.addView(v, lp);
  }

  private void open(String page) {
    screen = page;
    scrollPosition = 0;
    build();
  }

  private void palette() {
    dark =
        (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    INK = dark ? 0xffeceef2 : 0xff191b20;
    SECONDARY = dark ? 0xff9a9ea8 : 0xff62656e;
    GROUND = dark ? 0xff111214 : 0xfff3f4f6;
    RULE = dark ? 0xff2a2c31 : 0xffe9eaed;
    ACCENT = dark ? 0xff6ea1ff : 0xff1757b5;
    CARD = dark ? 0xff1c1d21 : Color.WHITE;
    RAISED = dark ? 0xff25272c : 0xffe7e9ed;
    TRACK = dark ? 0xff3d7bf0 : 0xff191b20;
    RIPPLE = dark ? 0x22ffffff : 0x14000000;
  }

  private void build() {
    palette();
    LinearLayout shell = column();
    shell.setBackgroundColor(GROUND);
    setContentView(shell);
    shell.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars =
              insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          shell.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    shell.requestApplyInsets();
    WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
    if (controller != null)
      controller.setSystemBarsAppearance(
          dark
              ? 0
              : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                  | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
          WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
              | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    LinearLayout toolbar = new LinearLayout(this);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.setPadding(dp(20), 0, dp(20), 0);
    shell.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));
    if (!screen.equals("home")) {
      Icon back = new Icon("back");
      back.setContentDescription("Vissza a beállításokhoz");
      back.setBackground(press(GROUND, 8));
      toolbar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
      back.setOnClickListener(v -> navigateBack());
    }
    TextView brand = text(screen.equals("home") ? "iOS Keyboard for Android" : "Beállítások", 16, INK, true);
    toolbar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    if (screen.equals("home")) {
      TextView version = text("0.8", 13, SECONDARY, false);
      toolbar.addView(version);
    }
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    content = column();
    content.setPadding(dp(20), dp(12), dp(20), dp(32));
    scroll.addView(content);
    switch (screen) {
      case "setup":
        setup();
        break;
      case "language":
        language();
        break;
      case "timing":
        timings();
        break;
      case "correction":
        correction();
        break;
      case "typing":
        typing();
        break;
      case "dictation":
        dictation();
        break;
      case "appearance":
        appearance();
        break;
      case "emoji":
        emoji();
        break;
      case "pending":
        pending();
        break;
      case "maintenance":
        maintenance();
        break;
      default:
        home();
    }
    scroll.post(() -> scroll.scrollTo(0, scrollPosition));
  }

  private void heading(String title, String description) {
    addText(content, title, 28, INK, true, 4, 12);
    if (!description.isEmpty()) addText(content, description, 14, SECONDARY, false, 0, 24);
  }

  private void section(String title) {
    addText(content, title, 14, SECONDARY, true, 24, 12);
    group = column();
    group.setBackground(shape(CARD, 12));
    group.setPadding(dp(16), 0, dp(16), 0);
    content.addView(group);
  }

  private void divider() {
    if (group.getChildCount() > 0) {
      View line = new View(this);
      line.setBackgroundColor(RULE);
      group.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }
  }

  private void row(
      String title, String value, String description, Runnable action, boolean destructive) {
    divider();
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(16), 0, dp(16));
    row.setMinimumHeight(dp(64));
    row.setBackground(press(CARD, 6));
    group.addView(row);
    LinearLayout labels = column();
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    addText(labels, title, 16, destructive ? 0xffb4232b : INK, false, 0, 0);
    if (!description.isEmpty()) addText(labels, description, 13, SECONDARY, false, 6, 0);
    if (!value.isEmpty()) {
      TextView detail = text(value, 14, SECONDARY, false);
      detail.setPadding(dp(12), 0, dp(12), 0);
      detail.setGravity(Gravity.END);
      row.addView(detail);
    }
    if (action != null) {
      Icon chevron = new Icon("chevron");
      row.addView(chevron, new LinearLayout.LayoutParams(dp(12), dp(20)));
      row.setOnClickListener(v -> action.run());
      row.setFocusable(true);
    }
  }

  private void note(String value) {
    addText(content, value, 13, SECONDARY, false, 12, 0);
  }

  private void home() {
    heading("Beállítások", "A billentyűzeted, a saját ritmusodban");
    LinearLayout preview = new LinearLayout(this);
    preview.setGravity(Gravity.CENTER_VERTICAL);
    preview.setPadding(dp(16), dp(16), dp(16), dp(16));
    preview.setBackground(press(RAISED, 12));
    content.addView(preview);
    Icon mark = new Icon("mark");
    preview.addView(mark, new LinearLayout.LayoutParams(dp(56), dp(56)));
    LinearLayout copy = column();
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
    cp.setMargins(dp(16), 0, dp(12), 0);
    preview.addView(copy, cp);
    addText(copy, "Billentyűzet kipróbálása", 16, INK, true, 0, 6);
    addText(
        copy,
        (settings.text("language", "hu").equals("hu") ? "Magyar" : "English")
            + " · "
            + (settings.qwerty() ? "QWERTY" : "QWERTZ"),
        13,
        SECONDARY,
        false,
        0,
        0);
    preview.addView(new Icon("arrow"), new LinearLayout.LayoutParams(dp(20), dp(20)));
    preview.setContentDescription("Billentyűzet kipróbálása");
    preview.setFocusable(true);
    preview.setOnClickListener(v -> startActivity(new Intent(this, TypingActivity.class)));
    note(
        selected()
            ? "Az iOS Keyboard for Android az aktív rendszerbillentyűzet"
            : "Kapcsold be és válaszd ki az iOS Keyboard for Android billentyűzetet");
    section("Használat");
    row(
        "Billentyűzet bekapcsolása",
        selected() ? "Aktív" : "Beállítás",
        "",
        () -> open("setup"),
        false);
    section("Gépelés");
    row(
        "Nyelvek és kiosztás",
        settings.text("language", "hu").equals("hu") ? "Magyar" : "English",
        "",
        () -> open("language"),
        false);
    row(
        "Javítás és javaslatok",
        "Offline",
        "Javaslatsáv, szójavítás és beillesztés",
        () -> open("correction"),
        false);
    row(
        "Gépelési kényelem",
        "",
        "Nagybetű, dupla szóköz és előnézet",
        () -> open("typing"),
        false);
    row(
        "Diktálás",
        !settings.enabled("dictation", true) ? "Ki" : microphone() ? "Google" : "Engedély kell",
        "Beszédfelismerés a mikrofongombbal",
        () -> open("dictation"),
        false);
    row(
        "Időzítések",
        settings.number("long_press", 360) + " ms",
        "Hosszú nyomás és ismétlés",
        () -> open("timing"),
        false);
    row(
        "Megjelenés és visszajelzés",
        "",
        "Magasság, téma, rezgés és hang",
        () -> open("appearance"),
        false);
    row("Emoji", "", "Keresés, legutóbbiak és bőrszín", () -> open("emoji"), false);
    section("Alkalmazás");
    row(
        "Fejlesztés alatt",
        "",
        "Tanulás és további funkciók",
        () -> open("pending"),
        false);
    row("Karbantartás", "", "Előzmények és alapbeállítások", () -> open("maintenance"), false);
    note("A változtatások automatikusan mentődnek");
  }

  private void language() {
    heading("Nyelvek és kiosztás", "A nyelvek külön megjegyzik a kiválasztott kiosztást");
    section("Nyelv");
    choice(
        "Aktív nyelv",
        "language",
        new String[] {"Magyar", "English"},
        new String[] {"hu", "en"},
        "hu",
        true);
    String lang = settings.text("language", "hu");
    section(lang.equals("hu") ? "Magyar kiosztás" : "English layout");
    choice(
        "Kiosztás",
        "layout_" + lang,
        new String[] {"QWERTZ", "QWERTY"},
        new String[] {"QWERTZ", "QWERTY"},
        lang.equals("hu") ? "QWERTZ" : "QWERTY",
        false);
    note("A nyelvhez tartozó offline szótár automatikusan vált.");
  }

  private void correction() {
    heading("Javítás és javaslatok", "Helyben futó magyar és angol motor");
    section("Javaslatsáv");
    toggle("Javaslatok a billentyűk felett", "suggestions", true);
    toggle("Másolt szöveg felajánlása", "clipboard_suggest", false);
    note(
        "A sáv akkor jelenik meg, ha a mezőben már van szöveg: bal oldalon a beírt szó, középen"
            + " a javítás vagy a legjobb találat, jobbra egy további szó. Koppintásra a szó szóközzel"
            + " együtt bekerül."
            + " Bekapcsolt felajánlásnál a frissen másolt szöveg 90 másodpercig a sávból is"
            + " beilleszthető; a billentyűzet nem menti el.");
    section("Automatikus javítás");
    toggle("Óvatos szójavítás", "autocorrect", true);
    note(
        "Szóköznél vagy írásjelnél javít; a sávban kiemelt szó kerül be. Az első törlés"
            + " visszaállítja az eredeti szót. Jelszó-, e-mail- és webcímmezőben, illetve privát"
            + " bevitelnél kikapcsol. Nem tanul és nem ment beírt szöveget.");
  }

  private void typing() {
    heading("Gépelési kényelem", "Amit a telefonos billentyűzetek alapból tudnak");
    section("Szöveg");
    toggle("Automatikus nagybetű", "auto_caps", true);
    toggle("Dupla szóköz: pont", "double_space", true);
    section("Billentyűk");
    toggle("Billentyű-előnézet", "key_preview", true);
    note(
        "Mondat elején a Shift magától bekapcsol, ha a szövegmező ezt kéri. Két gyors szóköz pontot"
            + " és szóközt ír. Hosszan nyomva tartott törlésnél néhány betű után egész szavak"
            + " törlődnek. A 123-as oldalon a szóköz visszavált a betűkre.");
  }

  private boolean microphone() {
    return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        == android.content.pm.PackageManager.PERMISSION_GRANTED;
  }

  private void dictation() {
    heading("Diktálás", "A mikrofongombbal magyarul vagy angolul diktálhatsz");
    section("Beszédfelismerés");
    toggle("Diktálás a mikrofongombbal", "dictation", true);
    row(
        "Mikrofon-engedély",
        microphone() ? "Megadva" : "Megadás",
        "",
        microphone() ? null : () -> requestPermissions(
            new String[] {android.Manifest.permission.RECORD_AUDIO}, 31),
        false);
    row(
        "Felismerő",
        Dictation.available(this) ? "Google" : "Nincs telepítve",
        "Google beszédszolgáltatás",
        null,
        false);
    note(
        "Koppints a mikrofonra, és beszélj; a felismert szöveg rögtön a mezőbe kerül. Újabb"
            + " koppintásra vagy rövid csend után leáll. A hangot a Google beszédfelismerője"
            + " dolgozza fel, ehhez az a Google-nek kerül elküldésre. A billentyűzet nem ment"
            + " hangot és szöveget. Jelszómezőben a diktálás nem indul el.");
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    build();
  }

  private void timings() {
    heading("Időzítések", "Állítsd be, hogyan reagáljon a billentyűzet a nyomva tartásra");
    section("Nyomva tartás");
    timing(
        "Ékezetválasztó",
        "long_press",
        360,
        150,
        1200,
        "ms",
        "Ennyi ideig tartsd nyomva a betűt az ékezetekhez");
    timing(
        "Törlés indulása",
        "delete_delay",
        380,
        150,
        1500,
        "ms",
        "Ennyi idő után kezdődik az ismételt törlés");
    timing(
        "Törlés ismétlése", "delete_repeat", 65, 30, 300, "ms", "Két ismételt törlés közötti idő");
    section("Shift");
    timing(
        "Dupla Shift",
        "shift_double",
        330,
        150,
        800,
        "ms",
        "Két koppintás megengedett időköze a Caps Lockhoz");
    note("A normál billentyűleütés azonnali");
  }

  private void appearance() {
    heading("Megjelenés", "Méret és visszajelzés gépelés közben");
    section("Billentyűzet");
    timing("Magasság", "height", 100, 75, 115, "%", "Az alapmagassághoz viszonyítva");
    choice(
        "Téma",
        "theme",
        new String[] {"Világos", "Sötét", "Rendszer szerint"},
        new String[] {"light", "dark", "system"},
        "system",
        false);
    section("Visszajelzés");
    toggle("Rezgés", "haptic", true);
    toggle("Billentyűhang", "sound", false);
    note("A rezgés és a hang a telefon rendszerbeállításait is követi");
  }

  private void emoji() {
    heading("Emoji", "A telefon saját emoji-megjelenítésével");
    section("Panel");
    toggle("Emoji gomb", "emoji", true);
    toggle("Vissza a betűkhöz beszúrás után", "emoji_return", false);
    choice(
        "Bőrszín",
        "skin",
        new String[] {"Alap", "Világos", "Középvilágos", "Közepes", "Középsötét", "Sötét"},
        new String[] {"", "🏻", "🏼", "🏽", "🏾", "🏿"},
        "",
        false);
    section("Keresés");
    row(
        "Emojipanel megnyitása",
        "",
        "Kategóriák és legutóbbiak",
        () ->
            EmojiPanel.show(
                this,
                settings,
                value -> Toast.makeText(this, "Kiválasztva: " + value, Toast.LENGTH_SHORT).show()),
        false);
    note(
        EmojiCatalog.get(this).count()
            + " Unicode "
            + EmojiCatalog.get(this).version
            + " emoji és változat magyar kereséssel. A rajzolatot a kiválasztott betűkészlet"
            + " adja.");
    section("Emoji megjelenítése");
    row(
        "Emoji-betűkészlet",
        new java.io.File(getFilesDir(), "emoji-font.ttf").exists() ? "Saját" : "Rendszer",
        "TTF vagy OTF fájl importálása",
        this::fontOptions,
        false);
  }

  private void pending() {
    heading("Fejlesztés alatt", "Ezek a funkciók ebben a verzióban még nem használhatók");
    section("Bevitel");
    row("Tanulás", "", "Személyes szótár és következő szó ajánlása", null, false);
    section("További funkciók");
    row("Gesztusok", "", "Csúsztatásos gépelés és kurzormozgatás", null, false);
    row("Kiegészítők", "", "Vágólap-előzmények, fekvő mód és további nyelvek", null, false);
  }

  private void maintenance() {
    heading("Karbantartás", "A beállításokat és az emoji-előzményeket helyben tároljuk");
    section("Előzmények");
    row(
        "Legutóbbi emojik törlése",
        "",
        "A panel előzményei kiürülnek",
        () ->
            confirm(
                "Emoji-előzmények törlése?",
                "A legutóbb használt emojik listája kiürül.",
                () -> {
                  settings.prefs.edit().remove("emoji_recent").apply();
                  Toast.makeText(this, "Emoji-előzmények törölve", Toast.LENGTH_SHORT).show();
                }),
        false);
    section("Licencek");
    row("Nyílt forráskódú licencek", "GPL-3.0", "A projekt és a felhasznált összetevők licencei",
        () -> startActivity(new Intent(this, LicensesActivity.class)), false);
    section("Alapbeállítások");
    row(
        "Minden beállítás visszaállítása",
        "",
        "Kiosztások, időzítések és előzmények",
        () ->
            confirm(
                "Minden beállítás visszaállítása?",
                "Az egyéni beállítások és az emoji-előzmények törlődnek.",
                () -> {
                  settings.prefs.edit().clear().apply();
                  build();
                }),
        true);
    note(
        "iOS Keyboard for Android · 0.8.0-preview\nHálózati engedély nélkül; diktáláskor a hangot a"
            + " Google beszédfelismerője dolgozza fel");
  }

  private boolean selected() {
    return new android.content.ComponentName(this, KeyboardService.class).flattenToShortString()
        .equals(
            android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD));
  }

  private void setup() {
    heading("Billentyűzet bekapcsolása", "Egyszeri beállítás a telefon Android rendszerében");
    section("Beállítás");
    row(
        "1. Billentyűzet engedélyezése",
        "",
        "Az Android beviteli beállításaiban",
        () -> startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)),
        false);
    row(
        "2. iOS Keyboard for Android kiválasztása",
        selected() ? "Aktív" : "",
        "",
        () ->
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showInputMethodPicker(),
        false);
    section("Kipróbálás");
    row(
        "Gépelés és szerkesztés",
        "",
        "Valódi szöveg-, kereső- és jelszómező",
        () -> startActivity(new Intent(this, TypingActivity.class)),
        false);
    row(
        "Vizuális próba",
        "",
        "A billentyűzet előnézete",
        () -> startActivity(new Intent(this, MainActivity.class).putExtra("preview", "letters")),
        false);
    note(
        "Diktáláshoz koppints a mikrofonra. A szójavítás a beállításokban kapcsolható. A Váltás gombbal"
            + " bármikor másik billentyűzet választható.");
  }

  private void fontOptions() {
    new AlertDialog.Builder(this)
        .setTitle("Emoji megjelenítése")
        .setItems(
            new String[] {"Betűkészlet importálása (TTF / OTF)", "Rendszer betűkészlet használata"},
            (d, which) -> {
              if (which == 0) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.setType("*/*");
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(pick, 24);
              } else {
                java.io.File file = new java.io.File(getFilesDir(), "emoji-font.ttf");
                if (file.exists()) file.delete();
                settings
                    .prefs
                    .edit()
                    .putLong("emoji_font_changed", System.currentTimeMillis())
                    .apply();
                build();
              }
            })
        .setNegativeButton("Mégse", null)
        .show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 24 || result != RESULT_OK || data == null || data.getData() == null) return;
    java.io.File temp = new java.io.File(getFilesDir(), "emoji-font-import.tmp");
    try (java.io.InputStream in = getContentResolver().openInputStream(data.getData());
        java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
      byte[] buffer = new byte[16384];
      int count, total = 0;
      while ((count = in.read(buffer)) != -1) {
        total += count;
        if (total > 64 * 1024 * 1024)
          throw new java.io.IOException("A fájl legfeljebb 64 MB lehet");
        out.write(buffer, 0, count);
      }
      out.flush();
      new Typeface.Builder(temp).build();
      java.nio.file.Files.move(
          temp.toPath(),
          new java.io.File(getFilesDir(), "emoji-font.ttf").toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      settings.prefs.edit().putLong("emoji_font_changed", System.currentTimeMillis()).apply();
      Toast.makeText(this, "Emoji-betűkészlet importálva", Toast.LENGTH_SHORT).show();
      build();
    } catch (Exception ex) {
      temp.delete();
      Toast.makeText(this, "Nem sikerült betölteni a betűkészletet", Toast.LENGTH_LONG).show();
    }
  }

  private void confirm(String title, String message, Runnable action) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton("Mégse", null)
        .setPositiveButton(
            title.startsWith("Minden") ? "Visszaállítás" : "Törlés", (d, w) -> action.run())
        .show();
  }

  private void choice(
      String title, String key, String[] names, String[] values, String fallback, boolean rebuild) {
    String selected = settings.text(key, fallback);
    int found = 0;
    for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) found = i;
    final int current = found;
    row(
        title,
        names[current],
        "",
        () ->
            new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(
                    names,
                    current,
                    (dialog, which) -> {
                      settings.prefs.edit().putString(key, values[which]).apply();
                      dialog.dismiss();
                      scrollPosition = scroll.getScrollY();
                      build();
                    })
                .setNegativeButton("Mégse", null)
                .show(),
        false);
  }

  private void toggle(String title, String key, boolean fallback) {
    divider();
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(64));
    TextView label = text(title, 15, INK, false);
    label.setPadding(0, dp(16), dp(12), dp(16));
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    Switch control = new Switch(this);
    control.setShowText(false);
    control.setChecked(settings.enabled(key, fallback));
    control.setContentDescription(title);
    control.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
    control.setTrackTintList(
        new ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {TRACK, dark ? 0xff45474d : 0xffc9cbd1}));
    row.addView(control, new LinearLayout.LayoutParams(dp(52), dp(48)));
    group.addView(row);
    row.setOnClickListener(v -> control.setChecked(!control.isChecked()));
    control.setOnCheckedChangeListener(
        (button, value) -> settings.prefs.edit().putBoolean(key, value).apply());
  }

  private void timing(
      String title, String key, int fallback, int min, int max, String unit, String description) {
    divider();
    LinearLayout block = column();
    block.setPadding(0, dp(20), 0, dp(16));
    group.addView(block);
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    block.addView(header);
    TextView name = text(title, 16, INK, true);
    header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    EditText number = new EditText(this);
    number.setTextSize(16);
    number.setTypeface(medium);
    number.setTextColor(INK);
    number.setInputType(InputType.TYPE_CLASS_NUMBER);
    number.setSingleLine();
    number.setSelectAllOnFocus(true);
    number.setGravity(Gravity.CENTER);
    number.setFontFeatureSettings("tnum");
    number.setPadding(dp(8), 0, dp(8), 0);
    number.setBackground(shape(GROUND, 6));
    number.setText(String.valueOf(settings.number(key, fallback)));
    number.setContentDescription(title + " " + unit);
    header.addView(number, new LinearLayout.LayoutParams(dp(64), dp(44)));
    TextView suffix = text(unit, 13, SECONDARY, false);
    suffix.setPadding(dp(8), 0, 0, 0);
    header.addView(suffix, new LinearLayout.LayoutParams(dp(32), -2));
    addText(block, description, 13, SECONDARY, false, 8, 12);
    SeekBar slider = new SeekBar(this);
    slider.setMin(min);
    slider.setMax(max);
    slider.setProgress(settings.number(key, fallback));
    slider.setProgressTintList(ColorStateList.valueOf(INK));
    slider.setThumbTintList(ColorStateList.valueOf(INK));
    slider.setProgressBackgroundTintList(ColorStateList.valueOf(dark ? 0xff3a3c42 : 0xffdcdee3));
    slider.setContentDescription(title);
    block.addView(slider, new LinearLayout.LayoutParams(-1, dp(40)));
    LinearLayout footer = new LinearLayout(this);
    footer.setGravity(Gravity.CENTER_VERTICAL);
    block.addView(footer);
    TextView range = text(min + "–" + max + " " + unit, 12, SECONDARY, false);
    footer.addView(range, new LinearLayout.LayoutParams(0, -2, 1));
    TextView reset = text("Alapérték", 12, SECONDARY, false);
    reset.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
    reset.setMinHeight(dp(44));
    reset.setMinWidth(dp(80));
    reset.setBackground(press(CARD, 6));
    reset.setContentDescription(title + " alapértéke: " + fallback + " " + unit);
    footer.addView(reset);
    reset.setOnClickListener(v -> number.setText(String.valueOf(fallback)));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}

          public void onProgressChanged(SeekBar s, int value, boolean user) {
            if (user) number.setText(String.valueOf(value));
          }
        });
    number.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int b, int c) {}

          public void afterTextChanged(android.text.Editable e) {
            try {
              int value = Integer.parseInt(e.toString());
              if (value < min || value > max) {
                number.setError(min + "–" + max + " " + unit);
                return;
              }
              number.setError(null);
              settings.prefs.edit().putInt(key, value).apply();
              slider.setProgress(value);
            } catch (NumberFormatException ex) {
              number.setError("Adj meg egy számot");
            }
          }
        });
  }

  /** Consistent 1.7-unit outline family; the product mark uses its own keycap geometry. */
  private final class Icon extends View {
    private final String type;
    private final Paint pen = new Paint(3);

    Icon(String type) {
      super(SettingsActivity.this);
      this.type = type;
      setImportantForAccessibility(
          type.equals("back")
              ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
              : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      if (type.equals("back")) setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float size = Math.min(getWidth(), getHeight());
      if (!type.equals("mark")) size = Math.min(size, dp(24));
      c.save();
      c.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
      c.scale(size / 24, size / 24);
      pen.setColor(SECONDARY);
      pen.setStyle(Paint.Style.STROKE);
      pen.setStrokeWidth(1.7f);
      pen.setStrokeCap(Paint.Cap.ROUND);
      pen.setStrokeJoin(Paint.Join.ROUND);
      Path p = new Path();
      if (type.equals("mark")) {
        pen.setColor(0xff191b20);
        pen.setStyle(Paint.Style.FILL);
        c.drawRoundRect(1, 1, 23, 23, 5, 5, pen);
        pen.setColor(Color.WHITE);
        c.drawRoundRect(5, 5, 19, 19, 3, 3, pen);
        pen.setColor(0xff191b20);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(1.4f);
        c.drawOval(9, 10, 15, 16, pen);
        pen.setColor(0xff1757b5);
        c.drawLine(10, 8, 11.3f, 6.7f, pen);
        c.drawLine(13.2f, 8, 14.5f, 6.7f, pen);
      } else {
        if (type.equals("chevron")) {
          p.moveTo(9, 6);
          p.lineTo(15, 12);
          p.lineTo(9, 18);
        } else if (type.equals("back")) {
          p.moveTo(14, 6);
          p.lineTo(8, 12);
          p.lineTo(14, 18);
        } else {
          p.moveTo(5, 12);
          p.lineTo(19, 12);
          p.moveTo(13, 6);
          p.lineTo(19, 12);
          p.lineTo(13, 18);
        }
        c.drawPath(p, pen);
      }
      c.restore();
    }
  }
}
