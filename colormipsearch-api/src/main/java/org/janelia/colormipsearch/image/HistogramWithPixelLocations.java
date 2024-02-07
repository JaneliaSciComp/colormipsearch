package org.janelia.colormipsearch.image;

public interface HistogramWithPixelLocations<T> extends PixelHistogram<T>, PixelLocations<T> {
    HistogramWithPixelLocations<T> copy();
}
