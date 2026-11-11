// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.content.Context;
import android.graphics.Typeface;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/**
 * Complete versioned Unicode data with CLDR keywords per language (hu, en, de, fr, es, it, pt, pl,
 * nl), including variants. Search matches the active language, English and the Unicode name.
 */
public final class EmojiCatalog {
  public static final class Entry {
    public final String emoji, name, keywords, key;
    public final int group;
    private final Map<String, String> kw;
    private String searchLanguage, searchText;

    Entry(JSONObject value) {
      this(
          value.optString("emoji"),
          value.optString("name"),
          languages(value.optJSONObject("kw")),
          value.optInt("group"));
    }

    Entry(String emoji, String name, Map<String, String> kw, int group) {
      this.emoji = emoji;
      this.name = name;
      this.kw = kw;
      keywords = keywords("hu");
      this.group = group == 9 ? 6 : group;
      String base = emoji.replaceAll("[\\x{1F3FB}-\\x{1F3FF}]", "");
      key = base.isEmpty() ? emoji : base;
    }

    /** Active language keywords, then English ones, then the Unicode name. */
    public String keywords(String language) {
      language = language.split("[-_]")[0].toLowerCase(Locale.ROOT);
      String own = kw.getOrDefault(language, ""), en = kw.getOrDefault("en", "");
      return (own.isEmpty() || language.equals("en") ? "" : own + " | ")
          + (en.isEmpty() ? "" : en + " | ")
          + name;
    }

    /** {@code query} must already be {@link #normalize normalized}. */
    public boolean matches(String query, String language) {
      if (!language.equals(searchLanguage)) {
        searchText = normalize(emoji + " " + keywords(language));
        searchLanguage = language;
      }
      return searchText.contains(query);
    }

    private static Map<String, String> languages(JSONObject value) {
      Map<String, String> result = new HashMap<>();
      if (value != null)
        for (Iterator<String> it = value.keys(); it.hasNext(); ) {
          String lang = it.next();
          result.put(lang, value.optString(lang));
        }
      return result;
    }
  }

  private static EmojiCatalog instance;
  private final List<Entry> all = new ArrayList<>();
  private final LinkedHashMap<String, List<Entry>> families = new LinkedHashMap<>();
  public final String version;

  private EmojiCatalog(Context context) {
    try {
      JSONObject data =
          new JSONObject(
              new String(
                  context.getAssets().open("emoji/catalog.json").readAllBytes(),
                  StandardCharsets.UTF_8));
      version = data.getString("version");
      JSONArray entries = data.getJSONArray("entries");
      for (int i = 0; i < entries.length(); i++) {
        Entry item = new Entry(entries.getJSONObject(i));
        all.add(item);
        families.computeIfAbsent(item.key, key -> new ArrayList<>()).add(item);
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid emoji catalog", ex);
    }
  }

  public static synchronized EmojiCatalog get(Context context) {
    if (instance == null) instance = new EmojiCatalog(context);
    return instance;
  }

  public int count() {
    return all.size();
  }

  public List<Entry> display(KeyboardSettings settings) {
    List<Entry> result = new ArrayList<>();
    String skin = settings.text("skin", "");
    for (List<Entry> variants : families.values()) {
      Entry selected = variants.get(0);
      if (!skin.isEmpty()) {
        for (Entry item : variants)
          if (item.emoji.contains(skin)
              && item.emoji
                  .codePoints()
                  .filter(cp -> cp >= 0x1f3fb && cp <= 0x1f3ff)
                  .allMatch(cp -> cp == skin.codePointAt(0))) {
            selected = item;
            break;
          }
      }
      result.add(selected);
    }
    return result;
  }

  public List<Entry> variants(Entry item) {
    return families.get(item.key);
  }

  public List<Entry> filter(KeyboardSettings settings, int group, String query) {
    return filter(settings, group, query, "hu");
  }

  public List<Entry> filter(KeyboardSettings settings, int group, String query, String language) {
    String q = normalize(query);
    List<Entry> result = new ArrayList<>();
    if (group == 0 && q.isEmpty()) {
      for (String recent : settings.text("emoji_recent", "").split("\\|"))
        if (!recent.isEmpty()) {
          for (Entry item : all)
            if (item.emoji.equals(recent)) {
              result.add(item);
              break;
            }
        }
      return result;
    }
    for (Entry item : display(settings)) {
      if (q.isEmpty()
          ? item.group == group
          : item.matches(q, language)) result.add(item);
    }
    return result;
  }

  public void remember(KeyboardSettings settings, String emoji) {
    LinkedHashSet<String> recent = new LinkedHashSet<>();
    recent.add(emoji);
    recent.addAll(Arrays.asList(settings.text("emoji_recent", "").split("\\|")));
    recent.remove("");
    List<String> list = new ArrayList<>(recent);
    settings
        .prefs
        .edit()
        .putString("emoji_recent", String.join("|", list.subList(0, Math.min(60, list.size()))))
        .apply();
  }

  public String[][] legacy(KeyboardSettings settings) {
    List<Entry> entries = display(settings);
    String[][] values = new String[entries.size()][];
    for (int i = 0; i < entries.size(); i++) {
      Entry e = entries.get(i);
      values[i] = new String[] {e.emoji, e.keywords(settings.language()), String.valueOf(e.group)};
    }
    return values;
  }

  public static String normalize(String text) {
    return java.text.Normalizer.normalize(
            text.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
  }

  private static Typeface cachedFont;
  private static long fontStamp;

  public static synchronized Typeface font(Context context) {
    File file = new File(context.getFilesDir(), "emoji-font.ttf");
    if (!file.exists()) {
      cachedFont = null;
      fontStamp = 0;
      return Typeface.DEFAULT;
    }
    try {
      if (cachedFont == null || fontStamp != file.lastModified()) {
        cachedFont = new Typeface.Builder(file).setFallback("sans-serif").build();
        fontStamp = file.lastModified();
      }
      return cachedFont;
    } catch (Exception ex) {
      return Typeface.DEFAULT;
    }
  }
}
