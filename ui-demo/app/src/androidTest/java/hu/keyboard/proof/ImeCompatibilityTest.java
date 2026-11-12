// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Exercises the actual IME/editor connection; requires opt-in and refuses physical devices. */
@RunWith(AndroidJUnit4.class)
public final class ImeCompatibilityTest {
  private Instrumentation instrumentation;
  private Context context;
  private TypingActivity activity;
  private String originalIme, component;
  private boolean originallyEnabled;
  private SharedPreferences prefs;
  private Map<String, ?> originalPrefs;
  private JSONArray letterKeys, numberKeys;

  @Before
  public void start() throws Exception {
    assumeTrue("IME switching must be explicitly enabled for a disposable emulator",
        "true".equals(InstrumentationRegistry.getArguments().getString("allowImeSwitch")));
    assumeTrue("Refusing to change a physical device keyboard",
        Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
    instrumentation = InstrumentationRegistry.getInstrumentation();
    context = instrumentation.getTargetContext();
    var automation = instrumentation.getUiAutomation();
    var info = automation.getServiceInfo();
    info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    automation.setServiceInfo(info);
    component = new ComponentName(context, KeyboardService.class).flattenToShortString();
    originalIme = shell("settings get secure default_input_method").trim();
    originallyEnabled = shell("settings get secure enabled_input_methods").contains(component);
    prefs = new KeyboardSettings(context).prefs;
    originalPrefs = new HashMap<>(prefs.getAll());
    prefs.edit().clear().putString("language", "en").putString("layout_en", "QWERTY")
        .putBoolean("autocorrect", false).putBoolean("suggestions", false)
        .putBoolean("auto_caps", false).putBoolean("double_space", false)
        .putBoolean("key_preview", false).commit();
    try (var input = context.getAssets().open("geometry.json")) {
      JSONObject states = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8))
          .getJSONObject("states");
      letterKeys = states.getJSONArray("letters");
      numberKeys = states.getJSONArray("numbers");
    }
    activity = (TypingActivity) instrumentation.startActivitySync(
        new Intent(context, TypingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    String enabled = shell("ime enable " + quote(component));
    String selected = shell("ime set " + quote(component));
    long deadline = SystemClock.uptimeMillis() + 5000;
    String current;
    do {
      current = shell("settings get secure default_input_method").trim();
      if (component.equals(current)) break;
      SystemClock.sleep(50);
    } while (SystemClock.uptimeMillis() < deadline);
    assertEquals("Enable: " + enabled + "; select: " + selected, component, current);
  }

  @After
  public void restore() throws Exception {
    if (instrumentation == null) return;
    if (activity != null) instrumentation.runOnMainSync(activity::finish);
    try {
      if (originalIme != null && !originalIme.isEmpty() && !originalIme.equals("null"))
        shell("ime set " + quote(originalIme));
      if (!originallyEnabled && component != null) shell("ime disable " + quote(component));
    } finally {
      if (prefs != null && originalPrefs != null) {
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (var entry : originalPrefs.entrySet()) {
          Object value = entry.getValue();
          if (value instanceof String) edit.putString(entry.getKey(), (String) value);
          else if (value instanceof Boolean) edit.putBoolean(entry.getKey(), (Boolean) value);
          else if (value instanceof Integer) edit.putInt(entry.getKey(), (Integer) value);
          else if (value instanceof Long) edit.putLong(entry.getKey(), (Long) value);
          else if (value instanceof Float) edit.putFloat(entry.getKey(), (Float) value);
          else if (value instanceof Set) edit.putStringSet(entry.getKey(), (Set<String>) value);
        }
        edit.commit();
      }
    }
  }

  // UiAutomation executes argv directly, without a shell interpreting quote characters.
  private static String quote(String value) {
    if (!value.matches("[A-Za-z0-9_.$/]+")) throw new IllegalArgumentException("Invalid component");
    return value;
  }

  private String shell(String command) throws IOException {
    try (var input = new ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.getUiAutomation().executeShellCommand(command))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private EditText find(View view, String description) {
    if (view instanceof EditText && description.contentEquals(view.getContentDescription()))
      return (EditText) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        EditText found = find(group.getChildAt(i), description);
        if (found != null) return found;
      }
    }
    return null;
  }

  private EditText focus(String description) {
    AtomicReference<EditText> result = new AtomicReference<>();
    instrumentation.runOnMainSync(() -> {
      EditText field = find(activity.findViewById(android.R.id.content), description);
      assertNotNull(description, field);
      field.requestFocus();
      context.getSystemService(InputMethodManager.class).showSoftInput(field, 0);
      result.set(field);
    });
    // The window can be accessible while the IME/scroll animation is still moving it.
    // Send touches only after the actual on-screen bounds have remained stable.
    Rect previous = keyboardBounds();
    long stableSince = SystemClock.uptimeMillis();
    long deadline = stableSince + 6000;
    while (SystemClock.uptimeMillis() < deadline) {
      SystemClock.sleep(50);
      Rect current = keyboardBounds();
      if (!current.equals(previous)) {
        previous = current;
        stableSince = SystemClock.uptimeMillis();
      }
      if (SystemClock.uptimeMillis() - stableSince >= 350) return result.get();
    }
    fail("IME bounds did not settle before typing");
    return result.get();
  }

  private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String text) {
    if (node == null) return null;
    if (text.equals(String.valueOf(node.getContentDescription())) || text.equals(String.valueOf(node.getText()))) return node;
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo found = findNode(node.getChild(i), text);
      if (found != null) return found;
    }
    return null;
  }

  private AccessibilityNodeInfo imeNode(String text) {
    long deadline = SystemClock.uptimeMillis() + 8000;
    do {
      for (AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
        if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
        AccessibilityNodeInfo node = findNode(window.getRoot(), text);
        if (node != null) return node;
      }
      SystemClock.sleep(50);
    } while (SystemClock.uptimeMillis() < deadline);
    throw new AssertionError("IME node not found: " + text);
  }

  private AccessibilityNodeInfo appNode(String text) {
    long deadline = SystemClock.uptimeMillis() + 8000;
    do {
      for (AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
        if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
        AccessibilityNodeInfo node = findNode(window.getRoot(), text);
        if (node != null) return node;
      }
      SystemClock.sleep(50);
    } while (SystemClock.uptimeMillis() < deadline);
    throw new AssertionError("Application node not found: " + text);
  }

  private Rect keyboardBounds() {
    Rect rect = new Rect();
    imeNode("iOS Keyboard for Android billentyűzet").getBoundsInScreen(rect);
    assertTrue("Keyboard width", rect.width() > 0);
    assertTrue("Keyboard height", rect.height() > 0);
    return rect;
  }

  private void tap(float designX, float designY) {
    Rect rect = keyboardBounds();
    float x = rect.left + designX * rect.width() / 1179f;
    float y = rect.top + (designY - 1650) * rect.height() / 906f;
    long down = SystemClock.uptimeMillis();
    for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
      MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
      event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
      assertTrue("Touch injection rejected", instrumentation.getUiAutomation().injectInputEvent(event, true));
      event.recycle();
      SystemClock.sleep(20);
    }
    instrumentation.waitForIdleSync();
  }

  private void screenshot(String name) throws IOException {
    shell("mkdir -p /sdcard/Download/ime-compat");
    shell("screencap -p /sdcard/Download/ime-compat/" + name + ".png");
    System.out.println("IME diagnostics " + name + ": " + keyboardBounds()
        + "; windows=" + instrumentation.getUiAutomation().getWindows());
  }

  private void key(String label, boolean numbers) throws Exception {
    if (!numbers) {
      if (label.equals("Y")) label = "Z";
      else if (label.equals("Z")) label = "Y";
    }
    JSONArray keys = numbers ? numberKeys : letterKeys;
    for (int i = 0; i < keys.length(); i++) {
      JSONObject key = keys.getJSONObject(i);
      if (!key.getString("label").equals(label)) continue;
      JSONArray r = key.getJSONArray("rect");
      tap((float) (r.getDouble(0) + r.getDouble(2)) / 2,
          (float) (r.getDouble(1) + r.getDouble(3)) / 2);
      return;
    }
    throw new AssertionError("Unknown key " + label);
  }

  private void expect(EditText field, String expected) {
    AtomicReference<String> actual = new AtomicReference<>();
    long deadline = SystemClock.uptimeMillis() + 3000;
    do {
      instrumentation.runOnMainSync(() -> actual.set(field.getText().toString()));
      if (expected.equals(actual.get())) return;
      SystemClock.sleep(30);
    } while (SystemClock.uptimeMillis() < deadline);
    assertEquals(expected, actual.get());
  }

  @Test
  public void noLearningEditorSuppressesTheClipboardOfferAndSuggestionStrip() throws Exception {
    EditText message = focus("Üzenet");
    ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
    AtomicReference<ClipData> previous = new AtomicReference<>();
    instrumentation.runOnMainSync(() -> {
      previous.set(clipboard.getPrimaryClip());
      clipboard.setPrimaryClip(ClipData.newPlainText("synthetic test", "SYNTHETIC_CLIPBOARD"));
    });
    prefs.edit().putBoolean("suggestions", true).putBoolean("clipboard_suggest", true).commit();
    try {
      focus("Üzenet");
      int offeredHeight = keyboardBounds().height();
      screenshot("clipboard-normal-field");
      focus("Jelszópróba");
      int privateHeight = keyboardBounds().height();
      assertTrue("Positive control: normal field did not show its clipboard strip",
          offeredHeight > privateHeight);
      instrumentation.runOnMainSync(() -> {
        message.setImeOptions(message.getImeOptions()
            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        message.setText("synthetic");
        message.setSelection(message.length());
      });
      focus("Üzenet");
      screenshot("clipboard-private-field");
      assertEquals("No-learning field must hide suggestions and clipboard", privateHeight,
          keyboardBounds().height());
      key("A", false);
      expect(message, "synthetica");
    } finally {
      prefs.edit().putBoolean("clipboard_suggest", false).commit();
      instrumentation.runOnMainSync(() -> {
        if (previous.get() == null) clipboard.clearPrimaryClip();
        else clipboard.setPrimaryClip(previous.get());
      });
    }
  }

  @Test
  public void decliningSpeechApprovalKeepsTypingLocal() throws Exception {
    EditText field = focus("Üzenet");
    int microphoneBefore = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO);
    tap(1072, 2436);
    appNode(context.getString(R.string.speech_consent_title));
    assertTrue(String.valueOf(appNode(context.getString(R.string.speech_consent_message)).getText())
        .contains("upload microphone audio"));
    assertTrue(appNode(context.getString(R.string.speech_consent_decline))
        .performAction(AccessibilityNodeInfo.ACTION_CLICK));
    instrumentation.waitForIdleSync();
    assertFalse(new KeyboardSettings(context).enabled(MicPermissionActivity.CONSENT_KEY, false));
    assertEquals(microphoneBefore, context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO));
    focus("Üzenet");
    key("A", false);
    expect(field, "a");
  }

  @Test
  public void literalTypingDeleteAndSelectionReplacement() throws Exception {
    EditText field = focus("Üzenet");
    for (char ch : "HELLO".toCharArray()) key(String.valueOf(ch), false);
    key("space", false);
    expect(field, "hello ");
    key("delete", false);
    expect(field, "hello");
    instrumentation.runOnMainSync(() -> field.setSelection(1, 4));
    key("A", false);
    expect(field, "hao");
  }

  @Test
  public void numericSearchActionAndEmojiInsertion() throws Exception {
    EditText numeric = focus("Szám");
    key("1", true); key("2", true); key("3", true);
    expect(numeric, "123");
    EditText search = focus("Keresés");
    AtomicBoolean submitted = new AtomicBoolean();
    instrumentation.runOnMainSync(() -> search.setOnEditorActionListener((view, action, event) -> {
      submitted.set(action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
      return true;
    }));
    key("return", false);
    long deadline = SystemClock.uptimeMillis() + 3000;
    while (!submitted.get() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
    assertTrue("Search editor action was not delivered", submitted.get());
    EditText message = focus("Üzenet");
    tap(127.5f, 2436);
    AccessibilityNodeInfo emoji = imeNode("😀");
    assertTrue(emoji.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    expect(message, "😀");
  }

  @Test
  public void passwordStaysLiteralAndDoesNotOpenSpeechConsent() throws Exception {
    prefs.edit().putBoolean("autocorrect", true).putBoolean("suggestions", true).commit();
    EditText field = focus("Jelszópróba");
    for (char ch : "HOGZ".toCharArray()) key(String.valueOf(ch), false);
    key("space", false);
    expect(field, "hogz ");
    screenshot("password-before-mic");
    tap(1072, 2436);
    instrumentation.getUiAutomation().waitForIdle(200, 3000);
    assertFalse(new KeyboardSettings(context).enabled(MicPermissionActivity.CONSENT_KEY, false));
    for (AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows())
      if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION)
        assertNull("Password field opened external speech approval",
            findNode(window.getRoot(), context.getString(R.string.speech_consent_title)));
    screenshot("password-after-mic");
    expect(field, "hogz ");
    keyboardBounds();
  }
}
