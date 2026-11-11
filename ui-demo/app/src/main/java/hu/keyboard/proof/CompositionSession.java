// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.proof;

import java.util.*;
import java.util.function.Consumer;

/** Editor-scoped, transient composition. Does not own a thread or retain finished messages. */
final class CompositionSession {
  interface Editor {
    boolean compose(String value);

    void commit(String value);

    void finish();

    String before(int length);

    boolean deleteBefore(int length);

    default int cursor() {
      return -1;
    }
  }

  interface Engine {
    void request(String word, String previous, int[] x, int[] y, Consumer<Result> callback);
  }

  record Candidate(String text, float confidence, boolean known) {}

  record Result(boolean originalKnown, List<Candidate> candidates) {}

  /** What the suggestion strip shows; {@code correction} is applied at the next word boundary. */
  record Suggestions(String typed, String correction, List<String> others) {
    static final Suggestions NONE = new Suggestions("", null, List.of());
  }

  interface Listener {
    void suggestions(Suggestions value);
  }

  private final Editor editor;
  private final Engine engine;
  private final StringBuilder word = new StringBuilder();
  private final List<Integer> xs = new ArrayList<>(), ys = new ArrayList<>();
  private String previous = "", undoOriginal = "", undoSuffix = "";
  private Result ready;
  private int undoCursor = -1;
  private boolean enabled, blocked, autocorrect = true;
  private long generation;
  private Listener listener = value -> {};

  CompositionSession(Editor editor, Engine engine) {
    this.editor = editor;
    this.engine = engine;
  }

  /** With autocorrection off, candidates are still offered but the typed word is kept. */
  void setAutocorrect(boolean value) {
    autocorrect = value;
  }

  void setListener(Listener value) {
    listener = value == null ? v -> {} : value;
  }

  void reset(boolean allowed) {
    generation++;
    enabled = allowed;
    word.setLength(0);
    xs.clear();
    ys.clear();
    previous = "";
    ready = null;
    blocked = false;
    clearUndo();
    listener.suggestions(Suggestions.NONE);
  }

  boolean composing() {
    return word.length() > 0;
  }

  void finish() {
    if (composing()) editor.finish();
    reset(enabled);
  }

  void type(String value, int x, int y) {
    if (value.isEmpty()) return;
    boolean letters = value.codePoints().allMatch(Character::isLetter);
    if (!enabled) {
      editor.commit(value);
      return;
    }
    if (!letters) {
      boundary(value);
      return;
    }
    clearUndo();
    if (!composing() && !blocked) {
      String before = editor.before(128);
      if (!before.isEmpty() && Character.isLetter(before.codePointBefore(before.length())))
        blocked = true;
      previous = lastWord(before);
    }
    if (blocked
        || word.codePointCount(0, word.length()) + value.codePointCount(0, value.length()) >= 48) {
      if (composing()) {
        editor.finish();
        clearWord();
      }
      blocked = true;
      editor.commit(value);
      return;
    }
    word.append(value);
    value
        .codePoints()
        .forEach(
            cp -> {
              xs.add(x);
              ys.add(y);
            });
    if (!editor.compose(word.toString())) {
      editor.finish();
      clearWord();
      blocked = true;
      editor.commit(value);
      return;
    }
    request();
  }

  private void request() {
    ready = null;
    publish();
    long token = ++generation;
    String snapshot = word.toString();
    engine.request(
        snapshot,
        previous,
        xs.stream().mapToInt(Integer::intValue).toArray(),
        ys.stream().mapToInt(Integer::intValue).toArray(),
        result -> {
          if (token == generation && enabled && word.toString().equals(snapshot)) {
            ready = result;
            publish();
          }
        });
  }

  private void publish() {
    String typed = word.toString();
    if (typed.isEmpty()) {
      listener.suggestions(Suggestions.NONE);
      return;
    }
    String best = bestCorrection(), correction = autocorrect ? best : null;
    List<String> others = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    seen.add(typed.toLowerCase(Locale.ROOT));
    if (best != null) seen.add(best.toLowerCase(Locale.ROOT));
    if (best != null && correction == null) others.add(best);
    if (ready != null)
      for (Candidate c : ready.candidates()) {
        if (others.size() >= (correction == null ? 2 : 1)) break;
        if (!c.known() || !Float.isFinite(c.confidence())) continue;
        if (seen.add(c.text().toLowerCase(Locale.ROOT))) others.add(matchCase(typed, c.text()));
      }
    listener.suggestions(new Suggestions(typed, correction, List.copyOf(others)));
  }

  /** Replaces the word being composed with a strip candidate and a trailing space. */
  boolean pick(String value) {
    if (!composing() || value.isEmpty()) return false;
    editor.commit(value + " ");
    editor.finish();
    clearWord();
    blocked = false;
    clearUndo();
    return true;
  }

  /** The replacement that the next boundary would apply, or null to keep the typed word. */
  private String correction() {
    return autocorrect ? bestCorrection() : null;
  }

  /** A confident nearby replacement for an unknown word, regardless of the autocorrect setting. */
  private String bestCorrection() {
    String original = word.toString();
    if (!original.isEmpty()
        && ready != null
        && !ready.originalKnown()
        && !ready.candidates().isEmpty()) {
      Map<String, Candidate> distinct = new HashMap<>();
      for (Candidate c : ready.candidates()) {
        if (!c.known()
            || !Float.isFinite(c.confidence())
            || c.confidence() < 0.185f
            || !oneEdit(original, c.text())) continue;
        String key = c.text().toLowerCase(Locale.ROOT);
        Candidate existing = distinct.get(key);
        if (existing == null || c.confidence() > existing.confidence()) distinct.put(key, c);
      }
      List<Candidate> eligible = new ArrayList<>(distinct.values());
      eligible.sort(Comparator.comparingDouble(Candidate::confidence).reversed());
      if (original.codePointCount(0, original.length()) >= 3
          && !eligible.isEmpty()
          && (eligible.size() == 1
              || eligible.get(0).confidence() - eligible.get(1).confidence() >= 0.02f))
        return matchCase(original, eligible.get(0).text());
    }
    return null;
  }

  private void boundary(String separator) {
    String original = word.toString(), correction = correction();
    String replacement = correction == null ? original : correction;
    editor.commit(replacement + separator);
    editor.finish();
    clearWord();
    blocked = false;
    clearUndo();
    if (!original.equals(replacement) && !separator.isEmpty()) {
      undoOriginal = original;
      undoSuffix = replacement + separator;
      undoCursor = editor.cursor();
    }
  }

  boolean delete() {
    if (composing()) {
      int start = word.offsetByCodePoints(word.length(), -1);
      word.delete(start, word.length());
      xs.remove(xs.size() - 1);
      ys.remove(ys.size() - 1);
      editor.compose(word.toString());
      if (composing()) request();
      else {
        editor.finish();
        clearWord();
      }
      return true;
    }
    if (!undoSuffix.isEmpty()) {
      String suffix = undoSuffix, original = undoOriginal;
      int expectedCursor = undoCursor;
      clearUndo();
      if ((expectedCursor < 0 || editor.cursor() == expectedCursor)
          && editor.before(suffix.length()).equals(suffix)
          && editor.deleteBefore(suffix.length())) {
        editor.commit(original);
        blocked = true;
        return true;
      }
    }
    return false;
  }

  void selectionChanged(int start, int end, int candidatesEnd) {
    if (composing() && (start != end || end != candidatesEnd)) finish();
    else if (candidatesEnd < 0
        && !undoSuffix.isEmpty()
        && (start != end || (undoCursor >= 0 && end != undoCursor))) clearUndo();
  }

  private void clearWord() {
    generation++;
    word.setLength(0);
    xs.clear();
    ys.clear();
    previous = "";
    ready = null;
    listener.suggestions(Suggestions.NONE);
  }

  private void clearUndo() {
    undoOriginal = "";
    undoSuffix = "";
    undoCursor = -1;
  }

  private static String lastWord(String value) {
    int end = value.length();
    while (end > 0 && !Character.isLetter(value.codePointBefore(end)))
      end = value.offsetByCodePoints(end, -1);
    int start = end;
    while (start > 0 && Character.isLetter(value.codePointBefore(start)))
      start = value.offsetByCodePoints(start, -1);
    return value.substring(start, end);
  }

  private static String matchCase(String original, String replacement) {
    if (original.codePointCount(0, original.length()) > 1
        && original.equals(original.toUpperCase(Locale.ROOT)))
      return replacement.toUpperCase(Locale.ROOT);
    if (Character.isUpperCase(original.codePointAt(0)))
      return replacement.substring(0, 1).toUpperCase(Locale.ROOT) + replacement.substring(1);
    return replacement;
  }

  /** Bounded Unicode edit distance, also accepting an adjacent transposition. */
  static boolean oneEdit(String a, String b) {
    int[] x = a.toLowerCase(Locale.ROOT).codePoints().toArray(),
        y = b.toLowerCase(Locale.ROOT).codePoints().toArray();
    if (Arrays.equals(x, y) || Math.abs(x.length - y.length) > 1) return false;
    if (x.length == y.length) {
      int first = -1, last = -1, count = 0;
      for (int i = 0; i < x.length; i++)
        if (x[i] != y[i]) {
          if (first < 0) first = i;
          last = i;
          count++;
        }
      return count == 1
          || (count == 2 && last == first + 1 && x[first] == y[last] && x[last] == y[first]);
    }
    int[] longer = x.length > y.length ? x : y, shorter = x.length > y.length ? y : x;
    int i = 0, k = 0, skips = 0;
    while (i < longer.length && k < shorter.length) {
      if (longer[i] == shorter[k]) {
        i++;
        k++;
      } else {
        if (++skips > 1) return false;
        i++;
      }
    }
    return true;
  }
}
