package org.janelia.colormipsearch.image.type;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;

public interface RGBPixelType<T extends RGBPixelType<T> & NativeType<T>> extends NativeType<T> {
    int getRed();

    int getGreen();

    int getBlue();

    T fromARGBType(ARGBType argbType);

    default boolean isZero() {
        return getRed() == 0 && getGreen() == 0 && getBlue() == 0;
    }

    default boolean isNotZero() {
        return getRed() != 0 || getGreen() != 0 || getBlue() != 0;
    }

}
