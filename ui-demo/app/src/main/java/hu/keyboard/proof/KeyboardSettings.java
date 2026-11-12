// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Shared persistent configuration for settings and the keyboard preview. */
public final class KeyboardSettings {
  public final SharedPreferences prefs;
  private final Context context;

  public KeyboardSettings(Context context) {
    this.context = context;
    prefs = context.getSharedPreferences("keyboard_settings_v1", Context.MODE_PRIVATE);
  }

  public int number(String key, int fallback) {
    return prefs.getInt(key, fallback);
  }

  public boolean enabled(String key, boolean fallback) {
    return prefs.getBoolean(key, fallback);
  }

  public String text(String key, String fallback) {
    return prefs.getString(key, fallback);
  }

  /** The active typing language; the phone's language on first use when supported. */
  public String language() {
    String code = text("language", null);
    return code != null && Languages.get(code) != null ? code : fallbackLanguage();
  }

  private String fallbackLanguage() {
    android.os.LocaleList locales = context.getSystemService(android.app.LocaleManager.class).getSystemLocales();
    Languages.Language language = locales.isEmpty() ? null : Languages.forLocale(locales.get(0).toLanguageTag());
    return language == null ? "en" : language.code();
  }

  Languages.Language current() {
    return Languages.get(language());
  }

  /** Languages the globe key cycles through, in settings order; always includes the active one. */
  public List<String> languages() {
    List<String> result = new ArrayList<>();
    for (String code : text("languages", "").split(","))
      if (Languages.get(code) != null && !result.contains(code)) result.add(code);
    if (!result.contains(language())) result.add(0, language());
    return result;
  }

  /** Persist a nonempty, deduplicated globe cycle and keep its active language valid. */
  public void setLanguages(List<String> codes) {
    List<String> valid = new ArrayList<>();
    for (String code : codes)
      if (Languages.get(code) != null && !valid.contains(code)) valid.add(code);
    if (valid.isEmpty()) throw new IllegalArgumentException("Select at least one supported language");
    SharedPreferences.Editor edit = prefs.edit().putString("languages", String.join(",", valid));
    if (!valid.contains(language())) edit.putString("language", valid.get(0));
    edit.apply();
  }

  /** QWERTZ, QWERTY or AZERTY for the active language. */
  public String layout() {
    Languages.Language language = current();
    String chosen = text("layout_" + language.code(), language.layouts().get(0));
    return language.layouts().contains(chosen) ? chosen : language.layouts().get(0);
  }

  public boolean qwerty() {
    return layout().equals("QWERTY");
  }
}
