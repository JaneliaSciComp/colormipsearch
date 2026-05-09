package org.janelia.colormipsearch.imageprocessing;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class ImageStats {
    public int minVal;
    public int maxVal;
    public int meanVal;
    public int nonBgCount;
    public int totalPixels;
    public long nonBgSum;
    public int[] histogram;

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("minVal", minVal)
                .append("maxVal", maxVal)
                .append("meanVal", meanVal)
                .append("nonBgCount", nonBgCount)
                .append("totalPixels", totalPixels)
                .append("nonBgSum", nonBgSum)
                .toString();
    }
}
