package org.janelia.colormipsearch.image;

import java.util.Arrays;

public class ValuesHistogram {
    private final int valueMask;
    private final int[] histogram;
    private int histMax;

    ValuesHistogram(int valueBits) {
        this.valueMask = (1 << valueBits) -1;
        histogram = new int[(1<<valueBits)];
        histMax = 0;
    }

    private ValuesHistogram(ValuesHistogram c) {
        this.valueMask = c.valueMask;
        histogram = Arrays.copyOf(c.histogram, c.histogram.length);
        histMax = c.histMax;
    }

    int add(int val) {
        int ci = val & valueMask;
        if (ci > 0) {
            histogram[ci] = ++histogram[ci];
            if (ci > histMax) {
                histMax = ci;
            }
        }
        return histMax;
    }

    int remove(int val) {
        int ci = val & valueMask;
        if (ci > 0) {
            int ciCount = --histogram[ci];
            if (ciCount < 0) {
                throw new IllegalStateException("Illegal remove at " + ci + " from the histogram");
            }
            histogram[ci] = ciCount;
            if (ci == histMax && histogram[histMax] <= 0) {
                // no need to test if current ci is max because the only time histogram of max gets to 0
                // is if max > 0 and ci == max
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

    void clear() {
        Arrays.fill(histogram, 0);
        histMax = 0;
    }

    int maxVal() {
        return histMax;
    }

    ValuesHistogram copy() {
        return new ValuesHistogram(this);
    }

}
