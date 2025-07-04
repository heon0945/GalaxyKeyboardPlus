/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpannableStringUtils {
    /**
     * Copies the spans from the region <code>start...end</code> in
     * <code>source</code> to the region
     * <code>destoff...destoff+end-start</code> in <code>dest</code>.
     * Spans in <code>source</code> that begin before <code>start</code>
     * or end after <code>end</code> but overlap this range are trimmed
     * as if they began at <code>start</code> or ended at <code>end</code>.
     * Only SuggestionSpans that don't have the SPAN_PARAGRAPH span are copied.
     *
     * This code is almost entirely taken from {@link TextUtils#copySpansFrom}, except for the
     * kind of span that is copied.
     *
     * @throws IndexOutOfBoundsException if any of the copied spans
     * are out of range in <code>dest</code>.
     */
    public static void copyNonParagraphSuggestionSpansFrom(Spanned source, int start, int end,
            Spannable dest, int destoff) {
        Object[] spans = source.getSpans(start, end, SuggestionSpan.class);

        for (int i = 0; i < spans.length; i++) {
            int fl = source.getSpanFlags(spans[i]);
            // We don't care about the PARAGRAPH flag in LatinIME code. However, if this flag
            // is set, Spannable#setSpan will throw an exception unless the span is on the edge
            // of a word. But the spans have been split into two by the getText{Before,After}Cursor
            // methods, so after concatenation they may end in the middle of a word.
            // Since we don't use them, we can just remove them and avoid crashing.
            fl &= ~Spanned.SPAN_PARAGRAPH;

            int st = source.getSpanStart(spans[i]);
            int en = source.getSpanEnd(spans[i]);

            if (st < start)
                st = start;
            if (en > end)
                en = end;

            dest.setSpan(spans[i], st - start + destoff, en - start + destoff,
                         fl);
        }
    }

    /**
     * Returns a CharSequence concatenating the specified CharSequences, retaining their
     * SuggestionSpans that don't have the PARAGRAPH flag, but not other spans.
     *
     * This code is almost entirely taken from {@link TextUtils#concat(CharSequence...)}, except
     * it calls copyNonParagraphSuggestionSpansFrom instead of {@link TextUtils#copySpansFrom}.
     */
    public static CharSequence concatWithNonParagraphSuggestionSpansOnly(CharSequence... text) {
        if (text.length == 0) {
            return "";
        }

        if (text.length == 1) {
            return text[0];
        }

        boolean spanned = false;
        for (int i = 0; i < text.length; i++) {
            if (text[i] instanceof Spanned) {
                spanned = true;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length; i++) {
            sb.append(text[i]);
        }

        if (!spanned) {
            return sb.toString();
        }

        SpannableString ss = new SpannableString(sb);
        int off = 0;
        for (int i = 0; i < text.length; i++) {
            int len = text[i].length();

            if (text[i] instanceof Spanned) {
                copyNonParagraphSuggestionSpansFrom((Spanned) text[i], 0, len, ss, off);
            }

            off += len;
        }

        return new SpannedString(ss);
    }

    public static boolean hasUrlSpans(final CharSequence text,
            final int startIndex, final int endIndex) {
        if (!(text instanceof Spanned)) {
            return false; // Not spanned, so no link
        }
        final Spanned spanned = (Spanned)text;
        // getSpans(x, y) does not return spans that start on x or end on y. x-1, y+1 does the
        // trick, and works in all cases even if startIndex <= 0 or endIndex >= text.length().
        final URLSpan[] spans = spanned.getSpans(startIndex - 1, endIndex + 1, URLSpan.class);
        return null != spans && spans.length > 0;
    }

    /**
     * Splits the given {@code charSequence} with at occurrences of the given {@code regex}.
     * <p>
     * This is equivalent to
     * {@code charSequence.toString().split(regex, preserveTrailingEmptySegments ? -1 : 0)}
     * except that the spans are preserved in the result array.
     * </p>
     * @param charSequence the character sequence to be split.
     * @param regex the regex pattern to be used as the separator.
     * @param preserveTrailingEmptySegments {@code true} to preserve the trailing empty
     * segments. Otherwise, trailing empty segments will be removed before being returned.
     * @return the array which contains the result. All the spans in the <code>charSequence</code>
     * is preserved.
     */
    @UsedForTesting
    public static CharSequence[] split(final CharSequence charSequence, final String regex,
            final boolean preserveTrailingEmptySegments) {
        // A short-cut for non-spanned strings.
        if (!(charSequence instanceof Spanned)) {
            // -1 means that trailing empty segments will be preserved.
            return charSequence.toString().split(regex, preserveTrailingEmptySegments ? -1 : 0);
        }

        // Hereafter, emulate String.split for CharSequence.
        final ArrayList<CharSequence> sequences = new ArrayList<>();
        final Matcher matcher = Pattern.compile(regex).matcher(charSequence);
        int nextStart = 0;
        boolean matched = false;
        while (matcher.find()) {
            sequences.add(charSequence.subSequence(nextStart, matcher.start()));
            nextStart = matcher.end();
            matched = true;
        }
        if (!matched) {
            // never matched. preserveTrailingEmptySegments is ignored in this case.
            return new CharSequence[] { charSequence };
        }
        sequences.add(charSequence.subSequence(nextStart, charSequence.length()));
        if (!preserveTrailingEmptySegments) {
            for (int i = sequences.size() - 1; i >= 0; --i) {
                if (!TextUtils.isEmpty(sequences.get(i))) {
                    break;
                }
                sequences.remove(i);
            }
        }
        return sequences.toArray(new CharSequence[sequences.size()]);
    }
}
