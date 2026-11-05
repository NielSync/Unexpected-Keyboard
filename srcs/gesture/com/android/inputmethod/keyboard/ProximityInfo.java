/*
 * Copyright (C) 2011 The Android Open Source Project
 * (Apache License 2.0) — vendored and adapted from OpenBoard at commit
 * 46fdf2b550035ca69299ce312fa158e7ade36967 for Unexpected Keyboard.
 *
 * The original class works with OpenBoard's [Key] class. This adaptation
 * takes simple key descriptions ([KeyInfo]) instead, so that Unexpected
 * Keyboard can feed the geometry of its own layouts. The native method
 * declarations must match the functions registered by
 * libjni_latinimegoogle.so.
 */

package com.android.inputmethod.keyboard;

import org.dslul.openboard.inputmethod.latin.utils.JniUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProximityInfo {
    // Must be equal to MAX_PROXIMITY_CHARS_SIZE in native/jni/src/defines.h
    public static final int MAX_PROXIMITY_CHARS_SIZE = 16;
    // Must be equal to ProximityInfoParams in native/jni/src/defines.h
    public static final int GRID_WIDTH = 32;
    public static final int GRID_HEIGHT = 16;
    /** Number of key widths from current touch point to search for nearest
        keys. */
    private static final float SEARCH_DISTANCE = 1.2f;
    private static final int NOT_A_CODE = -1;
    private static final int CODE_SPACE = 32;

    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;
    private final int mCellWidth;
    private final int mCellHeight;
    private final int mKeyboardMinWidth;
    private final int mKeyboardHeight;
    private final int mMostCommonKeyWidth;
    private final int mMostCommonKeyHeight;
    private final List<KeyInfo> mSortedKeys;
    private final List<KeyInfo>[] mGridNeighbors;

    /** Minimal description of a keyboard key for the proximity computation.
        Coordinates are pixels in the keyboard view. */
    public static final class KeyInfo {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final int code;

        public KeyInfo(int x, int y, int width, int height, int code) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.code = code;
        }
    }

    @SuppressWarnings("unchecked")
    public ProximityInfo(final int minWidth, final int height,
            final int mostCommonKeyWidth, final int mostCommonKeyHeight,
            final List<KeyInfo> sortedKeys) {
        mGridWidth = GRID_WIDTH;
        mGridHeight = GRID_HEIGHT;
        mGridSize = mGridWidth * mGridHeight;
        mCellWidth = (minWidth + mGridWidth - 1) / mGridWidth;
        mCellHeight = (height + mGridHeight - 1) / mGridHeight;
        mKeyboardMinWidth = minWidth;
        mKeyboardHeight = height;
        mMostCommonKeyWidth = mostCommonKeyWidth;
        mMostCommonKeyHeight = mostCommonKeyHeight;
        mSortedKeys = sortedKeys;
        mGridNeighbors = new List[mGridSize];
        if (minWidth == 0 || height == 0) {
            return;
        }
        computeNearestNeighbors();
        mNativeProximityInfo = createNativeProximityInfo();
    }

    private long mNativeProximityInfo;
    static {
        JniUtils.loadNativeLibrary();
    }

    private static native long setProximityInfoNative(int displayWidth, int displayHeight,
            int gridWidth, int gridHeight, int mostCommonKeyWidth, int mostCommonKeyHeight,
            int[] proximityCharsArray, int keyCount, int[] keyXCoordinates, int[] keyYCoordinates,
            int[] keyWidths, int[] keyHeights, int[] keyCharCodes, float[] sweetSpotCenterXs,
            float[] sweetSpotCenterYs, float[] sweetSpotRadii);

    private static native void releaseProximityInfoNative(long nativeProximityInfo);

    private static boolean needsProximityInfo(final KeyInfo key) {
        // Don't include special keys into ProximityInfo.
        return key.code >= CODE_SPACE;
    }

    private long createNativeProximityInfo() {
        final int[] proximityCharsArray = new int[mGridSize * MAX_PROXIMITY_CHARS_SIZE];
        Arrays.fill(proximityCharsArray, NOT_A_CODE);
        for (int i = 0; i < mGridSize; ++i) {
            final List<KeyInfo> neighborKeys = mGridNeighbors[i];
            final int proximityCharsLength = neighborKeys.size();
            int infoIndex = i * MAX_PROXIMITY_CHARS_SIZE;
            for (int j = 0; j < proximityCharsLength && infoIndex < (i + 1) * MAX_PROXIMITY_CHARS_SIZE; ++j) {
                proximityCharsArray[infoIndex] = neighborKeys.get(j).code;
                infoIndex++;
            }
        }
        final int keyCount = mSortedKeys.size();
        final int[] keyXCoordinates = new int[keyCount];
        final int[] keyYCoordinates = new int[keyCount];
        final int[] keyWidths = new int[keyCount];
        final int[] keyHeights = new int[keyCount];
        final int[] keyCharCodes = new int[keyCount];
        // Touch position correction is not used: the sweet spots are the
        // centers of the keys and the native code falls back to them when
        // the sweet spot arrays are [null].
        final float[] sweetSpotCenterXs = null;
        final float[] sweetSpotCenterYs = null;
        final float[] sweetSpotRadii = null;
        for (int infoIndex = 0; infoIndex < keyCount; infoIndex++) {
            final KeyInfo key = mSortedKeys.get(infoIndex);
            keyXCoordinates[infoIndex] = key.x;
            keyYCoordinates[infoIndex] = key.y;
            keyWidths[infoIndex] = key.width;
            keyHeights[infoIndex] = key.height;
            keyCharCodes[infoIndex] = key.code;
        }
        return setProximityInfoNative(mKeyboardMinWidth, mKeyboardHeight, mGridWidth,
                mGridHeight, mMostCommonKeyWidth, mMostCommonKeyHeight, proximityCharsArray,
                keyCount, keyXCoordinates, keyYCoordinates, keyWidths, keyHeights,
                keyCharCodes, sweetSpotCenterXs, sweetSpotCenterYs, sweetSpotRadii);
    }

    public long getNativeProximityInfo() {
        return mNativeProximityInfo;
    }

    public void release() {
        if (mNativeProximityInfo != 0) {
            releaseProximityInfoNative(mNativeProximityInfo);
            mNativeProximityInfo = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            release();
        } finally {
            super.finalize();
        }
    }

    private void computeNearestNeighbors() {
        final int defaultWidth = mMostCommonKeyWidth;
        final int keyCount = mSortedKeys.size();
        final int gridSize = mGridNeighbors.length;
        final int threshold = (int) (defaultWidth * SEARCH_DISTANCE);
        final int thresholdSquared = threshold * threshold;
        final int lastPixelXCoordinate = mGridWidth * mCellWidth - 1;
        final int lastPixelYCoordinate = mGridHeight * mCellHeight - 1;

        final KeyInfo[] neighborsFlatBuffer = new KeyInfo[gridSize * keyCount];
        final int[] neighborCountPerCell = new int[gridSize];
        final int halfCellWidth = mCellWidth / 2;
        final int halfCellHeight = mCellHeight / 2;
        for (final KeyInfo key : mSortedKeys) {
            final int keyX = key.x;
            final int keyY = key.y;
            final int topPixelWithinThreshold = keyY - threshold;
            final int yDeltaToGrid = topPixelWithinThreshold % mCellHeight;
            final int yMiddleOfTopCell = topPixelWithinThreshold - yDeltaToGrid + halfCellHeight;
            final int yStart = Math.max(halfCellHeight,
                    yMiddleOfTopCell + (yDeltaToGrid <= halfCellHeight ? 0 : mCellHeight));
            final int yEnd = Math.min(lastPixelYCoordinate, keyY + key.height + threshold);

            final int leftPixelWithinThreshold = keyX - threshold;
            final int xDeltaToGrid = leftPixelWithinThreshold % mCellWidth;
            final int xMiddleOfLeftCell = leftPixelWithinThreshold - xDeltaToGrid + halfCellWidth;
            final int xStart = Math.max(halfCellWidth,
                    xMiddleOfLeftCell + (xDeltaToGrid <= halfCellWidth ? 0 : mCellWidth));
            final int xEnd = Math.min(lastPixelXCoordinate, keyX + key.width + threshold);

            int baseIndexOfCurrentRow = (yStart / mCellHeight) * mGridWidth + (xStart / mCellWidth);
            for (int centerY = yStart; centerY <= yEnd; centerY += mCellHeight) {
                int index = baseIndexOfCurrentRow;
                for (int centerX = xStart; centerX <= xEnd; centerX += mCellWidth) {
                    if (squaredDistanceToEdge(key, centerX, centerY) < thresholdSquared
                            && neighborCountPerCell[index] < keyCount) {
                        neighborsFlatBuffer[index * keyCount + neighborCountPerCell[index]] = key;
                        ++neighborCountPerCell[index];
                    }
                    ++index;
                }
                baseIndexOfCurrentRow += mGridWidth;
            }
        }

        for (int i = 0; i < gridSize; ++i) {
            final int indexStart = i * keyCount;
            final int indexEnd = indexStart + neighborCountPerCell[i];
            final ArrayList<KeyInfo> neighbors = new ArrayList<>(indexEnd - indexStart);
            for (int index = indexStart; index < indexEnd; index++) {
                neighbors.add(neighborsFlatBuffer[index]);
            }
            mGridNeighbors[i] = Collections.unmodifiableList(neighbors);
        }
    }

    private static int squaredDistanceToEdge(final KeyInfo key, final int x, final int y) {
        final int dx = Math.max(Math.max(key.x - x, 0), x - key.x - key.width);
        final int dy = Math.max(Math.max(key.y - y, 0), y - key.y - key.height);
        return dx * dx + dy * dy;
    }
}
