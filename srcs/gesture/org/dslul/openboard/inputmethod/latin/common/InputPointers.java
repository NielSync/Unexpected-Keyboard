/*
 * (Apache License 2.0) — minimal replacement for the InputPointers class of
 * OpenBoard (commit 46fdf2b550035ca69299ce312fa158e7ade36967), keeping the
 * public surface used for word gesture typing.
 */

package org.dslul.openboard.inputmethod.latin.common;

import java.util.Arrays;

// TODO: This class is not thread-safe.
public final class InputPointers {
    private static final int DEFAULT_CAPACITY = 128;

    private int[] mXCoordinates = new int[DEFAULT_CAPACITY];
    private int[] mYCoordinates = new int[DEFAULT_CAPACITY];
    private int[] mPointerIds = new int[DEFAULT_CAPACITY];
    private int[] mTimes = new int[DEFAULT_CAPACITY];
    private int mPointerSize;

    public InputPointers() {}

    public InputPointers(final int capacity) {
        mXCoordinates = new int[capacity];
        mYCoordinates = new int[capacity];
        mPointerIds = new int[capacity];
        mTimes = new int[capacity];
    }

    private void ensureCapacity(final int length) {
        if (length <= mXCoordinates.length) {
            return;
        }
        final int newCapacity = Math.max(length, mXCoordinates.length * 2);
        mXCoordinates = Arrays.copyOf(mXCoordinates, newCapacity);
        mYCoordinates = Arrays.copyOf(mYCoordinates, newCapacity);
        mPointerIds = Arrays.copyOf(mPointerIds, newCapacity);
        mTimes = Arrays.copyOf(mTimes, newCapacity);
    }

    public void addPointer(final int x, final int y, final int pointerId, final int time) {
        ensureCapacity(mPointerSize + 1);
        mXCoordinates[mPointerSize] = x;
        mYCoordinates[mPointerSize] = y;
        mPointerIds[mPointerSize] = pointerId;
        mTimes[mPointerSize] = time;
        ++mPointerSize;
    }

    public void reset() {
        mPointerSize = 0;
    }

    public int getPointerSize() {
        return mPointerSize;
    }

    public int[] getXCoordinates() {
        return mXCoordinates;
    }

    public int[] getYCoordinates() {
        return mYCoordinates;
    }

    public int[] getPointerIds() {
        return mPointerIds;
    }

    public int[] getTimes() {
        return mTimes;
    }
}
