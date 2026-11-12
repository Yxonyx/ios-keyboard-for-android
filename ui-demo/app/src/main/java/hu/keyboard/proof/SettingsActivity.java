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
      back.setContentDescription(getString(R.string.back_settings));
      back.setBackground(press(GROUND, 8));
      toolbar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
      back.setOnClickListener(v -> navigateBack());
    }
    TextView brand = text(screen.equals("home") ? "iOS Keyboard for Android" : getString(R.string.settings), 16, INK, true);
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
      row.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
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
    heading(getString(R.string.settings), getString(R.string.settings_intro));
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
    addText(copy, getString(R.string.try_keyboard), 16, INK, true, 0, 6);
    addText(
        copy,
        settings.current().name() + " · " + settings.layout(),
        13,
        SECONDARY,
        false,
        0,
        0);
    preview.addView(new Icon("arrow"), new LinearLayout.LayoutParams(dp(20), dp(20)));
    preview.setContentDescription(getString(R.string.try_keyboard));
    preview.setFocusable(true);
    preview.setOnClickListener(v -> startActivity(new Intent(this, TypingActivity.class)));
    note(
        selected()
            ? getString(R.string.keyboard_active)
            : getString(R.string.keyboard_activate));
    section(getString(R.string.usage));
    row(
        getString(R.string.enable_keyboard),
        selected() ? getString(R.string.active) : getString(R.string.setup),
        "",
        () -> open("setup"),
        false);
    section(getString(R.string.typing));
    row(
        getString(R.string.languages_layout),
        settings.current().name(),
        "",
        () -> open("language"),
        false);
    row(
        getString(R.string.correction),
        getString(R.string.offline),
        getString(R.string.correction_summary),
        () -> open("correction"),
        false);
    row(
        getString(R.string.typing_comfort),
        "",
        getString(R.string.typing_summary),
        () -> open("typing"),
        false);
    row(
        getString(R.string.dictation),
        !settings.enabled("dictation", true) ? getString(R.string.off) : microphone() ? "Google" : getString(R.string.permission_needed),
        getString(R.string.dictation_summary),
        () -> open("dictation"),
        false);
    row(
        getString(R.string.timings),
        settings.number("long_press", 360) + " ms",
        getString(R.string.timing_summary),
        () -> open("timing"),
        false);
    row(
        getString(R.string.appearance_feedback),
        "",
        getString(R.string.appearance_summary),
        () -> open("appearance"),
        false);
    row("Emoji", "", getString(R.string.emoji_summary), () -> open("emoji"), false);
    section(getString(R.string.application));
    row(getString(R.string.privacy_title), "", getString(R.string.privacy_short),
        () -> startActivity(new Intent(this, PrivacyActivity.class)), false);
    row(
        getString(R.string.development),
        "",
        getString(R.string.development_summary),
        () -> open("pending"),
        false);
    row(getString(R.string.maintenance), "", getString(R.string.maintenance_summary), () -> open("maintenance"), false);
    note(getString(R.string.autosaved));
  }

  private void language() {
    heading(getString(R.string.languages_layout), getString(R.string.language_intro));
    section(getString(R.string.interface_language));
    interfaceLanguage();
    section(getString(R.string.language));
    choice(getString(R.string.active_language), "language", languageNames(), languageCodes(),
        settings.language(), true);
    row(getString(R.string.enabled_languages), enabledLanguageNames(), "", this::enabledLanguages, false);
    note(getString(R.string.globe_note));
    section(settings.current().name());
    String[] layouts = settings.current().layouts().toArray(new String[0]);
    choice(getString(R.string.layout), "layout_" + settings.language(), layouts, layouts,
        settings.layout(), false);
    note(getString(R.string.dictionary_switch));
  }

  private String[] languageNames() {
    return Languages.ALL.stream().map(Languages.Language::name).toArray(String[]::new);
  }

  private String[] languageCodes() {
    return Languages.ALL.stream().map(Languages.Language::code).toArray(String[]::new);
  }

  private String enabledLanguageNames() {
    return String.join(", ", settings.languages().stream().map(code -> Languages.get(code).name()).collect(java.util.stream.Collectors.toList()));
  }

  private void enabledLanguages() {
    boolean[] checked = new boolean[Languages.ALL.size()];
    for (int i = 0; i < checked.length; i++) checked[i] = settings.languages().contains(Languages.ALL.get(i).code());
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle(R.string.enabled_languages)
        .setMultiChoiceItems(languageNames(), checked, (d, which, value) -> checked[which] = value)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.save, null).create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      java.util.List<String> codes = new java.util.ArrayList<>();
      for (int i = 0; i < checked.length; i++) if (checked[i]) codes.add(Languages.ALL.get(i).code());
      if (codes.isEmpty()) {
        Toast.makeText(this, R.string.select_one_language, Toast.LENGTH_SHORT).show();
        return;
      }
      settings.setLanguages(codes);
      dialog.dismiss();
      build();
    }));
    dialog.show();
  }

  private void interfaceLanguage() {
    LocaleManager manager = getSystemService(LocaleManager.class);
    android.os.LocaleList selected = manager.getApplicationLocales();
    java.util.List<String> names = new java.util.ArrayList<>();
    names.add(getString(R.string.follow_system));
    names.addAll(java.util.Arrays.asList(languageNames()));
    int current = 0;
    if (!selected.isEmpty()) {
      Languages.Language language = Languages.forLocale(selected.get(0).toLanguageTag());
      if (language != null) current = Languages.ALL.indexOf(language) + 1;
    }
    final int chosen = current;
    row(getString(R.string.interface_language), names.get(current), "", () ->
        new AlertDialog.Builder(this).setTitle(R.string.interface_language)
            .setSingleChoiceItems(names.toArray(new String[0]), chosen, (dialog, which) -> {
              dialog.dismiss();
              String tag = which == 0 ? "" : Languages.ALL.get(which - 1).speech();
              manager.setApplicationLocales(android.os.LocaleList.forLanguageTags(tag));
            }).setNegativeButton(R.string.cancel, null).show(), false);
  }

  private void correction() {
    heading(getString(R.string.correction), getString(R.string.correction_intro));
    section(getString(R.string.suggestion_strip));
    toggle(getString(R.string.show_suggestions), "suggestions", true);
    toggle(getString(R.string.offer_clipboard), "clipboard_suggest", false);
    note(
        getString(R.string.suggestions_note));
    section(getString(R.string.autocorrection));
    toggle(getString(R.string.conservative_correction), "autocorrect", true);
    note(
        getString(R.string.correction_note));
  }

  private void typing() {
    heading(getString(R.string.typing_comfort), getString(R.string.typing_intro));
    section(getString(R.string.text));
    toggle(getString(R.string.auto_caps), "auto_caps", true);
    toggle(getString(R.string.double_space), "double_space", true);
    section(getString(R.string.keys));
    toggle(getString(R.string.key_preview), "key_preview", true);
    note(
        getString(R.string.typing_note));
  }

  private boolean microphone() {
    return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        == android.content.pm.PackageManager.PERMISSION_GRANTED;
  }

  private void dictation() {
    heading(getString(R.string.dictation), getString(R.string.dictation_intro));
    section(getString(R.string.speech_recognition));
    toggle(getString(R.string.microphone_dictation), "dictation", true);
    row(
        getString(R.string.microphone_permission),
        microphone() ? getString(R.string.granted) : getString(R.string.grant),
        "",
        microphone() ? null : () -> requestPermissions(
            new String[] {android.Manifest.permission.RECORD_AUDIO}, 31),
        false);
    row(getString(R.string.speech_approval),
        settings.enabled(MicPermissionActivity.CONSENT_KEY, false) ? getString(R.string.approved) : getString(R.string.unapproved),
        getString(R.string.revoke_approval),
        () -> {
          settings.prefs.edit().remove(MicPermissionActivity.CONSENT_KEY).apply();
          build();
        }, false);
    row(
        getString(R.string.recognizer),
        Dictation.available(this)
            ? (Dictation.service(this) != null ? "Google" : getString(R.string.system_default))
            : getString(R.string.not_installed),
        getString(R.string.external_service),
        null,
        false);
    note(
        getString(R.string.dictation_note));
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    build();
  }

  private void timings() {
    heading(getString(R.string.timings), getString(R.string.timing_intro));
    section(getString(R.string.hold));
    timing(
        getString(R.string.accent_picker),
        "long_press",
        360,
        150,
        1200,
        "ms",
        getString(R.string.accent_delay_note));
    timing(
        getString(R.string.delete_delay),
        "delete_delay",
        380,
        150,
        1500,
        "ms",
        getString(R.string.delete_delay_note));
    timing(
        getString(R.string.delete_repeat), "delete_repeat", 65, 30, 300, "ms", getString(R.string.delete_repeat_note));
    section("Shift");
    timing(
        getString(R.string.double_shift),
        "shift_double",
        330,
        150,
        800,
        "ms",
        getString(R.string.shift_delay_note));
    note(getString(R.string.instant_keys));
  }

  private void appearance() {
    heading(getString(R.string.appearance), getString(R.string.appearance_intro));
    section(getString(R.string.keyboard));
    timing(getString(R.string.height), "height", 100, 75, 115, "%", getString(R.string.height_note));
    choice(
        getString(R.string.theme),
        "theme",
        new String[] {getString(R.string.light), getString(R.string.dark), getString(R.string.follow_system)},
        new String[] {"light", "dark", "system"},
        "system",
        false);
    section(getString(R.string.feedback));
    toggle(getString(R.string.vibration), "haptic", true);
    toggle(getString(R.string.key_sound), "sound", false);
    note(getString(R.string.feedback_note));
  }

  private void emoji() {
    heading("Emoji", getString(R.string.emoji_intro));
    section(getString(R.string.emoji_panel));
    toggle(getString(R.string.emoji_button), "emoji", true);
    toggle(getString(R.string.emoji_return), "emoji_return", false);
    choice(
        getString(R.string.skin_tone),
        "skin",
        new String[] {getString(R.string.skin_default), getString(R.string.light), getString(R.string.medium_light), getString(R.string.medium), getString(R.string.medium_dark), getString(R.string.dark)},
        new String[] {"", "🏻", "🏼", "🏽", "🏾", "🏿"},
        "",
        false);
    section(getString(R.string.search));
    row(
        getString(R.string.open_emoji),
        "",
        getString(R.string.categories_recent),
        () ->
            EmojiPanel.show(
                this,
                settings,
                value -> Toast.makeText(this, getString(R.string.selected_prefix) + value, Toast.LENGTH_SHORT).show()),
        false);
    note(getString(R.string.emoji_count, EmojiCatalog.get(this).count(), EmojiCatalog.get(this).version));
    section(getString(R.string.emoji_display));
    row(
        getString(R.string.emoji_font),
        new java.io.File(getFilesDir(), "emoji-font.ttf").exists() ? getString(R.string.custom) : getString(R.string.system),
        getString(R.string.import_font_note),
        this::fontOptions,
        false);
  }

  private void pending() {
    heading(getString(R.string.development), getString(R.string.pending_intro));
    section(getString(R.string.typing));
    row(getString(R.string.learning), "", getString(R.string.learning_note), null, false);
    section(getString(R.string.more_features));
    row(getString(R.string.gestures), "", getString(R.string.gestures_note), null, false);
    row(getString(R.string.extras), "", getString(R.string.extras_note), null, false);
  }

  private void maintenance() {
    heading(getString(R.string.maintenance), getString(R.string.maintenance_intro));
    section(getString(R.string.history));
    row(
        getString(R.string.clear_recent),
        "",
        getString(R.string.clear_recent_note),
        () ->
            confirm(
                getString(R.string.clear_recent_title),
                getString(R.string.clear_recent_message),
                () -> {
                  settings.prefs.edit().remove("emoji_recent").apply();
                  Toast.makeText(this, getString(R.string.recent_cleared), Toast.LENGTH_SHORT).show();
                }),
        false);
    section(getString(R.string.licenses));
    row(getString(R.string.open_source_licenses), "GPL-3.0", getString(R.string.licenses_note),
        () -> startActivity(new Intent(this, LicensesActivity.class)), false);
    section(getString(R.string.defaults));
    row(
        getString(R.string.reset_all),
        "",
        getString(R.string.reset_all_note),
        () ->
            confirm(
                getString(R.string.reset_all_title),
                getString(R.string.reset_all_message),
                () -> {
                  settings.prefs.edit().clear().apply();
                  getSystemService(LocaleManager.class).setApplicationLocales(android.os.LocaleList.getEmptyLocaleList());
                  build();
                }),
        true);
    note(
        getString(R.string.version_privacy));
  }

  private boolean selected() {
    return new android.content.ComponentName(this, KeyboardService.class).flattenToShortString()
        .equals(
            android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD));
  }

  private void setup() {
    heading(getString(R.string.enable_keyboard), getString(R.string.setup_intro));
    note(getString(R.string.setup_warning));
    row(getString(R.string.privacy_title), "", getString(R.string.privacy_link_note),
        () -> startActivity(new Intent(this, PrivacyActivity.class)), false);
    section(getString(R.string.setup));
    row(
        getString(R.string.setup_step_enable),
        "",
        getString(R.string.setup_step_note),
        () -> startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)),
        false);
    row(
        getString(R.string.setup_step_select),
        selected() ? getString(R.string.active) : "",
        "",
        () ->
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showInputMethodPicker(),
        false);
    section(getString(R.string.try_it));
    row(
        getString(R.string.typing_editing),
        "",
        getString(R.string.typing_fields),
        () -> startActivity(new Intent(this, TypingActivity.class)),
        false);
    row(
        getString(R.string.visual_preview),
        "",
        getString(R.string.keyboard_preview),
        () -> startActivity(new Intent(this, MainActivity.class).putExtra("preview", "letters")),
        false);
    note(
        getString(R.string.setup_note));
  }

  private void fontOptions() {
    new AlertDialog.Builder(this)
        .setTitle(getString(R.string.emoji_display))
        .setItems(
            new String[] {getString(R.string.import_font), getString(R.string.system_font)},
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
        .setNegativeButton(getString(R.string.cancel), null)
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
          throw new java.io.IOException(getString(R.string.font_size_error));
        out.write(buffer, 0, count);
      }
      out.flush();
      new Typeface.Builder(temp).build();
      java.nio.file.Files.move(
          temp.toPath(),
          new java.io.File(getFilesDir(), "emoji-font.ttf").toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      settings.prefs.edit().putLong("emoji_font_changed", System.currentTimeMillis()).apply();
      Toast.makeText(this, getString(R.string.font_imported), Toast.LENGTH_SHORT).show();
      build();
    } catch (Exception ex) {
      temp.delete();
      Toast.makeText(this, getString(R.string.font_failed), Toast.LENGTH_LONG).show();
    }
  }

  private void confirm(String title, String message, Runnable action) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton(getString(R.string.cancel), null)
        .setPositiveButton(
            title.equals(getString(R.string.reset_all_title)) ? getString(R.string.reset) : getString(R.string.delete), (d, w) -> action.run())
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
                .setNegativeButton(getString(R.string.cancel), null)
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
    TextView reset = text(getString(R.string.default_value), 12, SECONDARY, false);
    reset.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
    reset.setMinHeight(dp(44));
    reset.setMinWidth(dp(80));
    reset.setBackground(press(CARD, 6));
    reset.setContentDescription(getString(R.string.default_accessibility, title, fallback, unit));
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
              number.setError(getString(R.string.enter_number));
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
