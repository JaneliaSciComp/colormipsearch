package org.janelia.colormipsearch.image.view;

/**
 * A histogram that tracks counts of integer values and maintains
 * the current maximum value efficiently. Supports add and remove
 * operations for use as a sliding-window max filter.
 */
public class ValuesHistogram {
    private final int valueMask;
    private final int[] histogram;
    private final boolean[] touched;
    private final int[] touchedValues;
    private int histMax;
    private int touchedCount;

    public ValuesHistogram(int valueBits) {
        int numberOfBins = 1 << valueBits;
        this.valueMask = numberOfBins - 1;
        this.histogram = new int[numberOfBins];
        this.touched = new boolean[numberOfBins];
        this.touchedValues = new int[numberOfBins];
    }

    public int add(int val) {
        int ci = histogramIndex(val);
        if (ci > 0) {
            if (!touched[ci]) {
                touched[ci] = true;
                touchedValues[touchedCount++] = ci;
            }
            histogram[ci]++;
            if (ci > histMax) {
                histMax = ci;
            }
        }
        return histMax;
    }

    public int remove(int val) {
        int ci = histogramIndex(val);
        if (ci > 0) {
            int ciCount = --histogram[ci];
            if (ciCount < 0) {
                throw new IllegalStateException("Illegal remove of value " + ci + " from the histogram");
            }
            if (ci == histMax && histogram[histMax] <= 0) {
                for (int pv = ci - 1; pv >= 0; pv--) {
                    if (histogram[pv] > 0) {
                        histMax = pv;
                        return histMax;
                    }
                }
                histMax = 0;
            }
        }
        return histMax;
    }

    public void clear() {
        for (int i = 0; i < touchedCount; i++) {
            int ci = touchedValues[i];
            histogram[ci] = 0;
            touched[ci] = false;
        }
        touchedCount = 0;
        histMax = 0;
    }

    public int maxVal() {
        return histMax;
    }

    private int histogramIndex(int val) {
        return val & valueMask;
    }
}
