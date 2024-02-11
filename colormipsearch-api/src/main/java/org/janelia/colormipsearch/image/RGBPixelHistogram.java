package org.janelia.colormipsearch.image;

import java.util.stream.LongStream;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class RGBPixelHistogram<T extends RGBPixelType<T>> implements PixelHistogram<T> {

    private final Gray8PixelHistogram rHistogram;
    private final Gray8PixelHistogram gHistogram;
    private final Gray8PixelHistogram bHistogram;
    private final T pixelValue;
    private Interval interval;

    public RGBPixelHistogram(T pixelValue, int numDimensions) {
        this.pixelValue = pixelValue;
        this.interval = Intervals.createMinMax(
                LongStream.concat(
                        LongStream.range(0, numDimensions).map(i -> 1L),
                        LongStream.range(0, numDimensions).map(i -> 0L)
                ).toArray()
        );
        this.rHistogram = new Gray8PixelHistogram(interval);
        this.gHistogram = new Gray8PixelHistogram(interval);
        this.bHistogram = new Gray8PixelHistogram(interval);
    }

    private RGBPixelHistogram(T pixelValue,
                              Gray8PixelHistogram rHistogram,
                              Gray8PixelHistogram gHistogram,
                              Gray8PixelHistogram bHistogram,
                              Interval interval) {
        this.pixelValue = pixelValue;
        this.rHistogram = rHistogram;
        this.gHistogram = gHistogram;
        this.bHistogram = bHistogram;
        this.interval = interval;
    }

    @Override
    public T add(T val) {
        int maxR = rHistogram.add(val.getRed());
        int maxG = gHistogram.add(val.getGreen());
        int maxB = bHistogram.add(val.getBlue());
        return pixelValue.createFromRGB(maxR, maxG, maxB);
    }

    @Override
    public T remove(T val) {
        int maxR = rHistogram.remove(val.getRed());
        int maxG = gHistogram.remove(val.getGreen());
        int maxB = bHistogram.remove(val.getBlue());
        return pixelValue.createFromRGB(maxR, maxG, maxB);
    }

    @Override
    public void clear() {
        rHistogram.clear();
        gHistogram.clear();
        bHistogram.clear();
    }

    @Override
    public T maxVal() {
        int maxR = rHistogram.maxVal();
        int maxG = gHistogram.maxVal();
        int maxB = bHistogram.maxVal();
        return pixelValue.createFromRGB(maxR, maxG, maxB);
    }

    @Override
    public RGBPixelHistogram<T> copy() {
        return new RGBPixelHistogram<>(pixelValue.copy(), rHistogram.copy(), gHistogram.copy(), bHistogram.copy(), interval);
    }

    @Override
    public Interval histogramInterval() {
        return interval;
    }

    @Override
    public void updateHistogramInterval(Interval interval) {
        this.interval = interval;
    }
}
