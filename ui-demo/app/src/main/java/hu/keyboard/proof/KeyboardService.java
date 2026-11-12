// SPDX-License-Identifier: GPL-3.0-only
package hu.keyboard.proof;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.List;
import java.util.regex.*;

/** Real IME. External editor text is never retained in the preview buffer or preferences. */
public final class KeyboardService extends InputMethodService implements KeyboardProofView.Output {
  private LinearLayout root;
  private KeyboardProofView keyboard;
  private KeyboardSettings settings;
  private CorrectionManager correction;
  private CompositionSession composition;
  private boolean sensitive, searching, popupVisible;
  private String query = "";
  private TextView queryLabel;
  private CompositionSession.Suggestions shown = CompositionSession.Suggestions.NONE;
  private ClipboardManager clipboard;
  private final ClipboardOffer clipboardOffer = new ClipboardOffer();
  private boolean inputViewActive;
  private long lastSpace;
  private static final long DOUBLE_SPACE_MS = 700;
  private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::clipChanged;
  private Dictation dictation;
  private final java.util.function.Consumer<Boolean> blurListener = enabled -> applyWindowTheme();
  // Decided when dictation starts: the space and capital the recognized text needs here.
  private String dictationSpace = "", dictationPartial = "";
  private boolean dictationCaps;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener =
      (p, k) -> {
        if (!"emoji_recent".equals(k) && !"emoji_category".equals(k) && root != null)
          root.post(
              () -> {
                if (!settings.enabled("dictation", true)
                    || !settings.enabled(MicPermissionActivity.CONSENT_KEY, false))
                  endDictation();
                if (k == null || "clipboard_suggest".equals(k)) clipChanged();
                applyWindowTheme();
                if (composition != null) {
                  composition.finish();
                  composition.reset(correctionAllowed(getCurrentInputEditorInfo()));
                }
                if (!searching) showLetters(false);
              });
      };

  @Override
  public void onCreate() {
    super.onCreate();
    settings = new KeyboardSettings(this);
    correction = new CorrectionManager(this);
    composition =
        new CompositionSession(
            new CompositionSession.Editor() {
              public boolean compose(String value) {
                InputConnection ic = getCurrentInputConnection();
                return ic != null && ic.setComposingText(value, 1);
              }

              public void commit(String value) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(value, 1);
              }

              public void finish() {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.finishComposingText();
              }

              public String before(int length) {
                InputConnection ic = getCurrentInputConnection();
                CharSequence value = ic == null ? null : ic.getTextBeforeCursor(length, 0);
                return value == null ? "" : value.toString();
              }

              public int cursor() {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return -1;
                SurroundingText text = ic.getSurroundingText(0, 0, 0);
                return text == null || text.getOffset() < 0
                    ? -1
                    : text.getOffset() + text.getSelectionEnd();
              }

              public boolean deleteBefore(int length) {
                InputConnection ic = getCurrentInputConnection();
                return ic != null && ic.deleteSurroundingText(length, 0);
              }
            },
            (word, previous, x, y, callback) -> {
              if (keyboard == null) return;
              correction.request(
                  settings.language(),
                  settings.layout(),
                  keyboard.correctionLayout(),
                  word,
                  previous,
                  x,
                  y,
                  callback);
            });
    composition.setListener(
        value -> {
          shown = value;
          refreshStrip();
        });
    clipboard = getSystemService(ClipboardManager.class);
    if (clipboard != null) clipboard.addPrimaryClipChangedListener(clipListener);
    dictation =
        new Dictation(
            this,
            new Dictation.Listener() {
              public void status(String value) {
                showDictation(value);
              }

              public void partial(String text) {
                dictationPartial = text;
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.setComposingText(dictated(text), 1);
              }

              public void done(String text) {
                commitDictation(text.isBlank() ? dictationPartial : text);
              }

              public void failed(int error) {
                commitDictation(dictationPartial);
                dictationError(error);
              }
            });
    settings.prefs.registerOnSharedPreferenceChangeListener(listener);
    WindowManager wm = getSystemService(WindowManager.class);
    if (wm != null) wm.addCrossWindowBlurEnabledListener(getMainExecutor(), blurListener);
  }

  @Override
  public void onDestroy() {
    inputViewActive = false;
    WindowManager wm = getSystemService(WindowManager.class);
    if (wm != null) wm.removeCrossWindowBlurEnabledListener(blurListener);
    if (dictation != null) dictation.cancel();
    clipboardOffer.clear();
    if (clipboard != null) clipboard.removePrimaryClipChangedListener(clipListener);
    settings.prefs.unregisterOnSharedPreferenceChangeListener(listener);
    if (correction != null) correction.close();
    super.onDestroy();
  }

  @Override
  public boolean onEvaluateInputViewShown() {
    super.onEvaluateInputViewShown();
    return true;
  }

  @Override
  public boolean onShowInputRequested(int flags, boolean configChange) {
    super.onShowInputRequested(flags, configChange);
    return true;
  }

  @Override
  public boolean onEvaluateFullscreenMode() {
    return false;
  }

  @Override
  public View onCreateInputView() {
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.TRANSPARENT);
    applyWindowTheme();
    showLetters(true);
    return root;
  }

  @Override
  public void onStartInput(EditorInfo info, boolean restarting) {
    super.onStartInput(info, restarting);
    endDictation();
    query = "";
    searching = false;
    popupVisible = false;
    sensitive = isSensitive(info);
    clipboardOffer.clear();
    composition.reset(correctionAllowed(info));
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting) {
    super.onStartInputView(info, restarting);
    inputViewActive = true;
    applyWindowTheme();
    if (root != null) showLetters(true);
    clipChanged();
  }

  private boolean stripEnabled() {
    return settings.enabled("suggestions", true);
  }

  private boolean privateEditor(EditorInfo info) {
    return info == null || isSensitive(info)
        || (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
  }

  private boolean clipboardAllowed() {
    return inputViewActive && !searching && !privateEditor(getCurrentInputEditorInfo())
        && settings.enabled("clipboard_suggest", false);
  }

  private String readClipboardText() {
    if (clipboard == null) return null;
    try {
      ClipData clip = clipboard.getPrimaryClip();
      if (clip == null || clip.getItemCount() == 0) return null;
      ClipDescription description = clip.getDescription();
      if (description.getExtras() != null
          && description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)) return null;
      CharSequence value = clip.getItemAt(0).getText();
      return value == null ? null : value.toString();
    } catch (SecurityException denied) {
      return null;
    }
  }

  private void clipChanged() {
    clipboardOffer.refresh(clipboardAllowed(), SystemClock.elapsedRealtime(), this::readClipboardText);
    refreshStrip();
  }

  private void refreshStrip() {
    if (keyboard == null) return;
    String preview = clipboardOffer.preview(clipboardAllowed(), SystemClock.elapsedRealtime());
    boolean privateField = !inputViewActive || privateEditor(getCurrentInputEditorInfo());
    InputConnection ic = privateField ? null : getCurrentInputConnection();
    CharSequence before = ic == null ? null : ic.getTextBeforeCursor(1, 0);
    CharSequence after = ic == null ? null : ic.getTextAfterCursor(1, 0);
    keyboard.setEditorHasText(
        (before != null && before.length() > 0) || (after != null && after.length() > 0));
    keyboard.showSuggestions(privateField ? CompositionSession.Suggestions.NONE : shown, preview);
    applyHeight();
  }

  /** Resizes the window when the strip or the accent popup room appears or goes away. */
  private void applyHeight() {
    if (keyboard == null || keyboard.getParent() != root) return;
    int base = boardHeight();
    android.view.ViewGroup.LayoutParams lp = keyboard.getLayoutParams();
    if (lp.height != base + extraHeight(base)) {
      lp.height = base + extraHeight(base);
      keyboard.setLayoutParams(lp);
    }
  }

  /**
   * The accent popup room above the keys is left out of the content and touch insets, so the app
   * neither jumps when a popup opens nor loses touches there.
   */
  @Override
  public void onComputeInsets(Insets insets) {
    super.onComputeInsets(insets);
    if (keyboard == null || keyboard.getParent() != root || searching) return;
    int board = boardHeight(), above = keyboard.getLayoutParams().height - board;
    int hidden = above - (keyboard.stripVisible() ? Math.round(board * 96f / 906f) : 0);
    if (hidden <= 0) return;
    insets.contentTopInsets += hidden;
    insets.visibleTopInsets += hidden;
    insets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE;
  }

  private void updateShift() {
    if (keyboard == null || searching) return;
    keyboard.autoShift(capitalsAtCursor());
  }

  private boolean capitalsAtCursor() {
    EditorInfo info = getCurrentInputEditorInfo();
    InputConnection ic = getCurrentInputConnection();
    return !sensitive
        && !searching
        && settings.enabled("auto_caps", true)
        && ic != null
        && info != null
        && ic.getCursorCapsMode(info.inputType) != 0;
  }

  @Override
  public void onFinishInputView(boolean finishing) {
    inputViewActive = false;
    clipboardOffer.clear();
    endDictation();
    if (composition != null) composition.finish();
    searching = false;
    query = "";
    if (keyboard != null) {
      keyboard.startEditor(false, false, true, "");
      keyboard.showSuggestions(CompositionSession.Suggestions.NONE, null);
    }
    super.onFinishInputView(finishing);
  }

  /** Whether words are composed for the strip; autocorrection is a separate opt-in on top. */
  private boolean correctionAllowed(EditorInfo info) {
    composition.setAutocorrect(settings.enabled("autocorrect", true));
    if (info == null
        || searching
        || isSensitive(info)
        || !(settings.enabled("autocorrect", true) || stripEnabled())) return false;
    if ((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return false;
    if ((info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT
        || (info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false;
    int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
    return variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        && variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        && variation != InputType.TYPE_TEXT_VARIATION_URI;
  }

  @Override
  public void onUpdateSelection(
      int oldStart, int oldEnd, int newStart, int newEnd, int candidatesStart, int candidatesEnd) {
    super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
    if (composition != null) composition.selectionChanged(newStart, newEnd, candidatesEnd);
    updateShift();
    refreshStrip();
  }

  @Override
  public void key(String value, int x, int y) {
    endDictation();
    if (searching) {
      text(value);
      return;
    }
    if (value.equals(" ") && doubleSpacePeriod()) return;
    composition.type(value, x, y);
  }

  /** iOS shortcut: a quick second space after a word becomes ". ". */
  private boolean doubleSpacePeriod() {
    long now = SystemClock.uptimeMillis(), previous = lastSpace;
    lastSpace = now;
    if (sensitive || !settings.enabled("double_space", true) || now - previous > DOUBLE_SPACE_MS)
      return false;
    InputConnection ic = getCurrentInputConnection();
    CharSequence before = ic == null ? null : ic.getTextBeforeCursor(2, 0);
    if (before == null
        || before.length() < 2
        || before.charAt(1) != ' '
        || !Character.isLetterOrDigit(before.charAt(0))) return false;
    composition.finish();
    ic.deleteSurroundingText(1, 0);
    ic.commitText(". ", 1);
    lastSpace = 0;
    return true;
  }

  private boolean isSensitive(EditorInfo info) {
    int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
    return (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER
        ? variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        : variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
  }

  /**
   * Frosted glass: the window blurs only what lies behind its own rounded bounds (FLAG_BLUR_BEHIND
   * would blur the whole app). Off while an accent popup enlarges the window, since the room above
   * the board would show as a blurred band, and when the system disables cross-window blur.
   */
  private void applyWindowTheme() {
    if (getWindow() == null || getWindow().getWindow() == null) return;
    Window window = getWindow().getWindow();
    WindowManager.LayoutParams attrs = window.getAttributes();
    attrs.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
    attrs.setBlurBehindRadius(0);
    window.setAttributes(attrs);
    WindowManager wm = getSystemService(WindowManager.class);
    boolean blur = wm != null && wm.isCrossWindowBlurEnabled() && !popupVisible;
    android.graphics.drawable.GradientDrawable shape =
        new android.graphics.drawable.GradientDrawable();
    shape.setColor(Color.TRANSPARENT);
    shape.setCornerRadius(width() * 96f / 1179f);
    window.setBackgroundDrawable(shape);
    float density = getResources().getDisplayMetrics().density;
    window.setBackgroundBlurRadius(blur ? Math.round(36 * density) : 0);
    if (keyboard != null) keyboard.setBlurred(blur);
  }

  private int width() {
    return getResources().getDisplayMetrics().widthPixels;
  }

  private int boardHeight() {
    int wanted = Math.round(width() * 906f / 1179f * settings.number("height", 100) / 100f);
    return Math.min(wanted, Math.round(getResources().getDisplayMetrics().heightPixels * 0.44f));
  }

  @Override
  public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
    super.onCurrentInputMethodSubtypeChanged(subtype);
    Languages.Language language =
        subtype == null ? null : Languages.forLocale(subtype.getLanguageTag());
    if (language != null) settings.prefs.edit().putString("language", language.code()).apply();
  }

  private String action() {
    if (searching) return "Keresés";
    EditorInfo info = getCurrentInputEditorInfo();
    if (info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "";
    switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
      case EditorInfo.IME_ACTION_SEARCH:
        return "Keresés";
      case EditorInfo.IME_ACTION_NEXT:
        return "Tovább";
      case EditorInfo.IME_ACTION_DONE:
        return "Kész";
      case EditorInfo.IME_ACTION_SEND:
        return "Küldés";
      case EditorInfo.IME_ACTION_GO:
        return "Ugrás";
      default:
        return "";
    }
  }

  private void showLetters(boolean reset) {
    if (root == null) return;
    root.removeAllViews();
    if (searching) {
      queryLabel = new TextView(this);
      queryLabel.setText("Emoji keresése: " + query + "   ·   Kész: keresés");
      queryLabel.setTextSize(16);
      queryLabel.setTextColor(Color.BLACK);
      queryLabel.setPadding(20, 12, 20, 12);
      queryLabel.setBackgroundColor(0xffeff0f4);
      queryLabel.setOnClickListener(v -> showEmoji());
      root.addView(queryLabel);
    }
    if (keyboard == null) {
      keyboard = new KeyboardProofView(this);
      keyboard.setOutput(this);
      applyWindowTheme();
    } else if (keyboard.getParent() != null)
      ((android.view.ViewGroup) keyboard.getParent()).removeView(keyboard);
    int height = boardHeight();
    keyboard.setImeBoardPixels(height);
    keyboard.setSuggestionStrip(stripEnabled());
    root.addView(keyboard, new LinearLayout.LayoutParams(-1, height + extraHeight(height)));
    // Height scaling is expressed by the renderer relative to actual width, including landscape.
    if (reset) {
      EditorInfo info = getCurrentInputEditorInfo();
      int cls = info == null ? 1 : info.inputType & InputType.TYPE_MASK_CLASS;
      boolean numbers =
          !searching
              && (cls == InputType.TYPE_CLASS_NUMBER
                  || cls == InputType.TYPE_CLASS_PHONE
                  || cls == InputType.TYPE_CLASS_DATETIME);
      keyboard.startEditor(capitalsAtCursor(), numbers, privateEditor(info), action());
      refreshStrip();
    }
  }

  /** Strip height, plus what an open accent popup needs above it (popup top is 138 above). */
  private int extraHeight(int board) {
    int strip = keyboard != null && keyboard.stripVisible() ? 96 : 0;
    return Math.round(board * (popupVisible ? Math.max(strip, 138) : strip) / 906f);
  }

  @Override
  public void text(String value) {
    endDictation();
    if (searching) {
      query += value;
      queryLabel.setText("Emoji keresése: " + query + "   ·   Kész: keresés");
      return;
    }
    composition.finish();
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) ic.commitText(value, 1);
  }

  @Override
  public void delete() {
    endDictation();
    if (searching) {
      query = removeLast(query);
      queryLabel.setText("Emoji keresése: " + query + "   ·   Kész: keresés");
      return;
    }
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    CharSequence selected = ic.getSelectedText(0);
    if (selected != null && selected.length() > 0) {
      composition.finish();
      ic.commitText("", 1);
      return;
    }
    if (!sensitive && composition.delete()) return;
    if (sensitive) {
      ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
      ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      return;
    }
    CharSequence before = ic.getTextBeforeCursor(512, 0);
    if (before == null || before.length() == 0) {
      ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
      ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      return;
    }
    String value = before.toString();
    String shortened = removeLast(value);
    ic.deleteSurroundingText(value.length() - shortened.length(), 0);
  }

  @Override
  public void deleteWord() {
    endDictation();
    InputConnection ic = getCurrentInputConnection();
    if (searching || ic == null) {
      delete();
      return;
    }
    composition.finish();
    CharSequence selected = ic.getSelectedText(0);
    CharSequence before = ic.getTextBeforeCursor(64, 0);
    if ((selected != null && selected.length() > 0) || before == null || before.length() == 0) {
      delete();
      return;
    }
    String value = before.toString();
    int end = value.length();
    while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) end--;
    int start = end;
    while (start > 0 && Character.isLetterOrDigit(value.codePointBefore(start)))
      start = value.offsetByCodePoints(start, -1);
    if (start == end) start = removeLast(value.substring(0, end)).length();
    ic.deleteSurroundingText(value.length() - start, 0);
  }

  @Override
  public void shiftCheck() {
    updateShift();
  }

  @Override
  public void pick(String value) {
    endDictation();
    if (searching) return;
    if (value != null) {
      composition.pick(value);
      return;
    }
    InputConnection ic = getCurrentInputConnection();
    String clip = clipboardOffer.take(clipboardAllowed(), SystemClock.elapsedRealtime());
    if (clip == null || ic == null) return;
    composition.finish();
    ic.commitText(clip, 1);
    refreshStrip();
  }

  private static String removeLast(String value) {
    if (value.isEmpty()) return value;
    Matcher matcher = Pattern.compile("\\X").matcher(value);
    int start = 0;
    while (matcher.find()) start = matcher.start();
    return value.substring(0, start);
  }

  @Override
  public void enter() {
    endDictation();
    if (searching) {
      showEmoji();
      return;
    }
    composition.finish();
    InputConnection ic = getCurrentInputConnection();
    EditorInfo info = getCurrentInputEditorInfo();
    if (ic == null || info == null) return;
    int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
    if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        && action != EditorInfo.IME_ACTION_NONE
        && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
      ic.performEditorAction(action);
    } else ic.commitText("\n", 1);
  }

  @Override
  public void emoji() {
    endDictation();
    if (!sensitive) {
      composition.finish();
      query = "";
      showEmoji();
    }
  }

  private void showEmoji() {
    searching = false;
    popupVisible = false;
    root.removeAllViews();
    EmbeddedEmojiView panel =
        new EmbeddedEmojiView(
            this,
            settings,
            query,
            value -> {
              InputConnection ic = getCurrentInputConnection();
              if (ic != null) ic.commitText(value, 1);
              if (settings.enabled("emoji_return", false)) {
                query = "";
                showLetters(true);
              }
            },
            this::delete,
            () -> {
              query = "";
              searching = false;
              composition.reset(correctionAllowed(getCurrentInputEditorInfo()));
              showLetters(true);
            },
            () -> {
              composition.finish();
              composition.reset(false);
              searching = true;
              showLetters(true);
            },
            () -> {
              query = "";
              composition.reset(correctionAllowed(getCurrentInputEditorInfo()));
              showLetters(true);
              dictate();
            });
    int height =
        Math.min(
            Math.round(width() * 1164f / 1179f),
            Math.round(getResources().getDisplayMetrics().heightPixels * 0.49f));
    root.addView(panel, new LinearLayout.LayoutParams(-1, height));
  }

  @Override
  public void dictate() {
    if (dictation.active()) {
      dictation.stop();
      return;
    }
    if (sensitive || searching) {
      Toast.makeText(this, "Ebben a mezőben nincs diktálás", Toast.LENGTH_SHORT).show();
      return;
    }
    if (!settings.enabled("dictation", true)) {
      Toast.makeText(this, "A diktálás ki van kapcsolva a beállításokban", Toast.LENGTH_SHORT)
          .show();
      return;
    }
    if (!settings.enabled(MicPermissionActivity.CONSENT_KEY, false)
        || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      askMicrophone();
      return;
    }
    composition.finish();
    InputConnection ic = getCurrentInputConnection();
    CharSequence before = ic == null ? null : ic.getTextBeforeCursor(1, 0);
    dictationSpace =
        before != null && before.length() > 0 && !Character.isWhitespace(before.charAt(0))
            ? " "
            : "";
    dictationCaps = capitalsAtCursor();
    dictationPartial = "";
    if (!dictation.start(settings.current().speech()))
      Toast.makeText(this, "Nincs telepítve Google beszédfelismerő", Toast.LENGTH_LONG).show();
  }

  private void askMicrophone() {
    startActivity(
        new Intent(this, MicPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  private String dictated(String text) {
    String value = text.strip();
    if (value.isEmpty()) return "";
    if (dictationCaps)
      value =
          new String(Character.toChars(Character.toUpperCase(value.codePointAt(0))))
              + value.substring(Character.charCount(value.codePointAt(0)));
    return dictationSpace + value;
  }

  private void commitDictation(String text) {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      String value = dictated(text);
      if (value.isEmpty()) ic.finishComposingText();
      else ic.commitText(value, 1);
    }
    dictationPartial = "";
    showDictation(null);
    updateShift();
  }

  /** Typing, leaving the field or closing the keyboard keeps what was heard so far. */
  private void endDictation() {
    if (dictation == null || !dictation.active()) return;
    dictation.cancel();
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) ic.finishComposingText();
    dictationPartial = "";
    showDictation(null);
  }

  private void showDictation(String status) {
    if (keyboard == null) return;
    keyboard.setDictation(status);
    applyHeight();
  }

  private void dictationError(int error) {
    String message;
    switch (error) {
      case SpeechRecognizer.ERROR_NO_MATCH:
      case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
        message = "Nem hallottam semmit";
        break;
      case SpeechRecognizer.ERROR_NETWORK:
      case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
      case SpeechRecognizer.ERROR_SERVER:
      case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
        message = "Nincs kapcsolat a Google beszédfelismerővel";
        break;
      case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
        askMicrophone();
        return;
      case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
        message = "A beszédfelismerő foglalt, próbáld újra";
        break;
      case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
      case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
        message = "Ez a nyelv most nem érhető el a diktáláshoz";
        break;
      case SpeechRecognizer.ERROR_CLIENT:
        return;
      default:
        message = "Diktálási hiba (" + error + ")";
    }
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void settings() {
    Intent i = new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(i);
  }

  @Override
  public void next() {
    List<String> enabled = settings.languages();
    if (enabled.size() == 1) {
      switchToNextInputMethod(false);
      return;
    }
    String language = enabled.get((enabled.indexOf(settings.language()) + 1) % enabled.size());
    settings.prefs.edit().putString("language", language).apply();
    Toast.makeText(this, Languages.get(language).name(), Toast.LENGTH_SHORT).show();
  }

  @Override
  public void popup(boolean shown) {
    popupVisible = shown;
    applyWindowTheme();
    if (keyboard != null && keyboard.getParent() == root) {
      applyHeight();
    }
  }

  @Override
  public void cursor(int direction) {
    endDictation();
    if (searching) return;
    composition.finish();
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      int key = direction > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
      ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
      ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
    }
  }
}
