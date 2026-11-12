// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import java.util.function.Supplier;

/** One short-lived, local offer. The clipboard supplier is never called when access is disabled. */
final class ClipboardOffer {
  static final long LIFETIME_MS = 90_000;
  private String value;
  private long capturedAt;

  void refresh(boolean allowed, long now, Supplier<String> clipboard) {
    clear();
    if (!allowed) return;
    String text = clipboard.get();
    if (text != null && !text.isBlank()) {
      value = text;
      capturedAt = now;
    }
  }

  String preview(boolean allowed, long now) {
    String text = current(allowed, now);
    if (text == null) return null;
    String preview = text.strip().replaceAll("\\s+", " ");
    return preview.length() > 40 ? preview.substring(0, 40) : preview;
  }

  String take(boolean allowed, long now) {
    String text = current(allowed, now);
    clear();
    return text;
  }

  private String current(boolean allowed, long now) {
    if (!allowed || now - capturedAt >= LIFETIME_MS) clear();
    return value;
  }

  void clear() {
    value = null;
    capturedAt = 0;
  }
}
