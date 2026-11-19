// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.proof;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import hu.keyboard.engine.LatinImeEngine;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Owns the native engine on a single background worker; never reads an InputConnection. */
final class CorrectionManager implements AutoCloseable {
  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicLong latest = new AtomicLong();
  private LatinImeEngine engine;
  private String loadedLanguage = "", loadedLayout = "";
  private volatile boolean closed;

  CorrectionManager(Context context) {
    this.context = context.getApplicationContext();
  }

  synchronized void request(
      String language,
      String layoutName,
      int[][] layout,
      String word,
      String previous,
      int[] x,
      int[] y,
      Consumer<CompositionSession.Result> callback) {
    long token = latest.incrementAndGet();
    if (closed) return;
    worker.execute(
        () -> {
          if (closed || token != latest.get()) return;
          CompositionSession.Result result = new CompositionSession.Result(false, List.of());
          try {
            if (engine == null || !loadedLanguage.equals(language)) {
              if (engine != null) {
                engine.close();
                engine = null;
              }
              engine = new LatinImeEngine(dictionary(language), language);
              loadedLanguage = language;
              loadedLayout = "";
            }
            String geometry = language + layoutName;
            if (!loadedLayout.equals(geometry)) {
              engine.setLayout(1179, 906, layout[0], layout[1]);
              loadedLayout = geometry;
            }
            var candidates =
                engine.suggest(
                    word.toLowerCase(Locale.ROOT), x, y, previous.toLowerCase(Locale.ROOT));
            List<CompositionSession.Candidate> converted = new ArrayList<>();
            for (var c : candidates) {
              String text = lowerVariant(engine, c.text(), language);
              converted.add(
                  new CompositionSession.Candidate(text, c.confidence(), engine.contains(text)));
            }
            result =
                new CompositionSession.Result(
                    engine.contains(word.toLowerCase(Locale.ROOT)), List.copyOf(converted));
          } catch (Exception | LinkageError failure) {
            android.util.Log.w(
                "KeyboardCorrection", "Engine unavailable: " + failure.getClass().getSimpleName());
            // Fail open to literal typing. Never log editor text or rewrite it after failure.
            if (engine != null) {
              engine.close();
              engine = null;
            }
          }
          final var response = result;
          main.post(
              () -> {
                if (!closed && token == latest.get()) callback.accept(response);
              });
        });
  }

  /**
   * The dictionary often holds a sentence-start "Köszönöm" next to "köszönöm", and the capitalised
   * one can rank first. Offer the lowercase form when it is a word too; the session capitalises it
   * again where the typed word is capitalised. Names that exist only capitalised stay as they are.
   */
  static String lowerVariant(LatinImeEngine engine, String text, String language) {
    int first = text.isEmpty() ? 0 : text.codePointAt(0);
    if (text.codePointCount(0, text.length()) < 3 || !Character.isUpperCase(first)) return text;
    String rest = text.substring(Character.charCount(first));
    if (!rest.equals(rest.toLowerCase(Locale.ROOT))) return text;   // iPhone, McDonald
    if (rest.startsWith("'") || rest.startsWith("\u2019")) return text;   // I'm, I'll
    String lower = new String(Character.toChars(first)).toLowerCase(Locale.forLanguageTag(language)) + rest;
    return engine.contains(lower) ? lower : text;
  }

  private File dictionary(String language) throws Exception {
    if (Languages.get(language) == null)
      throw new IllegalArgumentException("Unsupported dictionary");
    JSONObject manifest;
    try (var in = context.getAssets().open("dictionaries/SOURCE.json")) {
      manifest =
          new JSONObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }
    String expected =
        manifest.getJSONObject("files").getJSONObject(language + ".dict").getString("sha256");
    File folder = new File(context.getFilesDir(), "dictionaries");
    if (!folder.isDirectory() && !folder.mkdirs())
      throw new IOException("Cannot create dictionary directory");
    File destination = new File(folder, language + "-" + expected + ".dict");
    if (destination.isFile()) return destination;
    File temporary = File.createTempFile(language + "-", ".tmp", folder);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var in = context.getAssets().open("dictionaries/" + language + ".dict");
          var out = new FileOutputStream(temporary)) {
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) != -1) {
          digest.update(buffer, 0, n);
          out.write(buffer, 0, n);
        }
      }
      StringBuilder actual = new StringBuilder();
      for (byte b : digest.digest()) actual.append(String.format(Locale.ROOT, "%02x", b & 255));
      if (!actual.toString().equals(expected))
        throw new IOException("Dictionary integrity mismatch");
      Files.move(
          temporary.toPath(),
          destination.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
      return destination;
    } finally {
      temporary.delete();
    }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    latest.incrementAndGet();
    worker.execute(
        () -> {
          if (engine != null) {
            engine.close();
            engine = null;
          }
        });
    worker.shutdown();
  }
}
