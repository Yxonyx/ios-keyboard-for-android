// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

/**
 * A firm downward drag on the keyboard hides it, the nearest an input method gets to iOS, where
 * dragging the page dismisses the keyboard. Distances are in the keyboard's 1179-wide design units.
 */
final class SwipeDown {
  /** More than one key row plus its gap, so sliding a finger to the key below still types. */
  static final float DISTANCE = 260f;

  private SwipeDown() {}

  /** True for a mostly vertical downward drag of at least {@link #DISTANCE}. */
  static boolean hides(float dx, float dy) {
    return dy >= DISTANCE && dy >= 2f * Math.abs(dx);
  }
}
