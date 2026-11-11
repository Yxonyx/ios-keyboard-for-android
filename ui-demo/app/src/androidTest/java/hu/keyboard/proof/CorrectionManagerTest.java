// SPDX-License-Identifier: Apache-2.0
package hu.keyboard.proof;

import static org.junit.Assert.*;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CorrectionManagerTest {
  @Test
  public void coldDictionaryExtractionAndLanguageSwitch() throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    var target = instrumentation.getTargetContext();
    Context fixture =
        new ContextWrapper(target) {
          final File folder =
              new File(target.getCacheDir(), "dictionary-fixture-" + System.nanoTime());

          @Override
          public Context getApplicationContext() {
            return this;
          }

          @Override
          public File getFilesDir() {
            folder.mkdirs();
            return folder;
          }
        };
    var manager = new CorrectionManager(fixture);
    try {
      for (String language : new String[] {"hu", "en"}) {
        String word = language.equals("hu") ? "hogy" : "hello";
        List<Integer> codes = new ArrayList<>(), rectangles = new ArrayList<>();
        Map<Integer, int[]> centers = new HashMap<>();
        String[] rows = {"qwertzuiop", "asdfghjkl", "yxcvbnm"};
        for (int row = 0; row < rows.length; row++)
          for (int i = 0; i < rows[row].length(); i++) {
            int cp = rows[row].charAt(i),
                x = 20 + i * 116 + (row == 1 ? 58 : row == 2 ? 174 : 0),
                y = 78 + row * 162;
            codes.add(cp);
            Collections.addAll(rectangles, x, y, 97, 129);
            centers.put(cp, new int[] {x + 48, y + 64});
          }
        int[][] layout = {
          codes.stream().mapToInt(Integer::intValue).toArray(),
          rectangles.stream().mapToInt(Integer::intValue).toArray()
        };
        int[] x = new int[word.length()], y = new int[word.length()];
        for (int i = 0; i < word.length(); i++) {
          x[i] = centers.get((int) word.charAt(i))[0];
          y[i] = centers.get((int) word.charAt(i))[1];
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CompositionSession.Result> response = new AtomicReference<>();
        instrumentation.runOnMainSync(
            () ->
                manager.request(
                    language,
                    "QWERTZ",
                    layout,
                    word,
                    "",
                    x,
                    y,
                    result -> {
                      response.set(result);
                      latch.countDown();
                    }));
        assertTrue("Engine callback timed out", latch.await(10, TimeUnit.SECONDS));
        assertTrue("Dictionary did not recognize fixture", response.get().originalKnown());
      }
    } finally {
      manager.close();
      manager.close();
    }
  }
}
