/*
 * (Apache License 2.0) — adapted from OpenBoard at commit
 * 46fdf2b550035ca69299ce312fa158e7ade36967 for Unexpected Keyboard.
 * The gesture library is bundled with the application.
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.util.Log;

public final class JniUtils {
    private static final String TAG = JniUtils.class.getSimpleName();
    public static final String JNI_LIB_NAME = "jni_latinimegoogle";

    public static boolean sHaveGestureLib = false;
    static {
        try {
            System.loadLibrary(JNI_LIB_NAME);
            sHaveGestureLib = true;
        } catch (Throwable ue) {
            Log.e(TAG, "Could not load native library " + JNI_LIB_NAME, ue);
        }
    }

    private JniUtils() {
        // This utility class is not publicly instantiable.
    }

    public static void loadNativeLibrary() {
        // Ensures the static initializer is called
    }
}
