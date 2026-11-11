// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.engine;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LatinImeEngineTest {
  private LatinImeEngine open(String language) throws Exception {
    var context = InstrumentationRegistry.getInstrumentation().getContext();
    File file = new File(context.getCacheDir(), language + "-test.dict");
    try (var in = context.getAssets().open("dictionaries/" + language + ".dict");
        var out = new FileOutputStream(file)) {
      in.transferTo(out);
    }
    LatinImeEngine engine = new LatinImeEngine(file, language);
    List<Integer> codes = new ArrayList<>(), rects = new ArrayList<>();
    String[] rows = {"qwertzuiop", "asdfghjkl", "yxcvbnm"};
    for (int row = 0; row < rows.length; row++)
      for (int i = 0; i < rows[row].length(); i++) {
        codes.add((int) rows[row].charAt(i));
        Collections.addAll(
            rects, 20 + i * 116 + (row == 1 ? 58 : row == 2 ? 174 : 0), 78 + row * 162, 97, 129);
      }
    codes.add(32);
    Collections.addAll(rects, 309, 558, 560, 129);
    engine.setLayout(
        1179,
        906,
        codes.stream().mapToInt(Integer::intValue).toArray(),
        rects.stream().mapToInt(Integer::intValue).toArray());
    return engine;
  }

  private List<LatinImeEngine.Candidate> query(LatinImeEngine e, String word) {
    String[] rows = {"qwertzuiop", "asdfghjkl", "yxcvbnm"};
    int[] x = new int[word.length()], y = new int[word.length()];
    for (int n = 0; n < word.length(); n++)
      for (int r = 0; r < rows.length; r++) {
        int i = rows[r].indexOf(word.charAt(n));
        if (i >= 0) {
          x[n] = 68 + i * 116 + (r == 1 ? 58 : r == 2 ? 174 : 0);
          y[n] = 142 + r * 162;
        }
      }
    return e.suggest(word, x, y, "");
  }

  @Test
  public void hungarianDictionaryAndDecoder() throws Exception {
    try (var e = open("hu")) {
      assertTrue("Hungarian common word missing", e.contains("hogy"));
      assertTrue("Hungarian accent missing", e.contains("még"));
      var candidates = query(e, "hogy");
      assertTrue(
          "Correct word should be a candidate",
          candidates.stream().anyMatch(c -> c.text().equals("hogy")));
      var typo = query(e, "hogz");
      assertTrue(
          "Adjacent-key typo should suggest hogy",
          typo.stream().anyMatch(c -> c.text().equals("hogy")));
    }
  }

  @Test
  public void englishDictionaryWorks() throws Exception {
    try (var e = open("en")) {
      assertTrue(e.contains("hello"));
      assertFalse(query(e, "hello").isEmpty());
    }
  }

  @Test
  public void allBundledDictionariesRecognizeCommonWords() throws Exception {
    Map<String, String> examples = Map.of(
        "hu", "hogy", "en", "hello", "de", "der", "fr", "de", "es", "de",
        "it", "di", "pt", "de", "pl", "nie", "nl", "de");
    for (var entry : examples.entrySet()) {
      try (var engine = open(entry.getKey())) {
        assertTrue("Common word missing in " + entry.getKey(), engine.contains(entry.getValue()));
      }
    }
  }

  @Test
  public void limitsAndLifecycle() throws Exception {
    var e = open("hu");
    assertTrue(e.suggest("a".repeat(48), new int[48], new int[48], "").isEmpty());
    assertTrue(e.suggest("a", new int[0], new int[0], "").isEmpty());
    e.close();
    e.close();
    try {
      e.contains("hogy");
      fail("Closed engine accepted a request");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void syntheticLatencyAndCandidateEvidence() throws Exception {
    var context = InstrumentationRegistry.getInstrumentation().getContext();
    long heapBefore = android.os.Debug.getNativeHeapAllocatedSize();
    long start = System.nanoTime();
    try (var e = open("hu")) {
      double loadMs = (System.nanoTime() - start) / 1_000_000.0;
      var example = query(e, "hogz");
      org.json.JSONArray candidates = new org.json.JSONArray();
      for (var c : example)
        candidates.put(
            new org.json.JSONObject()
                .put("text", c.text())
                .put("confidence", c.confidence())
                .put("score", c.score())
                .put("known", e.contains(c.text())));
      String[] words = {"hogy", "hogz", "szia", "szai", "hello"};
      for (int i = 0; i < 10; i++) query(e, words[i % words.length]);
      double[] times = new double[100];
      for (int i = 0; i < times.length; i++) {
        long t = System.nanoTime();
        query(e, words[i % words.length]);
        times[i] = (System.nanoTime() - t) / 1_000_000.0;
      }
      Arrays.sort(times);
      var report =
          new org.json.JSONObject()
              .put("device", android.os.Build.MODEL)
              .put("sdk", android.os.Build.VERSION.SDK_INT)
              .put("load_copy_and_layout_ms", loadMs)
              .put("warm_query_p50_ms", times[49])
              .put("warm_query_p95_ms", times[94])
              .put("warm_query_max_ms", times[99])
              .put("samples", 100)
              .put(
                  "native_heap_delta_bytes",
                  android.os.Debug.getNativeHeapAllocatedSize() - heapBefore)
              .put("process_pss_kib", android.os.Debug.getPss())
              .put("synthetic_hogz_candidates", candidates)
              .put("synthetic_hogz_is_known", e.contains("hogz"));
      try (var out =
          new FileOutputStream(new File(context.getFilesDir(), "engine-benchmark.json"))) {
        out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }
  }
}
