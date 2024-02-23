package org.janelia.colormipsearch.image;

import java.util.Arrays;

public class Gray8PixelHistogram implements PixelHistogram<Integer> {
    private final int[] histogram;
    private int histMax;

    Gray8PixelHistogram() {
        histogram = new int[256];
        histMax = 0;
    }

    @Override
    public Integer add(Integer val) {
        int ci = val & 0xff;
        if (ci > 0) {
            histogram[ci] = ++histogram[ci];
            histMax = histMax ^ ((histMax ^ ci) & -(histMax < ci ? 1 : 0)); // max(histMax, ci)
        }
        return histMax;
    }

    @Override
    public Integer remove(Integer val) {
        int ci = val & 0xFF;
        if (ci > 0) {
            int ciCount = --histogram[ci];
            if (ciCount < 0) {
                throw new IllegalStateException("Illegal remove at " + ci + " from the histogram");
            } else {
                histogram[ci] = ciCount;
            }
            if (histogram[histMax] == 0) {
                // no need to test if current ci is max because the only time histogram of max gets to 0
                // is if max > 0 and ci == max
                histMax = 0;
                for (int pv = ci - 1; pv >= 0; pv--) {
                    if (histogram[pv] > 0) {
                        histMax = pv;
                        break;
                    }
                }
            }
        }
        return histMax;
    }

    @Override
    public void clear() {
        Arrays.fill(histogram, 0);
        histMax = 0;
    }

    @Override
    public Integer maxVal() {
        return histMax;
    }

    @Override
    public Gray8PixelHistogram copy() {
        Gray8PixelHistogram histogramCopy = new Gray8PixelHistogram();
        System.arraycopy(histogram, 0, histogramCopy.histogram, 0, 256);
        return histogramCopy;
    }

}
