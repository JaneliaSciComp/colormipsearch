package org.janelia.colormipsearch.image.view;

import java.util.Arrays;

/**
 * A histogram that tracks counts of integer values and maintains
 * the current maximum value efficiently. Supports add and remove
 * operations for use as a sliding-window max filter.
 */
public class ValuesHistogram {
    private final int valueMask;
    private final int[] histogram;
    private int histMax;

    public ValuesHistogram(int valueBits) {
        this.valueMask = (1 << valueBits) - 1;
        histogram = new int[(1 << valueBits)];
        histMax = 0;
    }

    private ValuesHistogram(ValuesHistogram c) {
        this.valueMask = c.valueMask;
        histogram = Arrays.copyOf(c.histogram, c.histogram.length);
        histMax = c.histMax;
    }

    public int add(int val) {
        int ci = val & valueMask;
        if (ci > 0) {
            histogram[ci]++;
            if (ci > histMax) {
                histMax = ci;
            }
        }
        return histMax;
    }

    public int remove(int val) {
        int ci = val & valueMask;
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
        Arrays.fill(histogram, 0);
        histMax = 0;
    }

    public int maxVal() {
        return histMax;
    }
}
