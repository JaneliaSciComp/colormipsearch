package org.janelia.colormipsearch.image;

import net.imglib2.type.numeric.IntegerType;

public class IntensityPixelHistogram<T extends IntegerType<T>> implements PixelHistogram<T> {

    private final ValuesHistogram histogram;
    private final T pixelValue;

    public IntensityPixelHistogram(T pixelValue, int nbits) {
        this.pixelValue = pixelValue.createVariable();
        this.histogram = new ValuesHistogram(nbits);
    }

    private IntensityPixelHistogram(T pixelValue,
                                    ValuesHistogram histogram) {
        this.pixelValue = pixelValue.createVariable();
        this.histogram = histogram;
    }

    @Override
    public T add(T val) {
        int max = histogram.add(val.getInteger());
        pixelValue.setInteger(max);
        return pixelValue;
    }

    @Override
    public T remove(T val) {
        int max = histogram.remove(val.getInteger());
        pixelValue.setInteger(max);
        return pixelValue;
    }

    @Override
    public void clear() {
        histogram.clear();
    }

    @Override
    public T maxVal() {
        int max = histogram.maxVal();
        pixelValue.setInteger(max);
        return pixelValue;
    }

    @Override
    public IntensityPixelHistogram<T> copy() {
        return new IntensityPixelHistogram<>(pixelValue.copy(), histogram.copy());
    }
}
