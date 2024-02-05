package org.janelia.colormipsearch.image.type;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;

public interface RGBPixelType<T extends RGBPixelType<T> & NativeType<T> & NumericType<T>> extends NativeType<T>, NumericType<T> {
    int getRed();

    int getGreen();

    int getBlue();

    T fromRGB(int r, int g, int b);

    default boolean isZero() {
        return getRed() == 0 && getGreen() == 0 && getBlue() == 0;
    }

    default boolean isNotZero() {
        return getRed() != 0 || getGreen() != 0 || getBlue() != 0;
    }
}
