// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every supported typing language in one place: letter layouts, long-press accents, the offline
 * dictionary (assets/dictionaries/{code}.dict) and the dictation locale. Adding a language means
 * adding an entry here, a dictionary, a subtype in res/xml/input_method.xml and translations.
 */
final class Languages {
  /**
   * @param layouts letter layouts, the first is the default
   * @param extra a tenth key on the middle row (Spanish Ñ), or null
   * @param accents long-press variants per key, "A ÁÀ|E É" (the key's own letter is added)
   */
  record Language(
      String code, String name, String speech, List<String> layouts, String extra, String accents) {
    Locale locale() {
      return Locale.forLanguageTag(code);
    }

    /** Variants for each base letter in popup order, without the base letter itself. */
    Map<String, String> accentMap() {
      Map<String, String> result = new LinkedHashMap<>();
      if (accents.isEmpty()) return result;
      for (String group : accents.split("\\|")) {
        String[] parts = group.trim().split(" ", 2);
        result.put(parts[0], parts[1]);
      }
      return result;
    }
  }

  static final List<Language> ALL =
      List.of(
          new Language(
              "en",
              "English",
              "en-US",
              List.of("QWERTY", "QWERTZ", "AZERTY"),
              null,
              "A ÀÁÂÄÆÃÅĀ|E ÈÉÊËĒĖĘ|I ÎÏÍĪĮÌ|O ÔÖÒÓŒØŌÕ|U ÛÜÙÚŪ|C ÇĆČ|N ÑŃ|S ßŚŠ|Y ŸÝ|Z ŽŹŻ"),
          new Language(
              "hu",
              "Magyar",
              "hu-HU",
              List.of("QWERTZ", "QWERTY"),
              null,
              "A ÁÀÂÄÆÃÅĀ|E ÉÈÊËĘĖĒ|I Í|O ÖŐÓÒÔŒØŌÕ|U ÜŰÚÙÛŪ"),
          new Language(
              "de",
              "Deutsch",
              "de-DE",
              List.of("QWERTZ", "QWERTY"),
              null,
              "A ÄÀÁÂÆÃÅĀ|E ÉÈÊËĖ|I ÎÏÍĪÌ|O ÖÔÒÓŒØŌÕ|U ÜÛÙÚŪ|S ßŚŠ|C ÇĆČ|N ÑŃ|Y Ÿ"),
          new Language(
              "fr",
              "Français",
              "fr-FR",
              List.of("AZERTY", "QWERTY"),
              null,
              "A ÀÂÆÁÄÃÅĀ|E ÉÈÊËĘĖĒ|I ÎÏÌÍĮĪ|O ÔŒÖÒÓØŌÕ|U ÙÛÜÚŪ|C ÇĆČ|Y ŸÝ|N ÑŃ"),
          new Language(
              "es",
              "Español",
              "es-ES",
              List.of("QWERTY"),
              "Ñ",
              "A ÁÀÄÂÃÅĀÆ|E ÉÈËÊĘĖĒ|I ÍÏÌÎĮĪ|O ÓÒÖÔÕØŌŒ|U ÚÜÙÛŪ|N Ń|C ÇĆČ"),
          new Language(
              "it",
              "Italiano",
              "it-IT",
              List.of("QWERTY"),
              null,
              "A ÀÁÂÄÆÃÅĀ|E ÈÉÊËĘĖĒ|I ÌÍÎÏĮĪ|O ÒÓÔÖŒØŌÕ|U ÙÚÛÜŪ"),
          new Language(
              "pt",
              "Português (Brasil)",
              "pt-BR",
              List.of("QWERTY"),
              null,
              "A ÃÁÀÂÄÅÆĀ|E ÉÊÈËĘĖĒ|I ÍÎÌÏĮĪ|O ÕÓÔÒÖŒØŌ|U ÚÜÙÛŪ|C ÇĆČ"),
          new Language(
              "pl",
              "Polski",
              "pl-PL",
              List.of("QWERTY", "QWERTZ"),
              null,
              "A Ą|C Ć|E Ę|L Ł|N Ń|O Ó|S Ś|Z ŻŹ"),
          new Language(
              "nl",
              "Nederlands",
              "nl-NL",
              List.of("QWERTY", "AZERTY"),
              null,
              "A ÁÄÀÂÃÅĀÆ|E ËÉÈÊĘĖĒ|I ÏÍÌÎĮĪ|O ÖÓÒÔÕØŌŒ|U ÜÚÙÛŪ|C ÇĆČ|N ÑŃ"));

  private Languages() {}

  static Language get(String code) {
    for (Language language : ALL) if (language.code.equals(code)) return language;
    return null;
  }

  /** Maps a locale such as pt-BR or en_GB to a supported language, or null. */
  static Language forLocale(String tag) {
    if (tag == null || tag.isEmpty()) return null;
    return get(tag.replace('_', '-').split("-")[0].toLowerCase(Locale.ROOT));
  }

  /** The phone's language when supported, otherwise English. */
  static String fallback() {
    Language system = forLocale(Locale.getDefault().toLanguageTag());
    return system == null ? "en" : system.code;
  }
}
