// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.os.Handler;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Adapts and plumbs android IME service onto the chrome text input API.
 * ImeAdapter provides an interface in both ways native <-> java:
 * 1. InputConnectionAdapter notifies native code of text composition state and
 *    dispatch key events from java -> WebKit.
 * 2. Native ImeAdapter notifies java side to clear composition text.
 *
 * The basic flow is:
 * 1. When InputConnectionAdapter gets called with composition or result text:
 *    If we receive a composition text or a result text, then we just need to
 *    dispatch a synthetic key event with special keycode 229, and then dispatch
 *    the composition or result text.
 * 2. Intercept dispatchKeyEvent() method for key events not handled by IME, we
 *   need to dispatch them to webkit and check webkit's reply. Then inject a
 *   new key event for further processing if webkit didn't handle it.
 *
 * Note that the native peer object does not take any strong reference onto the
 * instance of this java object, hence it is up to the client of this class (e.g.
 * the ViewEmbedder implementor) to hold a strong reference to it for the required
 * lifetime of the object.
 */
@JNINamespace("content")
public class ImeAdapter {
    /**
     * Interface for the delegate that needs to be notified of IME changes.
     */
    public interface ImeAdapterDelegate {
        /**
         * @param isFinish whether the event is occurring because input is finished.
         */
        void onImeEvent(boolean isFinish);
        void onSetFieldValue();
        void onDismissInput();
        View getAttachedView();
        ResultReceiver getNewShowKeyboardReceiver();
    }

    protected class DelayedDismissInput implements Runnable {
        private final long mNativeImeAdapter;

        public DelayedDismissInput(long nativeImeAdapter) {
            mNativeImeAdapter = nativeImeAdapter;
        }

        @Override
        public void run() {
            Log.d(TAG, "ImeAdapter::DelayedDismissInput run() mNativeImeAdapterAndroid = " + mNativeImeAdapterAndroid);
            if (mNativeImeAdapterAndroid == 0) {
                return;
            }
            attach(mNativeImeAdapter, sTextInputTypeNone, AdapterInputConnection.INVALID_SELECTION,
                    AdapterInputConnection.INVALID_SELECTION);
            dismissInput(true);
        }
    }

    private static final int COMPOSITION_KEY_CODE = 229;

    // Delay introduced to avoid hiding the keyboard if new show requests are received.
    // The time required by the unfocus-focus events triggered by tab has been measured in soju:
    // Mean: 18.633 ms, Standard deviation: 7.9837 ms.
    // The value here should be higher enough to cover these cases, but not too high to avoid
    // letting the user perceiving important delays.
    protected static final int INPUT_DISMISS_DELAY = 150;

    // All the constants that are retrieved from the C++ code.
    // They get set through initializeWebInputEvents and initializeTextInputTypes calls.
    static int sEventTypeRawKeyDown;
    static int sEventTypeKeyUp;
    static int sEventTypeChar;
    protected static int sTextInputTypeNone;
    static int sTextInputTypeText;
    protected static int sTextInputTypeTextArea;
    protected static int sTextInputTypePassword;
    protected static int sTextInputTypeSearch;
    static int sTextInputTypeUrl;
    static int sTextInputTypeEmail;
    static int sTextInputTypeTel;
    protected static int sTextInputTypeNumber;
    protected static int sTextInputTypeContentEditable;
    static int sModifierShift;
    static int sModifierAlt;
    static int sModifierCtrl;
    static int sModifierCapsLockOn;
    static int sModifierNumLockOn;
    static char[] sSingleCharArray = new char[1];
    static KeyCharacterMap sKeyCharacterMap;

    protected long mNativeImeAdapterAndroid;
    protected InputMethodManagerWrapper mInputMethodManagerWrapper;
    protected AdapterInputConnection mInputConnection;
    protected final ImeAdapterDelegate mViewEmbedder;
    protected final Handler mHandler;
    protected DelayedDismissInput mDismissInput = null;
    protected int mTextInputType;
    private int mInitialSelectionStart;
    private int mInitialSelectionEnd;
    private static final String TAG = ImeAdapter.class.getSimpleName();
    private String mLastComposeText;

    @VisibleForTesting
    int mLastSyntheticKeyCode;

    @VisibleForTesting
    protected boolean mIsShowWithoutHideOutstanding = false;

    /**
     * @param wrapper InputMethodManagerWrapper that should receive all the call directed to
     *                InputMethodManager.
     * @param embedder The view that is used for callbacks from ImeAdapter.
     */
    public ImeAdapter(InputMethodManagerWrapper wrapper, ImeAdapterDelegate embedder) {
        mInputMethodManagerWrapper = wrapper;
        mViewEmbedder = embedder;
        mHandler = new Handler();
    }

    /**
     * Default factory for AdapterInputConnection classes.
     */
    public static class AdapterInputConnectionFactory {
        public AdapterInputConnection get(View view, ImeAdapter imeAdapter, EditorInfo outAttrs) {
            return new AdapterInputConnection(view, imeAdapter, outAttrs);
        }
    }

    @VisibleForTesting
    public void setInputMethodManagerWrapper(InputMethodManagerWrapper immw) {
        mInputMethodManagerWrapper = immw;
    }

    /**
     * Should be only used by AdapterInputConnection.
     * @return InputMethodManagerWrapper that should receive all the calls directed to
     *         InputMethodManager.
     */
    InputMethodManagerWrapper getInputMethodManagerWrapper() {
        return mInputMethodManagerWrapper;
    }

    /**
     * Set the current active InputConnection when a new InputConnection is constructed.
     * @param inputConnection The input connection that is currently used with IME.
     */
    void setInputConnection(AdapterInputConnection inputConnection) {
        mInputConnection = inputConnection;
        mLastComposeText = null;
    }

    /**
     * Should be only used by AdapterInputConnection.
     * @return The input type of currently focused element.
     */
    public int getTextInputType() {
        return mTextInputType;
    }

    /**
     * Should be only used by AdapterInputConnection.
     * @return The starting index of the initial text selection.
     */
    int getInitialSelectionStart() {
        return mInitialSelectionStart;
    }

    /**
     * Should be only used by AdapterInputConnection.
     * @return The ending index of the initial text selection.
     */
    int getInitialSelectionEnd() {
        return mInitialSelectionEnd;
    }

    public static int getTextInputTypeNone() {
        return sTextInputTypeNone;
    }

    private static int getModifiers(int metaState) {
        int modifiers = 0;
        if ((metaState & KeyEvent.META_SHIFT_ON) != 0) {
            modifiers |= sModifierShift;
        }
        if ((metaState & KeyEvent.META_ALT_ON) != 0) {
            modifiers |= sModifierAlt;
        }
        if ((metaState & KeyEvent.META_CTRL_ON) != 0) {
            modifiers |= sModifierCtrl;
        }
        if ((metaState & KeyEvent.META_CAPS_LOCK_ON) != 0) {
            modifiers |= sModifierCapsLockOn;
        }
        if ((metaState & KeyEvent.META_NUM_LOCK_ON) != 0) {
            modifiers |= sModifierNumLockOn;
        }
        return modifiers;
    }

    public boolean isActive() {
        return mInputConnection != null && mInputConnection.isActive();
    }

    protected boolean isFor(int nativeImeAdapter, int textInputType) {
        return mNativeImeAdapterAndroid == nativeImeAdapter &&
               mTextInputType == textInputType;
    }

    public void attachAndShowIfNeeded(int nativeImeAdapter, int textInputType,
            int selectionStart, int selectionEnd, boolean showIfNeeded) {
        mHandler.removeCallbacks(mDismissInput);

        // If current input type is none and showIfNeeded is false, IME should not be shown
        // and input type should remain as none.
        if (mTextInputType == sTextInputTypeNone && !showIfNeeded) {
            return;
        }

        if (!isFor(nativeImeAdapter, textInputType)) {
            // Set a delayed task to perform unfocus. This avoids hiding the keyboard when tabbing
            // through text inputs or when JS rapidly changes focus to another text element.
            if (textInputType == sTextInputTypeNone) {
                mDismissInput = new DelayedDismissInput(nativeImeAdapter);
                mHandler.postDelayed(mDismissInput, INPUT_DISMISS_DELAY);
                return;
            }

            attach(nativeImeAdapter, textInputType, selectionStart, selectionEnd);

            mInputMethodManagerWrapper.restartInput(mViewEmbedder.getAttachedView());
            if (showIfNeeded) {
                showKeyboard();
            }
        } else if (hasInputType() && showIfNeeded) {
            showKeyboard();
        }
    }

    public void attach(long nativeImeAdapter, int textInputType, int selectionStart,
            int selectionEnd) {
        Log.d(TAG, "ImeAdapter::attach nativeImeAdapter = " + nativeImeAdapter + ", textInputType = " +
                textInputType + ", Selection = (" + selectionStart + ", " + selectionEnd + ")");
        if (mNativeImeAdapterAndroid != 0) {
            nativeResetImeAdapter(mNativeImeAdapterAndroid);
        }
        mNativeImeAdapterAndroid = nativeImeAdapter;
        mTextInputType = textInputType;
        mInitialSelectionStart = selectionStart;
        mInitialSelectionEnd = selectionEnd;
        mLastComposeText = null;
        if (nativeImeAdapter != 0) {
            nativeAttachImeAdapter(mNativeImeAdapterAndroid);
        }
    }

    /**
     * Attaches the imeAdapter to its native counterpart. This is needed to start forwarding
     * keyboard events to WebKit.
     * @param nativeImeAdapter The pointer to the native ImeAdapter object.
     */
    public void attach(long nativeImeAdapter) {
        Log.d(TAG, "ImeAdapter::attach nativeImeAdapter = " + nativeImeAdapter);
        if (mNativeImeAdapterAndroid != 0) {
            nativeResetImeAdapter(mNativeImeAdapterAndroid);
        }
        mNativeImeAdapterAndroid = nativeImeAdapter;
        if (nativeImeAdapter != 0) {
            nativeAttachImeAdapter(mNativeImeAdapterAndroid);
        }
    }

    protected void showKeyboard() {
        mIsShowWithoutHideOutstanding = true;
        mInputMethodManagerWrapper.showSoftInput(mViewEmbedder.getAttachedView(), 0,
                mViewEmbedder.getNewShowKeyboardReceiver());
    }

    protected void dismissInput(boolean unzoomIfNeeded) {
        mIsShowWithoutHideOutstanding  = false;
        View view = mViewEmbedder.getAttachedView();
        if (mInputMethodManagerWrapper.isActive(view)) {
            mInputMethodManagerWrapper.hideSoftInputFromWindow(view.getWindowToken(), 0,
                    unzoomIfNeeded ? mViewEmbedder.getNewShowKeyboardReceiver() : null);
        }
        mViewEmbedder.onDismissInput();
    }

    protected boolean hasInputType() {
        return mTextInputType != sTextInputTypeNone;
    }

    private static boolean isTextInputType(int type) {
        return type != sTextInputTypeNone && !InputDialogContainer.isDialogInputType(type);
    }

    public boolean hasTextInputType() {
        return isTextInputType(mTextInputType);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        return translateAndSendNativeEvents(event);
    }

    protected boolean shouldEnableToDeferLoading(){
        return false;
    }

    protected void deferringEventRequired(String text){
        return;
    }

    protected void enableDefersLoadingFlag(boolean enable) {
        return;
    }

    private int shouldSendKeyEventWithKeyCode(String text) {
        if (text.length() != 1) return COMPOSITION_KEY_CODE;

        if (text.equals("\n")) return KeyEvent.KEYCODE_ENTER;
        else if (text.equals("\t")) return KeyEvent.KEYCODE_TAB;
        else return COMPOSITION_KEY_CODE;
    }

    /**
     * @return Android KeyEvent for a single unicode character.  Only one KeyEvent is returned
     * even if the system determined that various modifier keys (like Shift) would also have
     * been pressed.
     */
    private static KeyEvent androidKeyEventForCharacter(char chr) {
        if (sKeyCharacterMap == null) {
            sKeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        }
        sSingleCharArray[0] = chr;
        // TODO: Evaluate cost of this system call.
        KeyEvent[] events = sKeyCharacterMap.getEvents(sSingleCharArray);
        if (events == null) {  // No known key sequence will create that character.
            return null;
        }

        for (int i = 0; i < events.length; ++i) {
            if (events[i].getAction() == KeyEvent.ACTION_DOWN &&
                    !KeyEvent.isModifierKey(events[i].getKeyCode())) {
                return events[i];
            }
        }

        return null;  // No printing characters were found.
    }

    @VisibleForTesting
    public static KeyEvent getTypedKeyEventGuess(String oldtext, String newtext) {
        // Starting typing a new composition should add only a single character.  Any composition
        // beginning with text longer than that must come from something other than typing so
        // return 0.
        if (oldtext == null) {
            if (newtext.length() == 1) {
                return androidKeyEventForCharacter(newtext.charAt(0));
            } else {
                return null;
            }
        }

        // The content has grown in length: assume the last character is the key that caused it.
        if (newtext.length() > oldtext.length() && newtext.startsWith(oldtext))
            return androidKeyEventForCharacter(newtext.charAt(newtext.length() - 1));

        // The content has shrunk in length: assume that backspace was pressed.
        if (oldtext.length() > newtext.length() && oldtext.startsWith(newtext))
            return new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);

        // The content is unchanged or has undergone a complex change (i.e. not a simple tail
        // modification) so return an unknown key-code.
        return null;
    }

    void sendKeyEventWithKeyCode(int keyCode, int flags) {
        long eventTime = System.currentTimeMillis();
        mLastSyntheticKeyCode = keyCode;
        translateAndSendNativeEvents(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags));
        translateAndSendNativeEvents(new KeyEvent(System.currentTimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags));
    }

    // Calls from Java to C++

    boolean checkCompositionQueueAndCallNative(CharSequence text, int newCursorPosition,
            boolean isCommit) {
        if (mNativeImeAdapterAndroid == 0) return false;

        String textStr = text.toString();
        
        // Committing an empty string finishes the current composition.
        boolean isFinish = textStr.isEmpty();
        mViewEmbedder.onImeEvent(isFinish);
        int keyCode = shouldSendKeyEventWithKeyCode(textStr);
        long timeStampMs = SystemClock.uptimeMillis();

        if (keyCode != COMPOSITION_KEY_CODE) {
            sendKeyEventWithKeyCode(keyCode,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
        } else {
            KeyEvent keyEvent = getTypedKeyEventGuess(mLastComposeText, textStr);
            int modifiers = 0;
            if (keyEvent != null) {
                keyCode = keyEvent.getKeyCode();
                modifiers = getModifiers(keyEvent.getMetaState());
            } else if (!textStr.equals(mLastComposeText)) {
                keyCode = KeyEvent.KEYCODE_UNKNOWN;
            } else {
                keyCode = -1;
            }

            // If this is a commit with no previous composition, then treat it as a native
            // KeyDown/KeyUp pair with no composition rather than a synthetic pair with
            // composition below.
            if (keyCode > 0 && isCommit && mLastComposeText == null) {
                mLastSyntheticKeyCode = keyCode;
                return translateAndSendNativeEvents(keyEvent) &&
                       translateAndSendNativeEvents(KeyEvent.changeAction(
                               keyEvent, KeyEvent.ACTION_UP));
            }

            // When typing, there is no issue sending KeyDown and KeyUp events around the
            // composition event because those key events do nothing (other than call JS
            // handlers).  Typing does not cause changes outside of a KeyPress event which
            // we don't call here.  However, if the key-code is a control key such as
            // KEYCODE_DEL then there never is an associated KeyPress event and the KeyDown
            // event itself causes the action.  The net result below is that the Renderer calls
            // cancelComposition() and then Android starts anew with setComposingRegion().
            // This stopping and restarting of composition could be a source of problems
            // with 3rd party keyboards.
            //
            // An alternative is to *not* call nativeSetComposingText() in the non-commit case
            // below.  This avoids the restart of composition described above but fails to send
            // an update to the composition while in composition which, strictly speaking,
            // does not match the spec.
            //
            // For now, the solution is to endure the restarting of composition and only dive
            // into the alternate solution should there be problems in the field.  --bcwhite

            if (keyCode >= 0) {
                nativeSendSyntheticKeyEvent(mNativeImeAdapterAndroid, sEventTypeRawKeyDown,
                        timeStampMs, keyCode, modifiers, 0);
            }

            if (isCommit) {
                nativeCommitText(mNativeImeAdapterAndroid, textStr);
                textStr = null;
            } else {
                nativeSetComposingText(mNativeImeAdapterAndroid, textStr, newCursorPosition);
            }

            if (keyCode >= 0) {
                nativeSendSyntheticKeyEvent(mNativeImeAdapterAndroid, sEventTypeKeyUp,
                        timeStampMs, keyCode, modifiers, 0);
            }

            mLastSyntheticKeyCode = keyCode;
        }

        mLastComposeText = textStr;
        return true;
    }

    boolean checkCompositionQueueAndCallNativeWithSubComposingRegion(String text, int newCursorPosition,
            int newRegionStart, int newRegionEnd, int backgroundColor, boolean isCommit) {
        if (mNativeImeAdapterAndroid == 0) return false;

        // Committing an empty string finishes the current composition.
        boolean isFinish = text.isEmpty();
        mViewEmbedder.onImeEvent(isFinish);
        int keyCode = shouldSendKeyEventWithKeyCode(text);
        long timeStampMs = System.currentTimeMillis();

        if (keyCode != COMPOSITION_KEY_CODE) {
            sendKeyEventWithKeyCode(keyCode,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
        } else {
            nativeSendSyntheticKeyEvent(mNativeImeAdapterAndroid, sEventTypeRawKeyDown,
                    timeStampMs, 0, keyCode, 0);
            if (isCommit) {
                nativeCommitText(mNativeImeAdapterAndroid, text);
            } else {
                //nativeSetComposingText(mNativeImeAdapterAndroid, text, newCursorPosition); // Original code.
                nativeSetComposingTextWithSubComposingRegion(mNativeImeAdapterAndroid, text, newCursorPosition, newRegionStart, newRegionEnd, backgroundColor);
            }
            nativeSendSyntheticKeyEvent(mNativeImeAdapterAndroid, sEventTypeKeyUp,
                    timeStampMs, 0, keyCode, 0);
        }

        return true;
    }

    protected void finishComposingText() {
        mLastComposeText = null;
        if (mNativeImeAdapterAndroid == 0) return;
        nativeFinishComposingText(mNativeImeAdapterAndroid);
    }

    boolean translateAndSendNativeEvents(KeyEvent event) {
        if (mNativeImeAdapterAndroid == 0) return false;

        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN &&
            action != KeyEvent.ACTION_UP) {
            // action == KeyEvent.ACTION_MULTIPLE
            // TODO(bulach): confirm the actual behavior. Apparently:
            // If event.getKeyCode() == KEYCODE_UNKNOWN, we can send a
            // composition key down (229) followed by a commit text with the
            // string from event.getUnicodeChars().
            // Otherwise, we'd need to send an event with a
            // WebInputEvent::IsAutoRepeat modifier. We also need to verify when
            // we receive ACTION_MULTIPLE: we may receive it after an ACTION_DOWN,
            // and if that's the case, we'll need to review when to send the Char
            // event.
            return false;
        }
        mViewEmbedder.onImeEvent(false);
        return nativeSendKeyEvent(mNativeImeAdapterAndroid, event, event.getAction(),
                getModifiers(event.getMetaState()), event.getEventTime(), event.getKeyCode(),
                                event.isSystem(), event.getUnicodeChar());
    }

    boolean sendSyntheticKeyEvent(int eventType, long timestampMs, int keyCode, int modifiers,
            int unicodeChar) {
        if (mNativeImeAdapterAndroid == 0) return false;

        nativeSendSyntheticKeyEvent(
                mNativeImeAdapterAndroid, eventType, timestampMs, keyCode, modifiers, unicodeChar);
        return true;
    }

    boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeDeleteSurroundingText(mNativeImeAdapterAndroid, beforeLength, afterLength);
        return true;
    }

    boolean setEditableSelectionOffsets(int start, int end) {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSetEditableSelectionOffsets(mNativeImeAdapterAndroid, start, end);
        return true;
    }

    /**
     * Send a request to the native counterpart to set compositing region to given indices.
     * @param start The start of the composition.
     * @param end The end of the composition.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    boolean setComposingRegion(CharSequence text, int start, int end) {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSetComposingRegion(mNativeImeAdapterAndroid, start, end);
        mLastComposeText = text != null ? text.toString() : null;
        return true;
    }

    /**
     * Send a request to the native counterpart to unselect text.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean unselect() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeUnselect(mNativeImeAdapterAndroid);
        return true;
    }

    /**
     * Send a request to the native counterpart of ImeAdapter to select all the text.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean selectAll() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeSelectAll(mNativeImeAdapterAndroid);
        return true;
    }

    /**
     * Send a request to the native counterpart of ImeAdapter to cut the selected text.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean cut() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeCut(mNativeImeAdapterAndroid);
        return true;
    }

    /**
     * Send a request to the native counterpart of ImeAdapter to copy the selected text.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean copy() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativeCopy(mNativeImeAdapterAndroid);
        return true;
    }

    /**
     * Send a request to the native counterpart of ImeAdapter to paste the text from the clipboard.
     * @return Whether the native counterpart of ImeAdapter received the call.
     */
    public boolean paste() {
        if (mNativeImeAdapterAndroid == 0) return false;
        nativePaste(mNativeImeAdapterAndroid);
        return true;
    }

    // Calls from C++ to Java

    @CalledByNative
    private static void initializeWebInputEvents(int eventTypeRawKeyDown, int eventTypeKeyUp,
            int eventTypeChar, int modifierShift, int modifierAlt, int modifierCtrl,
            int modifierCapsLockOn, int modifierNumLockOn) {
        sEventTypeRawKeyDown = eventTypeRawKeyDown;
        sEventTypeKeyUp = eventTypeKeyUp;
        sEventTypeChar = eventTypeChar;
        sModifierShift = modifierShift;
        sModifierAlt = modifierAlt;
        sModifierCtrl = modifierCtrl;
        sModifierCapsLockOn = modifierCapsLockOn;
        sModifierNumLockOn = modifierNumLockOn;
    }

    @CalledByNative
    private static void initializeTextInputTypes(int textInputTypeNone, int textInputTypeText,
            int textInputTypeTextArea, int textInputTypePassword, int textInputTypeSearch,
            int textInputTypeUrl, int textInputTypeEmail, int textInputTypeTel,
            int textInputTypeNumber, int textInputTypeContentEditable) {
        sTextInputTypeNone = textInputTypeNone;
        sTextInputTypeText = textInputTypeText;
        sTextInputTypeTextArea = textInputTypeTextArea;
        sTextInputTypePassword = textInputTypePassword;
        sTextInputTypeSearch = textInputTypeSearch;
        sTextInputTypeUrl = textInputTypeUrl;
        sTextInputTypeEmail = textInputTypeEmail;
        sTextInputTypeTel = textInputTypeTel;
        sTextInputTypeNumber = textInputTypeNumber;
        sTextInputTypeContentEditable = textInputTypeContentEditable;
    }

    @CalledByNative
    private void focusedNodeChanged(boolean isEditable,boolean isSelectable) {
        if (mInputConnection != null && isEditable) mInputConnection.restartInput();
    }

    @CalledByNative
    private void cancelComposition() {
        if (mInputConnection != null) mInputConnection.restartInput();
        mLastComposeText = null;
    }

    @CalledByNative
    void detach() {
        Log.d(TAG, "ImeAdapter Detach()");
        if (mDismissInput != null) mHandler.removeCallbacks(mDismissInput);
        mNativeImeAdapterAndroid = 0;
        mTextInputType = 0;
    }
    
//S_JPN_CEDS_0489
    public void RequestUpdateIME(boolean show_ime_if_needed, boolean send_ime_ack )
    {    
        if (mNativeImeAdapterAndroid == 0) return ;
        nativeRequestUpdateIME( mNativeImeAdapterAndroid, show_ime_if_needed, send_ime_ack);
    }

    private native boolean nativeSendSyntheticKeyEvent(long nativeImeAdapterAndroid,
            int eventType, long timestampMs, int keyCode, int modifiers, int unicodeChar);

    private native boolean nativeSendKeyEvent(long nativeImeAdapterAndroid, KeyEvent event,
            int action, int modifiers, long timestampMs, int keyCode, boolean isSystemKey,
            int unicodeChar);

    private native void nativeSetComposingText(long nativeImeAdapterAndroid, String text,
            int newCursorPosition);

    private native void nativeSetComposingTextWithSubComposingRegion(long nativeImeAdapterAndroid, String text,
            int newCursorPosition, int newRegionStart, int newRegionEnd, int backgroundColor);

    private native void nativeCommitText(long nativeImeAdapterAndroid, String text);

    private native void nativeFinishComposingText(long nativeImeAdapterAndroid);

    private native void nativeAttachImeAdapter(long nativeImeAdapterAndroid);

    private native void nativeSetEditableSelectionOffsets(long nativeImeAdapterAndroid,
            int start, int end);

    private native void nativeSetComposingRegion(long nativeImeAdapterAndroid, int start, int end);

    private native void nativeDeleteSurroundingText(long nativeImeAdapterAndroid,
            int before, int after);

    private native void nativeUnselect(long nativeImeAdapterAndroid);
    private native void nativeSelectAll(long nativeImeAdapterAndroid);
    private native void nativeCut(long nativeImeAdapterAndroid);
    private native void nativeCopy(long nativeImeAdapterAndroid);
    protected native void nativePaste(long nativeImeAdapterAndroid);
    protected native void nativeDirectPaste(long nativeImeAdapterAndroid, String text);
    private native void nativeResetImeAdapter(long nativeImeAdapterAndroid);
    protected native void nativeDefersLoading(long nativeImeAdapterAndroid, boolean defer);
    
//S_JPN_CEDS_0489
    protected native void nativeRequestUpdateIME( long nativeImeAdapterAndroid,boolean show_ime_if_needed, boolean send_ime_ack );
}
