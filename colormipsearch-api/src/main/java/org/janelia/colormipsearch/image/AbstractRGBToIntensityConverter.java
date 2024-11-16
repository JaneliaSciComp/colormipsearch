package org.janelia.colormipsearch.image;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.NumericType;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public abstract class AbstractRGBToIntensityConverter<S extends RGBPixelType<S>, T extends NumericType<T>> implements Converter<S, T> {

    private final boolean withGammaCorrection;

    public AbstractRGBToIntensityConverter(boolean withGammaCorrection) {
        this.withGammaCorrection = withGammaCorrection;
    }

    @Override
    public abstract void convert(S source, T target);

    protected int getIntensity(S source, int maxValue) {
        int r = source.getRed();
        int g = source.getGreen();
        int b = source.getBlue();

        return withGammaCorrection
                ? PixelOps.rgbToGrayWithGammaCorrection(r, g, b, maxValue)
                : PixelOps.rgbToGrayNoGammaCorrection(r, g, b, maxValue);
    }

}
