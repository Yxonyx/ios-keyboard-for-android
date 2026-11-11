// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.proof;

import static org.junit.Assert.*;

import java.util.*;
import java.util.function.Consumer;
import org.junit.Test;

public final class CompositionSessionTest {
  private static final class Editor implements CompositionSession.Editor {
    String text = "";
    int start = -1;

    public boolean compose(String value) {
      if (start < 0) start = text.length();
      text = text.substring(0, start) + value;
      return true;
    }

    public void commit(String value) {
      text = (start < 0 ? text : text.substring(0, start)) + value;
      start = -1;
    }

    public void finish() {
      start = -1;
    }

    public String before(int n) {
      return text.substring(Math.max(0, text.length() - n));
    }

    public int cursor() {
      return text.length();
    }

    public boolean deleteBefore(int n) {
      text = text.substring(0, text.length() - n);
      return true;
    }
  }

  private static final class Pending implements CompositionSession.Engine {
    final List<Consumer<CompositionSession.Result>> callbacks = new ArrayList<>();

    public void request(
        String word,
        String previous,
        int[] x,
        int[] y,
        Consumer<CompositionSession.Result> callback) {
      callbacks.add(callback);
    }

    void answer(int i, String text) {
      callbacks
          .get(i)
          .accept(
              new CompositionSession.Result(
                  false, List.of(new CompositionSession.Candidate(text, 0.9f, true))));
    }
  }

  @Test
  public void correctionAndImmediateUndo() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("hogz", 10, 20);
    p.answer(0, "hogy");
    s.type(" ", 10, 20);
    assertEquals("hogy ", e.text);
    assertTrue(s.delete());
    assertEquals("hogz", e.text);
    assertFalse(s.delete());
  }

  @Test
  public void staleReplyCannotRewriteNewWord() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("hogz", 10, 20);
    s.type(" ", 10, 20);
    s.type("szia", 10, 20);
    p.answer(0, "hogy");
    s.type(" ", 10, 20);
    assertEquals("hogz szia ", e.text);
  }

  @Test
  public void privateFieldDoesNotQueryEngine() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(false);
    s.type("secret", 10, 20);
    s.type(" ", 10, 20);
    assertEquals("secret ", e.text);
    assertTrue(p.callbacks.isEmpty());
  }

  @Test
  public void correctWordsArePreserved() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("hogy", 10, 20);
    p.callbacks
        .get(0)
        .accept(
            new CompositionSession.Result(
                true, List.of(new CompositionSession.Candidate("hogyh", 1, true))));
    s.type(" ", 10, 20);
    assertEquals("hogy ", e.text);
  }

  @Test
  public void noCorrectionWithoutReadyResult() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("hogz", 10, 20);
    s.type(" ", 10, 20);
    assertEquals("hogz ", e.text);
  }

  @Test
  public void editingExistingWordDoesNotCorrectAFraction() {
    Editor e = new Editor();
    e.text = "h";
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("ogy", 10, 20);
    s.type(" ", 10, 20);
    assertEquals("hogy ", e.text);
    assertTrue(p.callbacks.isEmpty());
  }

  @Test
  public void deleteDuringCompositionAndAccents() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("még", 10, 20);
    assertTrue(s.delete());
    assertEquals("mé", e.text);
    s.finish();
    assertEquals("mé", e.text);
  }

  @Test
  public void onlyNearbyEditsAreAccepted() {
    assertTrue(CompositionSession.oneEdit("hogz", "hogy"));
    assertTrue(CompositionSession.oneEdit("szai", "szia"));
    assertTrue(CompositionSession.oneEdit("szo", "szó"));
    assertTrue(CompositionSession.oneEdit("sziaa", "szia"));
    assertFalse(CompositionSession.oneEdit("szia", "szia"));
    assertFalse(CompositionSession.oneEdit("szia", "hello"));
  }

  @Test
  public void filterBeforeChoosingAndRequireClearMargin() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("hogz", 10, 20);
    p.callbacks
        .get(0)
        .accept(
            new CompositionSession.Result(
                false,
                List.of(
                    new CompositionSession.Candidate("jogú", 0.6f, true),
                    new CompositionSession.Candidate("hoz", 0.3f, true),
                    new CompositionSession.Candidate("hogy", 0.35f, true),
                    new CompositionSession.Candidate("Hogy", 0.34f, true))));
    s.type(" ", 10, 20);
    assertEquals("hogy ", e.text);
    s.type("hogz", 10, 20);
    p.callbacks
        .get(1)
        .accept(
            new CompositionSession.Result(
                false,
                List.of(
                    new CompositionSession.Candidate("hoz", 0.34f, true),
                    new CompositionSession.Candidate("hogy", 0.35f, true))));
    s.type(" ", 10, 20);
    assertEquals("hogy hogz ", e.text);
  }

  @Test
  public void movingSelectionInvalidatesUndo() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("hogz", 10, 20);
    p.answer(0, "hogy");
    s.type(" ", 10, 20);
    s.selectionChanged(0, 2, -1);
    assertFalse(s.delete());
    assertEquals("hogy ", e.text);
  }

  @Test
  public void staleComposingSelectionDoesNotCancelUndo() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("hogz", 10, 20);
    p.answer(0, "hogy");
    s.type(" ", 10, 20);
    s.selectionChanged(4, 4, 4);
    assertTrue(s.delete());
    assertEquals("hogz", e.text);
  }

  @Test
  public void stripShowsTypedCorrectionAndCompletions() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    List<CompositionSession.Suggestions> shown = new ArrayList<>();
    s.setListener(shown::add);
    s.reset(true);
    s.type("Hogz", 10, 20);
    assertEquals("Hogz", shown.get(shown.size() - 1).typed());
    assertNull(shown.get(shown.size() - 1).correction());
    p.callbacks
        .get(0)
        .accept(
            new CompositionSession.Result(
                false,
                List.of(
                    new CompositionSession.Candidate("hogy", 0.9f, true),
                    new CompositionSession.Candidate("hogyan", 0.5f, true),
                    new CompositionSession.Candidate("hogyha", 0.4f, true))));
    var last = shown.get(shown.size() - 1);
    assertEquals("Hogy", last.correction());
    assertEquals(List.of("Hogyan"), last.others());
    s.type(" ", 10, 20);
    assertEquals(CompositionSession.Suggestions.NONE, shown.get(shown.size() - 1));
  }

  @Test
  public void pickingCandidateReplacesWordWithoutUndo() {
    Editor e = new Editor();
    e.text = "Na ";
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    s.reset(true);
    s.type("hog", 10, 20);
    assertTrue(s.pick("hogyan"));
    assertEquals("Na hogyan ", e.text);
    assertFalse(s.delete());
    assertFalse(s.pick("más"));
  }

  @Test
  public void singleCapitalDoesNotShoutCandidates() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    List<CompositionSession.Suggestions> shown = new ArrayList<>();
    s.setListener(shown::add);
    s.reset(true);
    s.type("A", 10, 20);
    p.callbacks
        .get(0)
        .accept(
            new CompositionSession.Result(
                true, List.of(new CompositionSession.Candidate("az", 0.9f, true))));
    assertEquals(List.of("Az"), shown.get(shown.size() - 1).others());
  }

  @Test
  public void stripWorksWithAutocorrectOff() {
    Editor e = new Editor();
    Pending p = new Pending();
    var s = new CompositionSession(e, p);
    List<CompositionSession.Suggestions> shown = new ArrayList<>();
    s.setListener(shown::add);
    s.setAutocorrect(false);
    s.reset(true);
    s.type("hogz", 10, 20);
    p.callbacks
        .get(0)
        .accept(
            new CompositionSession.Result(
                false,
                List.of(
                    new CompositionSession.Candidate("jogú", 0.3f, true),
                    new CompositionSession.Candidate("hogy", 0.9f, true))));
    var last = shown.get(shown.size() - 1);
    assertNull(last.correction());
    assertEquals(List.of("hogy", "jogú"), last.others());
    s.type(" ", 10, 20);
    assertEquals("hogz ", e.text);
  }
}
