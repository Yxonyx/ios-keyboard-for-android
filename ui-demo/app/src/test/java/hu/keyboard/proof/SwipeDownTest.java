// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;

import org.junit.Test;

public final class SwipeDownTest {
  @Test public void firmDownwardDragHides() {
    assertTrue(SwipeDown.hides(0, 300));
    assertTrue(SwipeDown.hides(120, 300));
  }

  @Test public void slidingToTheKeyBelowStillTypes() {
    assertFalse(SwipeDown.hides(0, 150));   // one row down: neighbour correction
    assertFalse(SwipeDown.hides(0, 259));
  }

  @Test public void sidewaysOrUpwardDragsDoNotHide() {
    assertFalse(SwipeDown.hides(400, 300));  // diagonal, mostly sideways
    assertFalse(SwipeDown.hides(0, -400));   // upward
    assertFalse(SwipeDown.hides(500, 0));    // space-bar cursor drag
  }
}
