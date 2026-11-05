/*
 * Copyright (C) 2008 The Android Open Source Project
 * (Apache License 2.0) — vendored and trimmed from OpenBoard at commit
 * 46fdf2b550035ca69299ce312fa158e7ade36967 for Unexpected Keyboard.
 *
 * Only the native bindings and the pieces needed for word gesture typing
 * are kept. The native method declarations must match exactly the
 * functions registered by libjni_latinimegoogle.so.
 */

package com.android.inputmethod.latin;

import android.text.TextUtils;

import org.dslul.openboard.inputmethod.latin.common.InputPointers;
import org.dslul.openboard.inputmethod.latin.common.NativeSuggestOptions;
import org.dslul.openboard.inputmethod.latin.utils.JniUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Implements a static, compacted, binary dictionary of standard words. */
public final class BinaryDictionary {
    public static final int DICTIONARY_MAX_WORD_LENGTH = 48;
    public static final int MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3;

    private static final int NOT_A_CODE = -1;
    /** Must be equal to MAX_RESULTS in native/jni/src/defines.h */
    private static final int MAX_RESULTS = 18;

    private long mNativeDict;
    private final Locale mLocale;

    private final DicTraverseSession mTraverseSession;

    static {
        JniUtils.loadNativeLibrary();
    }

    /**
     * Constructs a binary dictionary from an existing dictionary file.
     *
     * @param path the path of the file to read through native code.
     * @param offset the offset of the dictionary data within the file.
     * @param length the length of the binary data.
     * @param useFullEditDistance whether to use the full edit distance in
     *     suggestions.
     * @param locale the locale of the dictionary.
     */
    public BinaryDictionary(final String path, final long offset, final long length,
            final boolean useFullEditDistance, final Locale locale) {
        mLocale = locale;
        mNativeDict = openNative(path, offset, length, false /* isUpdatable */);
        mTraverseSession = new DicTraverseSession(locale, mNativeDict, length);
    }

    private static native long openNative(String sourceDir, long dictOffset, long dictSize,
            boolean isUpdatable);
    private static native long createOnMemoryNative(long formatVersion,
            String locale, String[] attributeKeyStringArray, String[] attributeValueStringArray);
    private static native void getHeaderInfoNative(long dict, int[] outHeaderSize,
            int[] outFormatVersion, ArrayList<int[]> outAttributeKeys,
            ArrayList<int[]> outAttributeValues);
    private static native boolean flushNative(long dict, String filePath);
    private static native boolean needsToRunGCNative(long dict, boolean mindsBlockByGC);
    private static native boolean flushWithGCNative(long dict, String filePath);
    private static native void closeNative(long dict);
    private static native int getFormatVersionNative(long dict);
    private static native int getProbabilityNative(long dict, int[] word);
    private static native int getMaxProbabilityOfExactMatchesNative(long dict, int[] word);
    private static native int getNgramProbabilityNative(long dict, int[][] prevWordCodePointArrays,
            boolean[] isBeginningOfSentenceArray, int[] word);
    private static native void getWordPropertyNative(long dict, int[] word,
            boolean isBeginningOfSentence, int[] outCodePoints, boolean[] outFlags,
            int[] outProbabilityInfo, ArrayList<int[][]> outNgramPrevWordsArray,
            ArrayList<boolean[]> outNgramPrevWordIsBeginningOfSentenceArray,
            ArrayList<int[]> outNgramTargets, ArrayList<int[]> outNgramProbabilityInfo,
            ArrayList<int[]> outShortcutTargets, ArrayList<Integer> outShortcutProbabilities);
    private static native int getNextWordNative(long dict, int token, int[] outCodePoints,
            boolean[] outIsBeginningOfSentence);
    private static native void getSuggestionsNative(long dict, long proximityInfo,
            long traverseSession, int[] xCoordinates, int[] yCoordinates, int[] times,
            int[] pointerIds, int[] inputCodePoints, int inputSize, int[] suggestOptions,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int prevWordCount, int[] outputSuggestionCount, int[] outputCodePoints,
            int[] outputScores, int[] outputIndices, int[] outputTypes,
            int[] outputAutoCommitFirstWordConfidence,
            float[] inOutWeightOfLangModelVsSpatialModel);
    private static native boolean addUnigramEntryNative(long dict, int[] word, int probability,
            int[] shortcutTarget, int shortcutProbability, boolean isBeginningOfSentence,
            boolean isNotAWord, boolean isPossiblyOffensive, int timestamp);
    private static native boolean removeUnigramEntryNative(long dict, int[] word);
    private static native boolean addNgramEntryNative(long dict,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int[] word, int probability, int timestamp);
    private static native boolean removeNgramEntryNative(long dict,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int[] word);
    private static native boolean updateEntriesForWordWithNgramContextNative(long dict,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int[] word, boolean isValidWord, int count, int timestamp);
    private static native int updateEntriesForInputEventsNative(long dict,
            com.android.inputmethod.latin.utils.WordInputEventForPersonalization[] inputEvents,
            int startIndex);
    private static native String getPropertyNative(long dict, String query);
    private static native boolean isCorruptedNative(long dict);
    private static native boolean migrateNative(long dict, String dictFilePath,
            long newFormatVersion);

    public boolean isValidDictionary() {
        return mNativeDict != 0;
    }

    /** Decode a gesture from the recorded pointer positions. Returns the
        suggested words, best first. */
    public String[] getSuggestionsFromGesture(final InputPointers pointers,
            final long proximityInfo) {
        if (!isValidDictionary()) {
            return new String[0];
        }
        final DicTraverseSession session = mTraverseSession;
        Arrays.fill(session.mInputCodePoints, NOT_A_CODE);
        // Gestures are decoded at the beginning of a sentence.
        session.mPrevWordCodePointArrays[0] = new int[0];
        session.mIsBeginningOfSentenceArray[0] = true;
        final NativeSuggestOptions options = session.mNativeSuggestOptions;
        options.setIsGesture(true);
        options.setUseFullEditDistance(false);
        options.setBlockOffensiveWords(false);
        options.setSpaceAwareGestureEnabled(false);
        options.setWeightForLocale(1.f);
        getSuggestionsNative(mNativeDict, proximityInfo, session.getSession(),
                pointers.getXCoordinates(), pointers.getYCoordinates(),
                pointers.getTimes(), pointers.getPointerIds(),
                session.mInputCodePoints, pointers.getPointerSize(),
                options.getOptions(), session.mPrevWordCodePointArrays,
                session.mIsBeginningOfSentenceArray, 1 /* prevWordCount */,
                session.mOutputSuggestionCount, session.mOutputCodePoints,
                session.mOutputScores, session.mSpaceIndices, session.mOutputTypes,
                session.mOutputAutoCommitFirstWordConfidence,
                session.mInputOutputWeightOfLangModelVsSpatialModel);
        final int count = session.mOutputSuggestionCount[0];
        final ArrayList<String> suggestions = new ArrayList<>();
        for (int j = 0; j < count; ++j) {
            final int start = j * DICTIONARY_MAX_WORD_LENGTH;
            int len = 0;
            while (len < DICTIONARY_MAX_WORD_LENGTH
                    && session.mOutputCodePoints[start + len] != 0) {
                ++len;
            }
            if (len > 0) {
                suggestions.add(new String(session.mOutputCodePoints, start, len));
            }
        }
        return suggestions.toArray(new String[suggestions.size()]);
    }

    public int getFrequency(final String word) {
        if (TextUtils.isEmpty(word)) {
            return -1;
        }
        return getProbabilityNative(mNativeDict, StringUtils.toCodePointArray(word));
    }

    public void close() {
        if (mTraverseSession != null) {
            mTraverseSession.close();
        }
        if (mNativeDict != 0) {
            closeNative(mNativeDict);
            mNativeDict = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}

/** Minimal replacement for [org.dslul...StringUtils.toCodePointArray]. */
class StringUtils {
    static int[] toCodePointArray(final String word) {
        final int[] codePoints = new int[word.codePointCount(0, word.length())];
        int index = 0;
        for (int i = 0; i < word.length(); ) {
            final int codePoint = word.codePointAt(i);
            codePoints[index++] = codePoint;
            i += Character.charCount(codePoint);
        }
        return codePoints;
    }
}
