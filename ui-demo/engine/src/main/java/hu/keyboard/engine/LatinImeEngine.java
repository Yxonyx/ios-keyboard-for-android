// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only native dictionary. Own on one background executor; no editor text is persisted. */
public final class LatinImeEngine implements AutoCloseable {
  static {
    System.loadLibrary("keyboard_latinime");
  }

  private long handle;

  public record Candidate(String text, int score, float confidence) {}

  public LatinImeEngine(File dictionary, String locale) {
    if (!dictionary.isFile() || dictionary.length() > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Invalid dictionary");
    handle = openNative(dictionary.getAbsolutePath(), locale, dictionary.length());
    if (handle == 0) throw new IllegalStateException("Unable to open dictionary");
  }

  public synchronized void setLayout(int width, int height, int[] codes, int[] rectangles) {
    requireOpen();
    if (width <= 0
        || height <= 0
        || codes.length == 0
        || codes.length > 64
        || rectangles.length != codes.length * 4)
      throw new IllegalArgumentException("Invalid keyboard geometry");
    for (int i = 0; i < codes.length; i++)
      if (rectangles[4 * i + 2] <= 0 || rectangles[4 * i + 3] <= 0)
        throw new IllegalArgumentException("Invalid key size");
    layoutNative(handle, width, height, codes, rectangles);
  }

  public synchronized List<Candidate> suggest(String word, int[] x, int[] y, String previous) {
    requireOpen();
    int[] cp = word.codePoints().toArray(), prev = previous.codePoints().toArray();
    if (cp.length >= 48 || prev.length >= 48 || x.length != cp.length || y.length != cp.length)
      return List.of();
    int[] count = new int[1], words = new int[48 * 18], scores = new int[18];
    suggestNative(handle, cp, x, y, prev, count, words, scores);
    List<Candidate> result = new ArrayList<>();
    for (int i = 0; i < Math.min(count[0], 18); i++) {
      int n = 0;
      while (n < 48 && words[i * 48 + n] != 0) n++;
      if (n > 0) {
        String text = new String(words, i * 48, n);
        result.add(
            new Candidate(
                text, scores[i], normalizedNative(cp, text.codePoints().toArray(), scores[i])));
      }
    }
    result.sort(Comparator.comparingInt(Candidate::score).reversed());
    return List.copyOf(result);
  }

  public synchronized boolean contains(String word) {
    requireOpen();
    int[] cp = word.codePoints().toArray();
    return cp.length > 0 && cp.length < 48 && probabilityNative(handle, cp) >= 0;
  }

  private void requireOpen() {
    if (handle == 0) throw new IllegalStateException("Engine is closed");
  }

  @Override
  public synchronized void close() {
    if (handle != 0) {
      closeNative(handle);
      handle = 0;
    }
  }

  private static native float normalizedNative(int[] before, int[] after, int score);

  private static native long openNative(String path, String locale, long size);

  private static native void closeNative(long handle);

  private static native void layoutNative(
      long handle, int width, int height, int[] codes, int[] rectangles);

  private static native int probabilityNative(long handle, int[] word);

  private static native void suggestNative(
      long handle,
      int[] word,
      int[] x,
      int[] y,
      int[] previous,
      int[] count,
      int[] words,
      int[] scores);
}
