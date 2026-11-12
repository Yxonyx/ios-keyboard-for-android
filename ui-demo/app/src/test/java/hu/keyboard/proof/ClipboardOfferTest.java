// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;

import org.junit.Test;

public final class ClipboardOfferTest {
  @Test
  public void deniedAccessNeverInvokesTheClipboardReader() {
    ClipboardOffer offer = new ClipboardOffer();
    offer.refresh(false, 1000, () -> { throw new AssertionError("Clipboard was read"); });
    assertNull(offer.preview(true, 1001));
    assertNull(offer.take(true, 1001));
  }

  @Test
  public void revocationDropsTheValueRatherThanHidingItTemporarily() {
    ClipboardOffer offer = new ClipboardOffer();
    offer.refresh(true, 1000, () -> "synthetic clipboard");
    assertNull(offer.preview(false, 1001));
    assertNull(offer.preview(true, 1002));
    assertNull(offer.take(true, 1002));
  }

  @Test
  public void expiryAndSingleUseDoNotLeaveAReusableValue() {
    ClipboardOffer offer = new ClipboardOffer();
    offer.refresh(true, 1000, () -> "synthetic");
    assertEquals("synthetic", offer.preview(true, 1000 + ClipboardOffer.LIFETIME_MS - 1));
    assertNull(offer.take(true, 1000 + ClipboardOffer.LIFETIME_MS));
    offer.refresh(true, 100_000, () -> "new synthetic value");
    assertEquals("new synthetic value", offer.take(true, 100_001));
    assertNull(offer.take(true, 100_002));
  }

  @Test
  public void sensitiveOrEmptyClipboardClearsThePreviousOffer() {
    ClipboardOffer offer = new ClipboardOffer();
    offer.refresh(true, 1000, () -> "previous synthetic value");
    offer.refresh(true, 1001, () -> null);
    assertNull(offer.preview(true, 1002));
    offer.refresh(true, 1003, () -> "another synthetic value");
    offer.refresh(true, 1004, () -> "  \n  ");
    assertNull(offer.take(true, 1005));
  }

  @Test
  public void shortenedPreviewDoesNotAlterThePastedText() {
    ClipboardOffer offer = new ClipboardOffer();
    String text = "  synthetic\n\tclipboard with a long second line for preview  ";
    offer.refresh(true, 1000, () -> text);
    assertEquals(40, offer.preview(true, 1001).length());
    assertFalse(offer.preview(true, 1001).contains("\n"));
    assertEquals(text, offer.take(true, 1002));
  }
}
