/*
 * Copyright (C) 2013 The Android Open Source Project
 * (Apache License 2.0) — vendored and trimmed from OpenBoard at commit
 * 46fdf2b550035ca69299ce312fa158e7ade36967 for Unexpected Keyboard.
 *
 * The native declarations must match the functions registered by
 * libjni_latinimegoogle.so; the helpers are not used by Unexpected Keyboard
 * but the declarations are required for the JNI registration to succeed.
 */

package com.android.inputmethod.latin.utils;

import org.dslul.openboard.inputmethod.latin.utils.JniUtils;

public final class BinaryDictionaryUtils {
    private static final String TAG = BinaryDictionaryUtils.class.getSimpleName();

    static {
        JniUtils.loadNativeLibrary();
    }

    private BinaryDictionaryUtils() {
        // This utility class is not publicly instantiable.
    }

    private static native boolean createEmptyDictFileNative(String filePath, long dictVersion,
            String locale, String[] attributeKeyStringArray, String[] attributeValueStringArray);
    private static native float calcNormalizedScoreNative(int[] before, int[] after, int score);
    private static native int setCurrentTimeForTestNative(int currentTime);
}
