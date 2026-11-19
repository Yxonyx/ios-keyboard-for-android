// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;

import android.view.inputmethod.EditorInfo;
import org.junit.Test;

public final class EnterKeyTest {
  @Test public void primaryActionsAreBlueLikeIos() {
    assertTrue(EnterKey.primary(EditorInfo.IME_ACTION_SEARCH, false));
    assertTrue(EnterKey.primary(EditorInfo.IME_ACTION_GO, false));
    assertTrue(EnterKey.primary(EditorInfo.IME_ACTION_SEND, false));
    assertTrue(EnterKey.primary(EditorInfo.IME_ACTION_DONE, false));
  }

  @Test public void nextAndPlainReturnStayOrdinary() {
    assertFalse(EnterKey.primary(EditorInfo.IME_ACTION_NEXT, false));
    assertFalse(EnterKey.primary(EditorInfo.IME_ACTION_UNSPECIFIED, false));
    assertFalse(EnterKey.primary(EditorInfo.IME_ACTION_NONE, false));
  }

  @Test public void noEnterActionFlagWins() {
    assertFalse(
        EnterKey.primary(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_ENTER_ACTION, false));
  }

  @Test public void emojiSearchUsesThePrimaryKey() {
    assertTrue(EnterKey.primary(EditorInfo.IME_ACTION_UNSPECIFIED, true));
  }
}
