package org.janelia.colormipsearch.imageprocessing;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * ColorTransformation - transformations that converts a pixel value into another pixel value
 * based only on the pixel type and pixel value, irrespective of the pixel position.
 */
public abstract class ColorTransformation implements BiFunction<ImageType, Integer, Integer> {
    final Function<ImageType, ImageType> pixelTypeChange;

    protected ColorTransformation(Function<ImageType, ImageType> pixelTypeChange) {
        this.pixelTypeChange = pixelTypeChange;
    }

    private static int maskGray(int val, int threshold) {
        return val <= threshold ? 0 : val;
    }

    private static int grayToBinary8(int val, int threshold) {
        return val <= threshold ? 0 : 255;
    }

    private static int grayToBinary16(int val, int threshold) {
        return val <= threshold ? 0 : 65535;
    }

    private static int maskRGB(int val, int threshold, int maskedVal) {
        int r = (val >> 16) & 0xFF;
        int g = (val >> 8) & 0xFF;
        int b = (val & 0xFF);

        if (val != maskedVal && r <= threshold && g <= threshold && b <= threshold)
            return maskedVal;
        else
            return val;
    }

    private static int rgbToGrayNoGammaCorrection(int rgb, float maxGrayValue) {
        if ((rgb & 0xFFFFFF) == 0) {
            return 0;
        } else {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = (rgb & 0xFF);

            double rw = 1 / 3.;
            double gw = 1 / 3.;
            double bw = 1 / 3.;

            return (int) ((maxGrayValue / 255) * (r*rw + g*gw + b*bw + 0.5));
        }
    }

    private static int rgbToGrayWithGammaCorrection(int rgb, float maxGrayValue) {
        if ((rgb & 0xFFFFFF) == 0) {
            return 0;
        } else {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = (rgb & 0xFF);

            // Normalize and gamma correct:
            float rr = (float) Math.pow(r / 255., 2.2);
            float gg = (float) Math.pow(g / 255., 2.2);
            float bb = (float) Math.pow(b / 255., 2.2);

            // Calculate luminance:
            float lum = 0.2126f * rr + 0.7152f * gg + 0.0722f * bb;
            // Gamma comp and rescale to byte range:
            return (int) (maxGrayValue * Math.pow(lum, 1.0 / 2.2));
        }
    }

    private static int scaleGray(int gray, float oldMax, float newMax) {
        return (int) (gray / oldMax * newMax);
    }

    static ColorTransformation toGray8WithNoGammaCorrection() {
        return new ColorTransformation(pt -> ImageType.GRAY8) {
            @Override
            public Integer apply(ImageType pt, Integer pv) {
                switch (pt) {
                    case RGB:
                        return ColorTransformation.rgbToGrayNoGammaCorrection(pv, 255);
                    case GRAY8:
                        return pv;
                    case GRAY16:
                        return scaleGray(pv, 65535, 255);
                }
                throw new IllegalStateException("Cannot convert " + pt + " to gray8");
            }
        };
    }

    public static ColorTransformation toGray16WithNoGammaCorrection() {
        return new ColorTransformation(pt -> ImageType.GRAY16) {
            @Override
            public Integer apply(ImageType pt, Integer pv) {
                switch (pt) {
                    case RGB:
                        return ColorTransformation.rgbToGrayNoGammaCorrection(pv, 255);
                    case GRAY8:
                        return scaleGray(pv, 255, 65535);
                    case GRAY16:
                        return pv;
                }
                throw new IllegalStateException("Cannot convert " + pt + " to gray16");
            }
        };
    }

    public static ColorTransformation mask(int threshold) {
        return mask(threshold, 0xFF000000);
    }

    public static ColorTransformation mask(int threshold, int maskedVal) {
        return new ColorTransformation(pt -> pt) {
            @Override
            public Integer apply(ImageType pt, Integer pv) {
                switch (pt) {
                    case RGB:
                        return maskRGB(pv, threshold, maskedVal);
                    case GRAY8:
                    case GRAY16:
                        return maskGray(pv, threshold);
                }
                throw new IllegalStateException("Cannot mask image type " + pt);
            }
        };
    }

    public static int mask(ImageType pt, int p, int m) {
        if ((m & 0xFFFFFF) == 0) {
            switch (pt) {
                case RGB: return 0xFF000000;
                default: return 0;
            }
        } else {
            return p;
        }
    }

    static ColorTransformation toBinary16(int threshold) {
        return ColorTransformation.toGray16WithNoGammaCorrection().thenApplyColorTransformation(pv -> ColorTransformation.grayToBinary16(pv, threshold));
    }

    static ColorTransformation toBinary8(int threshold) {
        return ColorTransformation.toGray8WithNoGammaCorrection().thenApplyColorTransformation(pv -> ColorTransformation.grayToBinary8(pv, threshold));
    }

    public static ColorTransformation gray8Or16ToSignal(int threshold) {
        return new ColorTransformation(pt -> pt) {
            @Override
            public Integer apply(ImageType pt, Integer pv) {
                int grayPixelValue = ColorTransformation.rgbToGrayNoGammaCorrection(pv, 255);
                return grayPixelValue > threshold ? 1 : 0;
            }
        };
    }

    ColorTransformation thenApplyColorTransformation(Function<Integer, Integer> colorTransformation) {
        ColorTransformation currentTransformation = this;
        return new ColorTransformation(pixelTypeChange) {
            @Override
            public Integer apply(ImageType pt, Integer pv) {
                return colorTransformation.apply(currentTransformation.apply(pt, pv));
            }
        };
    }
}
