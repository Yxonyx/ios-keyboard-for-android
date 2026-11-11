// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.proof.debug;

import android.content.*;
import android.os.*;
import hu.keyboard.engine.LatinImeEngine;
import java.io.*;
import java.util.*;
import org.json.*;

/** Fixed synthetic workload only. Excluded from release builds; no editor or microphone access. */
public final class EngineBenchmarkReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    PendingResult pending = goAsync();
    new Thread(
            () -> {
              try {
                measure(context);
                pending.setResultCode(1);
              } catch (Exception failure) {
                android.util.Log.e("KeyboardBenchmark", failure.getClass().getSimpleName());
                pending.setResultCode(-1);
              } finally {
                pending.finish();
              }
            },
            "keyboard-benchmark")
        .start();
  }

  private void measure(Context context) throws Exception {
    File dictionary = new File(context.getCacheDir(), "benchmark-hu.dict");
    try (var in = context.getAssets().open("dictionaries/hu.dict");
        var out = new FileOutputStream(dictionary)) {
      in.transferTo(out);
    }
    JSONObject geometry;
    try (var in = context.getAssets().open("geometry.json")) {
      geometry =
          new JSONObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }
    JSONArray keys = geometry.getJSONObject("states").getJSONArray("letters");
    List<Integer> codes = new ArrayList<>(), rects = new ArrayList<>();
    Map<Integer, int[]> centers = new HashMap<>();
    for (int i = 0; i < keys.length(); i++) {
      JSONObject key = keys.getJSONObject(i);
      String label = key.getString("label");
      if (label.equals("space")) label = " ";
      if (label.length() != 1) continue;
      int cp = Character.toLowerCase(label.charAt(0));
      JSONArray box = key.getJSONArray("rect");
      int x = (int) Math.round(box.getDouble(0)),
          y = (int) Math.round(box.getDouble(1)) - 1650,
          w = (int) Math.round(box.getDouble(2)) - x,
          h = (int) Math.round(box.getDouble(3)) - 1650 - y;
      codes.add(cp);
      Collections.addAll(rects, x, y, w, h);
      centers.put(cp, new int[] {x + w / 2, y + h / 2});
    }
    long heap = Debug.getNativeHeapAllocatedSize(), t = System.nanoTime();
    try (var engine = new LatinImeEngine(dictionary, "hu")) {
      engine.setLayout(
          1179,
          906,
          codes.stream().mapToInt(Integer::intValue).toArray(),
          rects.stream().mapToInt(Integer::intValue).toArray());
      double load = (System.nanoTime() - t) / 1e6;
      String[] words = {"hogy", "hogz", "szia", "szai", "hello"};
      for (String word : words) query(engine, word, centers);
      double[] times = new double[100];
      for (int i = 0; i < times.length; i++) {
        long start = System.nanoTime();
        query(engine, words[i % words.length], centers);
        times[i] = (System.nanoTime() - start) / 1e6;
      }
      Arrays.sort(times);
      JSONObject result =
          new JSONObject()
              .put(
                  "workload",
                  "100 warm queries; five fixed synthetic words; editor/UI time excluded")
              .put("device", Build.MODEL)
              .put("sdk", Build.VERSION.SDK_INT)
              .put("ndk", "27.2.12479018")
              .put("native_optimization", "APP_OPTIM=release (O2)")
              .put("full_edit_distance", false)
              .put("dictionary_open_and_layout_ms", load)
              .put("warm_query_p50_ms", times[49])
              .put("warm_query_p95_ms", times[94])
              .put("warm_query_max_ms", times[99])
              .put("native_heap_delta_bytes", Debug.getNativeHeapAllocatedSize() - heap)
              .put("diagnostic_process_pss_kib", Debug.getPss());
      try (var out =
          new FileOutputStream(new File(context.getFilesDir(), "engine-benchmark.json"))) {
        out.write(result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }
  }

  private List<LatinImeEngine.Candidate> query(
      LatinImeEngine engine, String word, Map<Integer, int[]> centers) {
    int[] cp = word.codePoints().toArray(), x = new int[cp.length], y = new int[cp.length];
    for (int i = 0; i < cp.length; i++) {
      int[] point = centers.get(cp[i]);
      x[i] = point[0];
      y[i] = point[1];
    }
    return engine.suggest(word, x, y, "");
  }
}
