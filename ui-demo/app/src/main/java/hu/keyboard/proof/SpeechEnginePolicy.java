// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import java.util.List;

/** Preserve the user's system recognizer choice before considering fallback providers. */
final class SpeechEnginePolicy {
  private SpeechEnginePolicy() {}

  static String select(String configured, List<String> available) {
    if (configured != null && available.contains(configured)) return configured;
    for (String wanted : List.of("com.google.android.tts", "com.google.android.googlequicksearchbox"))
      for (String component : available)
        if (component.startsWith(wanted + "/")) return component;
    return null;
  }

  static boolean needsSetup(int api, boolean systemApp, boolean systemChoice, boolean onDeviceChoice) {
    return api >= 35 && !systemApp && !systemChoice && !onDeviceChoice;
  }
}
