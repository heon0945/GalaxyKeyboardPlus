package org.dslul.openboard.inputmethod.accessibility

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.util.SparseIntArray
import android.view.inputmethod.EditorInfo
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Keyboard
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.common.StringUtils
import java.util.*

internal class KeyCodeDescriptionMapper private constructor() {
    // Sparse array of spoken description resource IDs indexed by key codes
    private val mKeyCodeMap = SparseIntArray()

    /**
     * Returns the localized description of the action performed by a specified
     * key based on the current keyboard state.
     *
     * @param context The package's context.
     * @param keyboard The keyboard on which the key resides.
     * @param key The key from which to obtain a description.
     * @param shouldObscure {@true} if text (e.g. non-control) characters should be obscured.
     * @return a character sequence describing the action performed by pressing the key
     */
    fun getDescriptionForKey(context: Context, keyboard: Keyboard?,
                             key: Key, shouldObscure: Boolean): String? {
        val code = key.code
        if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
            val description = getDescriptionForSwitchAlphaSymbol(context, keyboard)
            if (description != null) {
                return description
            }
        }
        if (code == Constants.CODE_SHIFT) {
            return getDescriptionForShiftKey(context, keyboard)
        }
        if (code == Constants.CODE_ENTER) { // The following function returns the correct description in all action and
// regular enter cases, taking care of all modes.
            return getDescriptionForActionKey(context, keyboard, key)
        }
        if (code == Constants.CODE_OUTPUT_TEXT) {
            val outputText = key.outputText
            val description = getSpokenEmoticonDescription(context, outputText)
            return if (TextUtils.isEmpty(description)) outputText else description
        }
        // Just attempt to speak the description.
        if (code != Constants.CODE_UNSPECIFIED) { // If the key description should be obscured, now is the time to do it.
            val isDefinedNonCtrl = (Character.isDefined(code)
                    && !Character.isISOControl(code))
            if (shouldObscure && isDefinedNonCtrl) {
                return context.getString(OBSCURED_KEY_RES_ID)
            }
            val description = getDescriptionForCodePoint(context, code)
            if (description != null) {
                return description
            }
            return if (!TextUtils.isEmpty(key.label)) {
                key.label
            } else context.getString(R.string.spoken_description_unknown)
        }
        return null
    }

    /**
     * Returns a localized character sequence describing what will happen when
     * the specified key is pressed based on its key code point.
     *
     * @param context The package's context.
     * @param codePoint The code point from which to obtain a description.
     * @return a character sequence describing the code point.
     */
    fun getDescriptionForCodePoint(context: Context, codePoint: Int): String? { // If the key description should be obscured, now is the time to do it.
        val index = mKeyCodeMap.indexOfKey(codePoint)
        if (index >= 0) {
            return context.getString(mKeyCodeMap.valueAt(index))
        }
        val accentedLetter = getSpokenAccentedLetterDescription(context, codePoint)
        if (accentedLetter != null) {
            return accentedLetter
        }
        // Here, <code>code</code> may be a base (non-accented) letter.
        val unsupportedSymbol = getSpokenSymbolDescription(context, codePoint)
        if (unsupportedSymbol != null) {
            return unsupportedSymbol
        }
        val emojiDescription = getSpokenEmojiDescription(context, codePoint)
        if (emojiDescription != null) {
            return emojiDescription
        }
        return if (Character.isDefined(codePoint) && !Character.isISOControl(codePoint)) {
            StringUtils.newSingleCodePointString(codePoint)
        } else null
    }

    // TODO: Remove this method once TTS supports those accented letters' verbalization.
    private fun getSpokenAccentedLetterDescription(context: Context, code: Int): String? {
        val isUpperCase = Character.isUpperCase(code)
        val baseCode = if (isUpperCase) Character.toLowerCase(code) else code
        val baseIndex = mKeyCodeMap.indexOfKey(baseCode)
        val resId = if (baseIndex >= 0) mKeyCodeMap.valueAt(baseIndex) else getSpokenDescriptionId(context, baseCode, SPOKEN_LETTER_RESOURCE_NAME_FORMAT)
        if (resId == 0) {
            return null
        }
        val spokenText = context.getString(resId)
        return if (isUpperCase) context.getString(R.string.spoken_description_upper_case, spokenText) else spokenText
    }

    // TODO: Remove this method once TTS supports those symbols' verbalization.
    private fun getSpokenSymbolDescription(context: Context, code: Int): String? {
        val resId = getSpokenDescriptionId(context, code, SPOKEN_SYMBOL_RESOURCE_NAME_FORMAT)
        if (resId == 0) {
            return null
        }
        val spokenText = context.getString(resId)
        return if (!TextUtils.isEmpty(spokenText)) {
            spokenText
        } else context.getString(R.string.spoken_symbol_unknown)
        // If a translated description is empty, fall back to unknown symbol description.
    }

    // TODO: Remove this method once TTS supports emoji verbalization.
    private fun getSpokenEmojiDescription(context: Context, code: Int): String? {
        val resId = getSpokenDescriptionId(context, code, SPOKEN_EMOJI_RESOURCE_NAME_FORMAT)
        if (resId == 0) {
            return null
        }
        val spokenText = context.getString(resId)
        return if (!TextUtils.isEmpty(spokenText)) {
            spokenText
        } else context.getString(R.string.spoken_emoji_unknown)
        // If a translated description is empty, fall back to unknown emoji description.
    }

    private fun getSpokenDescriptionId(context: Context, code: Int,
                                       resourceNameFormat: String): Int {
        val resourceName = String.format(Locale.ROOT, resourceNameFormat, code)
        val resources = context.resources
        // Note that the resource package name may differ from the context package name.
        val resourcePackageName = resources.getResourcePackageName(
                R.string.spoken_description_unknown)
        val resId = resources.getIdentifier(resourceName, "string", resourcePackageName)
        if (resId != 0) {
            mKeyCodeMap.append(code, resId)
        }
        return resId
    }

    companion object {
        private val TAG = KeyCodeDescriptionMapper::class.java.simpleName
        private const val SPOKEN_LETTER_RESOURCE_NAME_FORMAT = "spoken_accented_letter_%04X"
        private const val SPOKEN_SYMBOL_RESOURCE_NAME_FORMAT = "spoken_symbol_%04X"
        private const val SPOKEN_EMOJI_RESOURCE_NAME_FORMAT = "spoken_emoji_%04X"
        private const val SPOKEN_EMOTICON_RESOURCE_NAME_PREFIX = "spoken_emoticon"
        private const val SPOKEN_EMOTICON_CODE_POINT_FORMAT = "_%02X"
        // The resource ID of the string spoken for obscured keys
        private const val OBSCURED_KEY_RES_ID = R.string.spoken_description_dot
        val instance = KeyCodeDescriptionMapper()

        /**
         * Returns a context-specific description for the CODE_SWITCH_ALPHA_SYMBOL
         * key or `null` if there is not a description provided for the
         * current keyboard context.
         *
         * @param context The package's context.
         * @param keyboard The keyboard on which the key resides.
         * @return a character sequence describing the action performed by pressing the key
         */
        private fun getDescriptionForSwitchAlphaSymbol(context: Context,
                                                       keyboard: Keyboard?): String? {
            val keyboardId = keyboard!!.mId
            val elementId = keyboardId.mElementId
            val resId: Int
            resId = when (elementId) {
                KeyboardId.ELEMENT_ALPHABET, KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED, KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED -> R.string.spoken_description_to_symbol
                KeyboardId.ELEMENT_SYMBOLS, KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> R.string.spoken_description_to_alpha
                KeyboardId.ELEMENT_PHONE -> R.string.spoken_description_to_symbol
                KeyboardId.ELEMENT_PHONE_SYMBOLS -> R.string.spoken_description_to_numeric
                else -> {
                    Log.e(TAG, "Missing description for keyboard element ID:$elementId")
                    return null
                }
            }
            return context.getString(resId)
        }

        /**
         * Returns a context-sensitive description of the "Shift" key.
         *
         * @param context The package's context.
         * @param keyboard The keyboard on which the key resides.
         * @return A context-sensitive description of the "Shift" key.
         */
        private fun getDescriptionForShiftKey(context: Context,
                                              keyboard: Keyboard?): String {
            val keyboardId = keyboard!!.mId
            val elementId = keyboardId.mElementId
            val resId: Int
            resId = when (elementId) {
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED -> R.string.spoken_description_caps_lock
                KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED, KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> R.string.spoken_description_shift_shifted
                KeyboardId.ELEMENT_SYMBOLS -> R.string.spoken_description_symbols_shift
                KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> R.string.spoken_description_symbols_shift_shifted
                else -> R.string.spoken_description_shift
            }
            return context.getString(resId)
        }

        /**
         * Returns a context-sensitive description of the "Enter" action key.
         *
         * @param context The package's context.
         * @param keyboard The keyboard on which the key resides.
         * @param key The key to describe.
         * @return Returns a context-sensitive description of the "Enter" action key.
         */
        private fun getDescriptionForActionKey(context: Context, keyboard: Keyboard?,
                                               key: Key): String {
            val keyboardId = keyboard!!.mId
            val actionId = keyboardId.imeAction()
            val resId: Int
            // Always use the label, if available.
            if (!TextUtils.isEmpty(key.label)) {
                return key.label!!.trim { it <= ' ' }
            }
            resId = when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> R.string.spoken_description_search
                EditorInfo.IME_ACTION_GO -> R.string.label_go_key
                EditorInfo.IME_ACTION_SEND -> R.string.label_send_key
                EditorInfo.IME_ACTION_NEXT -> R.string.label_next_key
                EditorInfo.IME_ACTION_DONE -> R.string.label_done_key
                EditorInfo.IME_ACTION_PREVIOUS -> R.string.label_previous_key
                else -> R.string.spoken_description_return
            }
            return context.getString(resId)
        }

        // TODO: Remove this method once TTS supports emoticon verbalization.
        private fun getSpokenEmoticonDescription(context: Context,
                                                 outputText: String?): String? {
            val sb = StringBuilder(SPOKEN_EMOTICON_RESOURCE_NAME_PREFIX)
            val textLength = outputText!!.length
            var index = 0
            while (index < textLength) {
                val codePoint = outputText.codePointAt(index)
                sb.append(String.format(Locale.ROOT, SPOKEN_EMOTICON_CODE_POINT_FORMAT, codePoint))
                index = outputText.offsetByCodePoints(index, 1)
            }
            val resourceName = sb.toString()
            val resources = context.resources
            // Note that the resource package name may differ from the context package name.
            val resourcePackageName = resources.getResourcePackageName(
                    R.string.spoken_description_unknown)
            val resId = resources.getIdentifier(resourceName, "string", resourcePackageName)
            return if (resId == 0) null else resources.getString(resId)
        }
    }

    init { // Special non-character codes defined in Keyboard
        mKeyCodeMap.put(Constants.CODE_SPACE, R.string.spoken_description_space)
        mKeyCodeMap.put(Constants.CODE_DELETE, R.string.spoken_description_delete)
        mKeyCodeMap.put(Constants.CODE_ENTER, R.string.spoken_description_return)
        mKeyCodeMap.put(Constants.CODE_SETTINGS, R.string.spoken_description_settings)
        mKeyCodeMap.put(Constants.CODE_SHIFT, R.string.spoken_description_shift)
        mKeyCodeMap.put(Constants.CODE_SHORTCUT, R.string.spoken_description_mic)
        mKeyCodeMap.put(Constants.CODE_SWITCH_ALPHA_SYMBOL, R.string.spoken_description_to_symbol)
        mKeyCodeMap.put(Constants.CODE_TAB, R.string.spoken_description_tab)
        mKeyCodeMap.put(Constants.CODE_LANGUAGE_SWITCH,
                R.string.spoken_description_language_switch)
        mKeyCodeMap.put(Constants.CODE_ACTION_NEXT, R.string.spoken_description_action_next)
        mKeyCodeMap.put(Constants.CODE_ACTION_PREVIOUS,
                R.string.spoken_description_action_previous)
        mKeyCodeMap.put(Constants.CODE_EMOJI, R.string.spoken_description_emoji)
        // Because the upper-case and lower-case mappings of the following letters is depending on
// the locale, the upper case descriptions should be defined here. The lower case
// descriptions are handled in {@link #getSpokenLetterDescriptionId(Context,int)}.
// U+0049: "I" LATIN CAPITAL LETTER I
// U+0069: "i" LATIN SMALL LETTER I
// U+0130: "İ" LATIN CAPITAL LETTER I WITH DOT ABOVE
// U+0131: "ı" LATIN SMALL LETTER DOTLESS I
        mKeyCodeMap.put(0x0049, R.string.spoken_letter_0049)
        mKeyCodeMap.put(0x0130, R.string.spoken_letter_0130)
    }
}