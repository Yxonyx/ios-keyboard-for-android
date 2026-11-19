// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.proof;

import static org.junit.Assert.*;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * Real dictionaries and the real correction rules, per typing language: common typos and words
 * typed without their accents are corrected at the space, and ordinary correct words are kept.
 */
@RunWith(AndroidJUnit4.class)
public final class MultilingualCorrectionTest {
  /** typed word -> text after the space; a word mapped to itself must be left alone. */
  private static final Map<String, String[][]> CASES = new LinkedHashMap<>();

  static {
    CASES.put("en", new String[][] {
      {"teh", "the"}, {"dont", "don't"}, {"recieve", "receive"}, {"tomorow", "tomorrow"},
      {"thats", "that's"}, {"im", "I'm"}, {"wierd", "weird"}, {"becuase", "because"},
      {"hello", "hello"}, {"its", "its"}, {"well", "well"}, {"cant", "can't"},
      {"ill", "I'll"}, {"lets", "let's"}, {"hid", "hid"}, {"were", "were"}, {"nut", "nut"}
    });
    CASES.put("hu", new String[][] {
      {"koszonom", "köszönöm"}, {"kesobb", "később"}, {"talalkozunk", "találkozunk"},
      {"hogz", "hogy"}, {"szep", "szép"}, {"holnap", "holnap"},
      // the list ranks "kosz" five times rarer than "kösz"; Backspace restores it
      {"kosz", "kösz"},
      {"mert", "mert"}, {"mar", "már"}, {"udvozlettel", "üdvözlettel"}, {"szivesen", "szívesen"},
      {"jo", "jó"}, {"itt", "itt"}
    });
    CASES.put("de", new String[][] {
      {"fur", "für"}, {"uber", "über"}, {"mussen", "müssen"}, {"konnen", "können"},
      {"schon", "schon"}, {"heute", "heute"}, {"vielleciht", "vielleicht"},
      {"natürlcih", "natürlich"}, {"danke", "danke"}, {"wiedr", "wieder"}
    });
    CASES.put("fr", new String[][] {
      {"tres", "très"}, {"deja", "déjà"}, {"francais", "français"}, {"etre", "être"},
      {"les", "les"}, {"mais", "mais"}, {"la", "la"}, {"des", "des"}, {"bonjuor", "bonjour"}, {"merci", "merci"},
      {"apres", "après"}, {"ou", "ou"}
    });
    CASES.put("es", new String[][] {
      {"manana", "mañana"}, {"tambien", "también"}, {"cancion", "canción"},
      {"esta", "esta"}, {"gracias", "gracias"}, {"despues", "después"}, {"porqeu", "porque"},
      {"hola", "hola"}, {"el", "el"}, {"tu", "tu"}, {"si", "si"}, {"mi", "mi"}, {"se", "se"}
    });
    CASES.put("it", new String[][] {
      {"perche", "perché"}, {"citta", "città"}, {"piu", "più"}, {"gia", "già"},
      {"come", "come"}, {"sono", "sono"}, {"si", "si"}, {"grazie", "grazie"}, {"domani", "domani"}
    });
    CASES.put("pt", new String[][] {
      {"voce", "você"}, {"nao", "não"}, {"entao", "então"}, {"tambem", "também"},
      {"para", "para"}, {"esta", "esta"}, {"obrigado", "obrigado"}, {"amanha", "amanhã"}, {"com", "com"}
    });
    CASES.put("pl", new String[][] {
      // the list has "dziękuje" slightly above "dziękuję"; either beats the bare form
      {"dziekuje", "dziękuje"}, {"prosze", "proszę"}, {"jestes", "jesteś"}, {"wiec", "więc"},
      {"tak", "tak"}, {"jest", "jest"}, {"dzisiaj", "dzisiaj"}, {"moze", "może"}
    });
    CASES.put("nl", new String[][] {
      {"misschein", "misschien"}, {"ideeen", "ideeën"}, {"gewest", "gewest"}, {"vandag", "vandaag"},
      {"hallo", "hallo"}, {"bedankt", "bedankt"}, {"eigenlijk", "eigenlijk"},
      {"alsjeblieft", "alsjeblieft"}, {"morgne", "morgen"}
    });
  }

  private static String[] rows(String language) {
    switch (language) {
      case "hu": case "de": return new String[] {"qwertzuiop", "asdfghjkl", "yxcvbnm"};
      case "fr": return new String[] {"azertyuiop", "qsdfghjklm", "wxcvbn"};
      default: return new String[] {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    }
  }

  private static String describe(CompositionSession.Result r) {
    StringBuilder b = new StringBuilder();
    for (var c : r.candidates().subList(0, Math.min(5, r.candidates().size())))
      b.append(c.text()).append(String.format(java.util.Locale.ROOT, ":%.3f%s ", c.confidence(), c.known() ? "" : "?"));
    return b.toString();
  }

  @Test
  public void typosAndMissingAccentsInEveryLanguage() throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    var target = instrumentation.getTargetContext();
    Context fixture =
        new ContextWrapper(target) {
          final File folder = new File(target.getCacheDir(), "dictionary-ml-" + System.nanoTime());

          @Override
          public Context getApplicationContext() {
            return this;
          }

          @Override
          public File getFilesDir() {
            folder.mkdirs();
            return folder;
          }
        };
    var manager = new CorrectionManager(fixture);
    List<String> failures = new ArrayList<>();
    int checked = 0;
    try {
      for (var entry : CASES.entrySet()) {
        String language = entry.getKey();
        List<Integer> codes = new ArrayList<>(), rectangles = new ArrayList<>();
        Map<Integer, int[]> centers = new HashMap<>();
        String[] rows = rows(language);
        for (int row = 0; row < rows.length; row++)
          for (int i = 0; i < rows[row].length(); i++) {
            int cp = rows[row].charAt(i),
                x = 20 + i * 116 + (rows[row].length() == 10 ? 0 : 58) + (row == 2 ? 116 : 0),
                y = 78 + row * 162;
            codes.add(cp);
            Collections.addAll(rectangles, x, y, 97, 129);
            centers.put(cp, new int[] {x + 48, y + 64});
          }
        int[][] layout = {
          codes.stream().mapToInt(Integer::intValue).toArray(),
          rectangles.stream().mapToInt(Integer::intValue).toArray()
        };
        for (String[] c : entry.getValue()) {
          String typed = c[0], expected = c[1];
          StringBuilder text = new StringBuilder();
          int[] composeStart = {-1};
          CompositionSession.Editor editor =
              new CompositionSession.Editor() {
                public boolean compose(String value) {
                  if (composeStart[0] < 0) composeStart[0] = text.length();
                  text.setLength(composeStart[0]);
                  text.append(value);
                  return true;
                }

                public void commit(String value) {
                  if (composeStart[0] >= 0) text.setLength(composeStart[0]);
                  text.append(value);
                  composeStart[0] = -1;
                }

                public void finish() {
                  composeStart[0] = -1;
                }

                public String before(int n) {
                  return text.substring(Math.max(0, text.length() - n));
                }

                public int cursor() {
                  return text.length();
                }

                public boolean deleteBefore(int n) {
                  text.setLength(text.length() - n);
                  return true;
                }
              };
          BlockingQueue<String> answered = new LinkedBlockingQueue<>();
          CompositionSession.Result[] last = new CompositionSession.Result[1];
          CompositionSession[] session = new CompositionSession[1];
          instrumentation.runOnMainSync(
              () -> {
                session[0] =
                    new CompositionSession(
                        editor,
                        (word, previous, x, y, callback) ->
                            manager.request(
                                language, rows[0], layout, word, previous, x, y,
                                result -> {
                                  last[0] = result;
                                  callback.accept(result);
                                  answered.add(word);
                                }));
                session[0].reset(true);
              });
          for (int i = 0; i < typed.length(); i++) {
            String letter = String.valueOf(typed.charAt(i));
            int[] at = centers.getOrDefault((int) Character.toLowerCase(typed.charAt(i)), new int[] {0, 0});
            instrumentation.runOnMainSync(() -> session[0].type(letter, at[0], at[1]));
          }
          // Only the reply for the whole word counts; earlier ones are discarded as stale.
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
          String got;
          do {
            got = answered.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
          } while (got != null && !got.equals(typed));
          assertNotNull(language + " engine timed out on " + typed, got);
          instrumentation.runOnMainSync(() -> session[0].type(" ", 0, 0));
          String result = text.toString().trim();
          checked++;
          if (last[0] != null) android.util.Log.i("MultilingualCorrection", language + " " + typed + " known=" + last[0].originalKnown() + " " + describe(last[0]));
          if (!result.equals(expected))
            failures.add(language + ": " + typed + " -> " + result + " (want " + expected + ")"
                + (last[0] == null ? "" : " known=" + last[0].originalKnown() + " " + describe(last[0])));
        }
      }
    } finally {
      manager.close();
    }
    android.util.Log.i("MultilingualCorrection", checked + " checked, failures: " + failures);
    assertTrue(failures.size() + " of " + checked + " failed:\n" + String.join("\n", failures),
        failures.isEmpty());
  }
}
