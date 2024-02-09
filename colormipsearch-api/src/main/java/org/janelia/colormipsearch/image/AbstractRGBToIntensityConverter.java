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
                ? rgbToGrayWithGammaCorrection(r, g, b, maxValue)
                : rgbToGrayNoGammaCorrection(r, g, b, maxValue);
    }

    private static int rgbToGrayNoGammaCorrection(int r, int g, int b, float maxValue) {
        double rw = 1 / 3.;
        double gw = 1 / 3.;
        double bw = 1 / 3.;

        return (int) ((maxValue / 255) * (r*rw + g*gw + b*bw + 0.5));
    }

    private static int rgbToGrayWithGammaCorrection(int r, int g, int b, float maxValue) {
        // Normalize and gamma correct:
        float rr = (float) Math.pow(r / 255., 2.2);
        float gg = (float) Math.pow(g / 255., 2.2);
        float bb = (float) Math.pow(b / 255., 2.2);

        // Calculate luminance:
        float lum = 0.2126f * rr + 0.7152f * gg + 0.0722f * bb;
        // Gamma comp and rescale to byte range:
        return (int) (maxValue * Math.pow(lum, 1.0 / 2.2));
    }

}
