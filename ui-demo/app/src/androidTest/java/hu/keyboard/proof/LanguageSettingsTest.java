// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;

import android.app.LocaleManager;
import android.content.*;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.view.inputmethod.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LanguageSettingsTest {
  private final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();

  @Test
  public void everyInterfaceLocaleLoadsItsOwnResourcesAndFormatsArguments() {
    String[] titles = {"Settings", "Beállítások", "Einstellungen", "Réglages", "Ajustes",
        "Impostazioni", "Configurações", "Ustawienia", "Instellingen"};
    for (int i = 0; i < Languages.ALL.size(); i++) {
      Configuration config = new Configuration(target.getResources().getConfiguration());
      config.setLocales(LocaleList.forLanguageTags(Languages.ALL.get(i).speech()));
      Context localized = target.createConfigurationContext(config);
      assertEquals(Languages.ALL.get(i).code(), titles[i], localized.getString(R.string.settings));
      String count = localized.getString(R.string.emoji_count, 123, "17.0");
      assertTrue(count, count.contains("123") && count.contains("17.0"));
      assertFalse(count, count.contains("%"));
      assertEquals("No stray resource quotes", count, count.replace("\"", ""));
      assertTrue(localized.getString(R.string.privacy_summary).contains("GitHub"));
      assertTrue(localized.getString(R.string.speech_consent_message).contains("Google"));
      if (i != 0) {
        Configuration english = new Configuration(config);
        english.setLocales(LocaleList.forLanguageTags("en-US"));
        assertNotEquals(target.createConfigurationContext(english).getString(R.string.speech_consent_message),
            localized.getString(R.string.speech_consent_message));
      }
    }
  }

  @Test
  public void androidExposesExactlyTheNineTypingSubtypes() {
    String component = new ComponentName(target, KeyboardService.class).flattenToShortString();
    InputMethodInfo keyboard = target.getSystemService(InputMethodManager.class).getInputMethodList()
        .stream().filter(info -> info.getId().equals(component)).findFirst().orElseThrow();
    assertEquals(9, keyboard.getSubtypeCount());
    Set<String> actual = new HashSet<>();
    for (int i = 0; i < keyboard.getSubtypeCount(); i++) {
      InputMethodSubtype subtype = keyboard.getSubtypeAt(i);
      assertTrue(subtype.isAsciiCapable());
      actual.add(subtype.getLanguageTag());
    }
    Set<String> expected = new HashSet<>();
    for (Languages.Language language : Languages.ALL) expected.add(language.speech());
    assertEquals(expected, actual);
    LocaleList interfaces = new android.app.LocaleConfig(target).getSupportedLocales();
    assertNotNull(interfaces);
    assertEquals(9, interfaces.size());
    Set<String> interfaceCodes = new HashSet<>();
    for (int i = 0; i < interfaces.size(); i++) interfaceCodes.add(interfaces.get(i).getLanguage());
    Set<String> typingCodes = new HashSet<>();
    for (Languages.Language language : Languages.ALL) typingCodes.add(language.code());
    assertEquals(typingCodes, interfaceCodes);
  }

  @Test
  public void globeSelectionNormalizesAndRejectsAnEmptyCycleWithoutLosingSettings() {
    String name = "language-test-" + System.nanoTime();
    var prefs = target.getSharedPreferences(name, 0);
    Context isolated = new ContextWrapper(target) {
      @Override public android.content.SharedPreferences getSharedPreferences(String key, int mode) {
        return prefs;
      }
    };
    try {
      KeyboardSettings settings = new KeyboardSettings(isolated);
      prefs.edit().putString("language", "hu").putString("layout_fr", "AZERTY")
          .putString("layout_de", "QWERTZ").commit();
      settings.setLanguages(Arrays.asList("fr", "de", "fr", "unsupported"));
      assertEquals(Arrays.asList("fr", "de"), settings.languages());
      assertEquals("fr", settings.language());
      assertEquals("AZERTY", settings.layout());
      prefs.edit().putString("language", "de").commit();
      assertEquals("QWERTZ", settings.layout());
      settings.setLanguage("pl");
      assertEquals("pl", settings.language());
      assertEquals(Arrays.asList("fr", "de", "pl"), settings.languages());
      settings.setLanguage("de");
      assertEquals("QWERTZ", settings.layout());
      assertEquals(Arrays.asList("fr", "de", "pl"), settings.languages());
      try {
        settings.setLanguages(List.of("unsupported"));
        fail("An empty cycle was accepted");
      } catch (IllegalArgumentException expected) {
        assertEquals(Arrays.asList("fr", "de", "pl"), settings.languages());
        assertEquals("de", settings.language());
      }
    } finally { target.deleteSharedPreferences(name); }
  }

  @Test
  public void everyLanguageSelectsItsOwnDictionaryLayoutAndEmojiKeywords() {
    String name = "language-assets-test-" + System.nanoTime();
    var prefs = target.getSharedPreferences(name, 0);
    Context isolated = new ContextWrapper(target) {
      @Override public android.content.SharedPreferences getSharedPreferences(String key, int mode) {
        return prefs;
      }
    };
    String[] queries = {"smile", "mosoly", "lächeln", "sourire", "sonrisa", "sorriso", "sorriso", "uśmiech", "glimlach"};
    try {
      KeyboardSettings settings = new KeyboardSettings(isolated);
      for (int i = 0; i < Languages.ALL.size(); i++) {
        Languages.Language language = Languages.ALL.get(i);
        prefs.edit().putString("language", language.code()).commit();
        assertEquals(language.layouts().get(0), settings.layout());
        try (var dictionary = target.getAssets().open("dictionaries/" + language.code() + ".dict")) {
          assertTrue(dictionary.read() >= 0);
        } catch (java.io.IOException e) { throw new AssertionError(language.code(), e); }
        assertFalse(language.code() + ": " + queries[i],
            EmojiCatalog.get(target).filter(settings, 1, queries[i]).isEmpty());
      }
    } finally { target.deleteSharedPreferences(name); }
  }
}
