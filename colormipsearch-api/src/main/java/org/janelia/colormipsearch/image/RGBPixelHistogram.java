package org.janelia.colormipsearch.image;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.LongStream;

import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.util.Intervals;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class RGBPixelHistogram<T extends RGBPixelType<T>> implements HistogramWithPixelLocations<T> {

    private final Gray8PixelHistogram rHistogram;
    private final Gray8PixelHistogram gHistogram;
    private final Gray8PixelHistogram bHistogram;
    private final Map<Point, T> pixelLocations;
    private final T pixelValue;
    private Interval interval;

    public RGBPixelHistogram(T pixelValue, int numDimensions) {
        this(pixelValue, new Gray8PixelHistogram(), new Gray8PixelHistogram(), new Gray8PixelHistogram(), new HashMap<>(),
                Intervals.createMinMax(
                        LongStream.concat(
                                LongStream.range(0, numDimensions).map(i -> 1L),
                                LongStream.range(0, numDimensions).map(i -> 0L)
                        ).toArray())
        );
    }

    private RGBPixelHistogram(T pixelValue,
                              Gray8PixelHistogram rHistogram,
                              Gray8PixelHistogram gHistogram,
                              Gray8PixelHistogram bHistogram,
                              Map<Point, T> pixelLocations,
                              Interval interval) {
        this.pixelValue = pixelValue;
        this.rHistogram = rHistogram;
        this.gHistogram = gHistogram;
        this.bHistogram = bHistogram;
        this.pixelLocations = new HashMap<>(pixelLocations);
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
        pixelLocations.clear();
    }

    @Override
    public RGBPixelHistogram<T> copy() {
        return new RGBPixelHistogram<>(pixelValue.copy(), rHistogram.copy(), gHistogram.copy(), bHistogram.copy(), pixelLocations, interval);
    }

    @Override
    public T add(Point location, T val) {
        if (val.isNotZero()) {
            pixelLocations.put(location, val);
            return add(val);
        }
        return pixelValue;
    }

    @Override
    public T remove(Collection<Point> locations) {
        T val = null;
        for (Point l : locations) {
            T removedVal = pixelLocations.remove(l);
            if (removedVal != null) {
                val = remove(removedVal);
            }
        }
        return val == null ? pixelValue : val;
    }

    @Override
    public Collection<Point> list() {
        return pixelLocations.keySet();
    }

    @Override
    public Interval getInterval() {
        return interval;
    }

    @Override
    public Interval updateInterval(Interval interval) {
        Interval prevInterval = this.interval;
        this.interval = interval;
        return prevInterval;
    }
}
