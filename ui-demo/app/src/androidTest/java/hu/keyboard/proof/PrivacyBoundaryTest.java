// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PrivacyBoundaryTest {
  @Test
  public void installedKeyboardHasNoInternetPermission() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String[] permissions = context.getPackageManager()
        .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
    assertFalse(Arrays.asList(permissions).contains(Manifest.permission.INTERNET));
    assertEquals(PackageManager.PERMISSION_DENIED,
        context.checkSelfPermission(Manifest.permission.INTERNET));
  }

  @Test
  public void speechCannotStartWithoutSeparateApproval() {
    Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = target.getSharedPreferences("speech-test-" + System.nanoTime(), 0);
    Context context = new ContextWrapper(target) {
      @Override
      public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
    };
    AtomicBoolean callback = new AtomicBoolean();
    Dictation.Listener listener = new Dictation.Listener() {
      public void status(String value) { callback.set(true); }
      public void partial(String value) { callback.set(true); }
      public void done(String value) { callback.set(true); }
      public void failed(int error) { callback.set(true); }
    };
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      Dictation dictation = new Dictation(context, listener);
      try {
        dictation.start("en-US");
        fail("Speech started before approval");
      } catch (IllegalStateException expected) {
        assertEquals("External speech recognition has not been approved", expected.getMessage());
        assertFalse(dictation.active());
        assertFalse(callback.get());
      } finally {
        dictation.cancel();
      }
    });
  }
}
