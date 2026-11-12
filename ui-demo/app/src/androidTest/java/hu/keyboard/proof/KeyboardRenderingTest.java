// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Process;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class KeyboardRenderingTest {
  private Context fixture() {
    Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = target.getSharedPreferences("render-test-" + System.nanoTime(), 0);
    return new ContextWrapper(target) {
      @Override
      public SharedPreferences getSharedPreferences(String name, int mode) {
        return prefs;
      }
    };
  }

  private static final KeyboardProofView.Output OUTPUT = new KeyboardProofView.Output() {
    public void text(String value) {}
    public void delete() {}
    public void enter() {}
    public void emoji() {}
    public void settings() {}
    public void next() {}
    public void cursor(int direction) {}
    public void popup(boolean shown) {}
  };

  @Test
  public void rendersAllLanguagesAcrossPhoneLandscapeAndTabletSizes() {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    instrumentation.runOnMainSync(() -> {
      Context context = fixture();
      KeyboardSettings settings = new KeyboardSettings(context);
      KeyboardProofView view = new KeyboardProofView(context);
      view.setOutput(OUTPUT);
      for (Languages.Language language : Languages.ALL) {
        for (String layout : language.layouts()) {
          settings.prefs.edit().putString("language", language.code())
              .putString("layout_" + language.code(), layout).commit();
          view.refreshSettings();
          Set<Integer> letters = new HashSet<>();
          for (int cp : view.correctionLayout()[0]) letters.add(cp);
          for (int cp = 'a'; cp <= 'z'; cp++)
            assertTrue(language.code() + "/" + layout + " missing " + (char) cp, letters.contains(cp));
          if (language.code().equals("es")) assertTrue(letters.contains((int) 'ñ'));
          for (int[] screen : new int[][] {{320, 800}, {1080, 2400}, {1200, 800}, {1600, 2560}}) {
            int height = Math.min(Math.round(screen[0] * 906f / 1179), Math.round(screen[1] * .44f));
            view.setImeBoardPixels(height);
            view.measure(View.MeasureSpec.makeMeasureSpec(screen[0], View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, screen[0], height);
            Bitmap bitmap = Bitmap.createBitmap(screen[0], height, Bitmap.Config.ARGB_8888);
            try {
              for (String theme : new String[] {"light", "dark"}) {
                settings.prefs.edit().putString("theme", theme).commit();
                for (String page : new String[] {"letters", "numbers", "symbols"}) {
                  view.showPreview(page);
                  view.draw(new Canvas(bitmap));
                }
              }
            } finally {
              bitmap.recycle();
            }
          }
        }
      }
    });
  }

  @Test
  public void privateEditorSuppressesSuggestionAndClipboardStrip() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      KeyboardProofView view = new KeyboardProofView(fixture());
      view.setOutput(OUTPUT);
      view.setSuggestionStrip(true);
      view.setEditorHasText(true);
      view.startEditor(false, false, true, "");
      view.showSuggestions(new CompositionSession.Suggestions("synthetic", "example", List.of()),
          "SYNTHETIC_CLIPBOARD");
      assertFalse(view.stripVisible());
    });
  }

  @Test
  public void runnerMatchesRequestedProcessArchitecture() {
    String expected = InstrumentationRegistry.getArguments().getString("expectedProcess64");
    if (expected != null) assertEquals(Boolean.parseBoolean(expected), Process.is64Bit());
  }
}
