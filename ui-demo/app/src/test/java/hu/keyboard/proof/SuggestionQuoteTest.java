// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SuggestionQuoteTest {
  private static String quote(String code, String word) {
    for (Languages.Language language : Languages.ALL)
      if (language.code().equals(code)) return language.quote(word);
    throw new AssertionError(code);
  }

  @Test public void englishUsesCurlyDoubleQuotes() {
    assertEquals("“iPhone”", quote("en", "iPhone"));
    assertEquals("“olá”", quote("pt", "olá"));
    assertEquals("“hallo”", quote("nl", "hallo"));
  }

  @Test public void centralEuropeanLanguagesOpenLow() {
    assertEquals("„alma”", quote("hu", "alma"));
    assertEquals("„tak”", quote("pl", "tak"));
    assertEquals("„Haus“", quote("de", "Haus"));
  }

  @Test public void romanceLanguagesUseGuillemets() {
    assertEquals("« café »", quote("fr", "café"));
    assertEquals("«hola»", quote("es", "hola"));
    assertEquals("«ciao»", quote("it", "ciao"));
  }

  @Test public void everyLanguageQuotes() {
    for (Languages.Language language : Languages.ALL)
      assertEquals(language.code(), true, language.quote("x").length() > 1);
  }
}
