package org.janelia.colormipsearch.image;

import org.janelia.colormipsearch.image.type.RGBPixelType;

public class RGBPixelHistogram<T extends RGBPixelType<T>> implements PixelHistogram<T> {

    private final ValuesHistogram rHistogram;
    private final ValuesHistogram gHistogram;
    private final ValuesHistogram bHistogram;
    private final T pixelValue;

    public RGBPixelHistogram(T pixelValue) {
        this.pixelValue = pixelValue;
        this.rHistogram = new ValuesHistogram(8);
        this.gHistogram = new ValuesHistogram(8);
        this.bHistogram = new ValuesHistogram(8);
    }

    private RGBPixelHistogram(T pixelValue,
                              ValuesHistogram rHistogram,
                              ValuesHistogram gHistogram,
                              ValuesHistogram bHistogram) {
        this.pixelValue = pixelValue;
        this.rHistogram = rHistogram;
        this.gHistogram = gHistogram;
        this.bHistogram = bHistogram;
    }

    @Override
    public T add(T val) {
        int maxR = rHistogram.add(val.getRed());
        int maxG = gHistogram.add(val.getGreen());
        int maxB = bHistogram.add(val.getBlue());
        pixelValue.setFromRGB(maxR, maxG, maxB);
        return pixelValue;
    }

    @Override
    public T remove(T val) {
        int maxR = rHistogram.remove(val.getRed());
        int maxG = gHistogram.remove(val.getGreen());
        int maxB = bHistogram.remove(val.getBlue());
        pixelValue.setFromRGB(maxR, maxG, maxB);
        return pixelValue;
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
        pixelValue.setFromRGB(maxR, maxG, maxB);
        return pixelValue;
    }

    @Override
    public RGBPixelHistogram<T> copy() {
        return new RGBPixelHistogram<>(pixelValue.copy(), rHistogram.copy(), gHistogram.copy(), bHistogram.copy());
    }
}
