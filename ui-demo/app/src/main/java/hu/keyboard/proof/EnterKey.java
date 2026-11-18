// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.view.inputmethod.EditorInfo;

/** How the return key looks for an editor, following the iOS convention. */
final class EnterKey {
  private EnterKey() {}

  /**
   * True when the return key performs a primary action (Search, Go, Send, Done), which iOS draws
   * as a blue key with a white label. Next and a plain return keep the ordinary key colour.
   */
  static boolean primary(int imeOptions, boolean searching) {
    if (searching) return true;
    if ((imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return false;
    switch (imeOptions & EditorInfo.IME_MASK_ACTION) {
      case EditorInfo.IME_ACTION_SEARCH:
      case EditorInfo.IME_ACTION_GO:
      case EditorInfo.IME_ACTION_SEND:
      case EditorInfo.IME_ACTION_DONE:
        return true;
      default:
        return false;
    }
  }
}
