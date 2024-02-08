package org.janelia.colormipsearch.image.type;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;

public interface RGBPixelType<T extends RGBPixelType<T> & NativeType<T> & IntegerType<T>> extends NativeType<T>, IntegerType<T> {
    int getRed();

    int getGreen();

    int getBlue();

    T createFromRGB(int r, int g, int b);

    void setFromRGB(int r, int g, int b);

    default boolean isZero() {
        return getRed() == 0 && getGreen() == 0 && getBlue() == 0;
    }

    default boolean isNotZero() {
        return getRed() != 0 || getGreen() != 0 || getBlue() != 0;
    }
}
