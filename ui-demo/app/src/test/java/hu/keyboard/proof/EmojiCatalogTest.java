// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.proof;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public final class EmojiCatalogTest {
  private static final EmojiCatalog.Entry CAT =
      new EmojiCatalog.Entry(
          "🐱",
          "cat face",
          Map.of("hu", "macskaarc | cica", "en", "kitten | pet", "de", "katzengesicht", "fr", "chat"),
          2);

  private static boolean matches(String query, String language) {
    return CAT.matches(EmojiCatalog.normalize(query), language);
  }

  @Test
  public void matchesActiveLanguageEnglishAndName() {
    assertTrue(matches("Katzen", "de"));
    assertTrue(matches("kitten", "de"));
    assertTrue(matches("cat face", "de"));
    assertFalse(matches("cica", "de"));
    assertTrue(matches("cica", "hu"));
    assertFalse(matches("katzen", "hu"));
  }

  @Test
  public void ignoresAccentsAndRegion() {
    assertTrue(matches("MACSKAÁRC", "hu"));
    assertTrue(matches("chat", "fr-FR"));
    assertTrue(matches("chat", "fr_CA"));
  }

  @Test
  public void missingLanguageFallsBackToEnglish() {
    assertTrue(matches("pet", "pl"));
    assertFalse(matches("cica", "pl"));
    assertEquals("kitten | pet | cat face", CAT.keywords("en"));
  }

  @Test
  public void legacyKeywordsStayHungarian() {
    assertEquals("macskaarc | cica | kitten | pet | cat face", CAT.keywords);
    assertEquals(2, CAT.group);
  }
}
