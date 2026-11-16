// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public final class SpeechEnginePolicyTest {
  private final List<String> engines = List.of("com.google.android.tts/Recognizer", "chosen.engine/Recognizer");

  @Test public void configuredEngineWinsOverGoogleFallback() {
    assertEquals("chosen.engine/Recognizer", SpeechEnginePolicy.select("chosen.engine/Recognizer", engines));
  }

  @Test public void missingOrRemovedChoiceFallsBackWithoutSelectingUnapprovedArbitraryApps() {
    assertEquals(engines.get(0), SpeechEnginePolicy.select(null, engines));
    assertEquals(engines.get(0), SpeechEnginePolicy.select("removed/Engine", engines));
    assertNull(SpeechEnginePolicy.select(null, List.of("other.app/Recognizer")));
  }

  @Test public void unconfiguredDownloadedEngineNeedsSetupOnModernAndroid() {
    assertTrue(SpeechEnginePolicy.needsSetup(35, false, false, false));
    assertFalse(SpeechEnginePolicy.needsSetup(36, false, true, false));
    assertFalse(SpeechEnginePolicy.needsSetup(36, true, false, false));
    assertFalse(SpeechEnginePolicy.needsSetup(36, false, false, true));
    assertFalse(SpeechEnginePolicy.needsSetup(33, false, false, false));
  }
}
