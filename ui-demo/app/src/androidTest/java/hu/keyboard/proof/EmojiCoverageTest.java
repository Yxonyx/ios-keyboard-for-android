// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.graphics.Paint;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

/** Read-only catalog and device font coverage check. */
public final class EmojiCoverageTest {
  @Test public void everyCatalogEntryIsReachableThroughItsCategoryOrVariants() {
    var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    var catalog = EmojiCatalog.get(context);
    var settings = new KeyboardSettings(context);
    var paint = new Paint();
    paint.setTypeface(EmojiCatalog.font(context));
    int reachable = 0, missing = 0;
    for (var entry : catalog.display(settings)) {
      assertTrue(entry.group >= 1 && entry.group <= 8);
      for (var variant : catalog.variants(entry)) {
        reachable++;
        if (!paint.hasGlyph(variant.emoji)) missing++;
      }
    }
    // Nine component-only records are deliberately not standalone keyboard keys.
    assertEquals(catalog.count() - 9, reachable);
    System.out.println("EMOJI_COVERAGE catalog=" + catalog.count() + " reachable=" + reachable
        + " fontMissing=" + missing);
  }
}
