package org.janelia.colormipsearch.image;

import org.janelia.colormipsearch.image.type.RGBPixelType;

public class RGBPixelHistogram<T extends RGBPixelType<T>> implements PixelHistogram<T> {

    private final Gray8PixelHistogram rHistogram;
    private final Gray8PixelHistogram gHistogram;
    private final Gray8PixelHistogram bHistogram;
    private final T pixelValue;

    public RGBPixelHistogram(T pixelValue) {
        this.pixelValue = pixelValue;
        this.rHistogram = new Gray8PixelHistogram();
        this.gHistogram = new Gray8PixelHistogram();
        this.bHistogram = new Gray8PixelHistogram();
    }

    private RGBPixelHistogram(T pixelValue,
                              Gray8PixelHistogram rHistogram,
                              Gray8PixelHistogram gHistogram,
                              Gray8PixelHistogram bHistogram) {
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
