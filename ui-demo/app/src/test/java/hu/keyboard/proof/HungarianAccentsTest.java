// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;
import org.junit.Test;

public final class HungarianAccentsTest {
  private final Languages.Language hu = Languages.get("hu");

  private String labels(String key, boolean rightward) {
    return String.join("", hu.popupLabels(key, rightward));
  }

  @Test public void visibleChoicesMatchTheSuppliedHungarianReference() {
    assertEquals("AÁÀÂÄÆÃÅĀ", labels("A", true));
    assertEquals("EÉÈÊËĘĖĒ", labels("E", true));
    assertEquals("ĪĮÌÏÎÍI", labels("I", false));
    assertEquals("ŌØŒÕÒÔŐÖOÓ", labels("O", false));
  }

  @Test public void longOIsImmediatelyRightOfBaseAndNoChoiceIsDropped() {
    var choices = hu.popupLabels("O", false);
    assertEquals(10, choices.size());
    assertEquals("Ó", choices.get(choices.indexOf("O") + 1));
    assertEquals(choices.size(), new java.util.HashSet<>(choices).size());
  }
}
