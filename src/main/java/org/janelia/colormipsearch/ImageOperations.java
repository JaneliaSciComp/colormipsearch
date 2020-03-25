package org.janelia.colormipsearch;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.google.common.base.Preconditions;

import ij.plugin.filter.RankFilters;
import ij.process.ImageProcessor;

class ImageOperations {

    @FunctionalInterface
    interface TriFunction<S, T, U, R> {
        R apply(S s, T t, U u);

        default <V> TriFunction<S, T, U, V> andThen(Function<? super R, ? extends V> after) {
            return (S s, T t, U u) -> after.apply(apply(s, t, u));
        }
    }

    @FunctionalInterface
    interface QuadFunction<P, S, T, U, R> {
        R apply(P p, S s, T t, U u);

        default <V> QuadFunction<P, S, T, U, V> andThen(Function<? super R, ? extends V> after) {
            return (P p, S s, T t, U u) -> after.apply(apply(p, s, t, u));
        }
    }

    abstract static class ColorTransformation implements BiFunction<MIPImage.ImageType, Integer, Integer> {
        final Function<MIPImage.ImageType, MIPImage.ImageType> pixelTypeChange;

        ColorTransformation(Function<MIPImage.ImageType, MIPImage.ImageType> pixelTypeChange) {
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

        private static int maskRGB(int val, int threshold) {
            int r = (val >> 16) & 0xFF;
            int g = (val >> 8) & 0xFF;
            int b = (val & 0xFF);

            if (r <= threshold && g <= threshold && b <= threshold)
                return -16777216; // alpha mask
            else
                return val;
        }

        private static int rgbToGray(int rgb, float maxGrayValue) {
            if (rgb == -16777216) {
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
                // Gamma compand and rescale to byte range:
                return (int) (maxGrayValue * Math.pow(lum, 1.0 / 2.2));
            }
        }

        private static int scaleGray(int gray, float oldMax, float newMax) {
            return (int) (gray / oldMax * newMax);
        }

        static ColorTransformation toGray8() {
            return new ColorTransformation(pt -> MIPImage.ImageType.GRAY8) {
                @Override
                public Integer apply(MIPImage.ImageType pt, Integer pv) {
                    switch (pt) {
                        case RGB:
                            return rgbToGray(pv, 255);
                        case GRAY8:
                            return pv;
                        case GRAY16:
                            return scaleGray(pv, 65535, 255);
                    }
                    throw new IllegalStateException("Cannot convert " + pt + " to gray8");
                }
            };
        }

        static ColorTransformation toGray16() {
            return new ColorTransformation(pt -> MIPImage.ImageType.GRAY16) {
                @Override
                public Integer apply(MIPImage.ImageType pt, Integer pv) {
                    switch (pt) {
                        case RGB:
                            return rgbToGray(pv, 65535);
                        case GRAY8:
                            return scaleGray(pv, 255, 65535);
                        case GRAY16:
                            return pv;
                    }
                    throw new IllegalStateException("Cannot convert " + pt + " to gray16");
                }
            };
        }

        static ColorTransformation mask(int threshold) {
            return new ColorTransformation(pt -> pt) {
                @Override
                public Integer apply(MIPImage.ImageType pt, Integer pv) {
                    switch (pt) {
                        case RGB:
                            return maskRGB(pv, threshold);
                        case GRAY8:
                        case GRAY16:
                            return maskGray(pv, threshold);
                    }
                    throw new IllegalStateException("Cannot mask image type " + pt);
                }
            };
        }

        static ColorTransformation toBinary16(int threshold) {
            return ColorTransformation.toGray16().thenApplyColorTransformation(pv -> ColorTransformation.grayToBinary16(pv, threshold));
        }

        static ColorTransformation toBinary8(int threshold) {
            return ColorTransformation.toGray8().thenApplyColorTransformation(pv -> ColorTransformation.grayToBinary8(pv, threshold));
        }

        static ColorTransformation toSignal() {
            return new ColorTransformation(pt -> pt) {
                @Override
                public Integer apply(MIPImage.ImageType pt, Integer pv) {
                    switch (pt) {
                        case RGB:
                            int r = ((pv >> 16) & 0xFF) > 0 ? 1 : 0;
                            int g = ((pv >> 8) & 0xFF) > 0 ? 1 : 0;
                            int b = (pv & 0xFF) > 0 ? 1 : 0;
                            return r > 0 || g > 0 || b > 0 ? (r << 16) | (g << 8) | b : -16777216;
                        case GRAY8:
                        case GRAY16:
                            return pv > 0 ? 1 : 0;
                    }
                    throw new IllegalStateException("Cannot convert image type " + pt + " to signal");
                }
            };
        }

        ColorTransformation thenApplyColorTransformation(Function<Integer, Integer> colorTransformation) {
            ColorTransformation currentTransformation = this;
            return new ColorTransformation(pixelTypeChange) {
                @Override
                public Integer apply(MIPImage.ImageType pt, Integer pv) {
                    return colorTransformation.apply(currentTransformation.apply(pt, pv));
                }
            };
        }
    }

    abstract static class PixelTransformation implements QuadFunction<Integer, Integer, MIPImage.ImageType, Integer, Integer> {
        final Function<MIPImage.ImageType, MIPImage.ImageType> pixelTypeChange;

        static Function<LImage, PixelTransformation> toIdentity() {
            return lImage -> new PixelTransformation(pt -> pt) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    return pv;
                }
            };
        }

        static Function<LImage, PixelTransformation> toMirror() {
            return lImage -> lImage.mirror().pf;
        }

        PixelTransformation(Function<MIPImage.ImageType, MIPImage.ImageType> pixelTypeChange) {
            this.pixelTypeChange = pixelTypeChange;
        }

    }

    private interface ColorHistogram {
        /**
         * Add a value and return the new max
         * @param val
         * @return the new max value
         */
        int add(int val);
        /**
         * Remove the value and return the new max
         * @param val
         * @return the new max value
         */
        int remove(int val);
        void clear();
    }

    private static final class RGBHistogram implements ColorHistogram {
        private final Gray8Histogram rHistogram;
        private final Gray8Histogram gHistogram;
        private final Gray8Histogram bHistogram;

        public RGBHistogram() {
            this.rHistogram = new Gray8Histogram();
            this.gHistogram = new Gray8Histogram();
            this.bHistogram = new Gray8Histogram();
        }

        @Override
        public int add(int val) {
            int maxR = rHistogram.add(val >> 16);
            int maxG = gHistogram.add(val >> 8);
            int maxB = bHistogram.add(val);
            return getColor(maxR, maxG, maxB);
        }

        private int getColor(int r, int g, int b) {
            return 0xFF000000 |
                    r << 16 |
                    g << 8 |
                    b;
        }

        @Override
        public int remove(int val) {
            int maxR = rHistogram.remove(val >> 16);
            int maxG = gHistogram.remove(val >> 8);
            int maxB = bHistogram.remove(val);
            return getColor(maxR, maxG, maxB);
        }

        @Override
        public void clear() {
            rHistogram.clear();
            gHistogram.clear();
            bHistogram.clear();
        }
    }

    private static final class Gray8Histogram implements ColorHistogram {

        private final int[] histogram;
        private int max;
        private int count;

        Gray8Histogram() {
            histogram = new int[256];
            max = -1;
            count = 0;
        }

        @Override
        public int add(int val) {
            int ci = val & 0xFF;
            histogram[ci] = histogram[ci] + 1;
            count++;
            if (ci > max) {
                max = ci;
            }
            return max;
        }

        @Override
        public int remove(int val) {
            int ci = val & 0xFF;
            count--;
            histogram[ci] = histogram[ci] - 1;
            Preconditions.checkArgument(histogram[ci] >= 0);
            Preconditions.checkArgument(count >= 0);
            if (ci == max) {
                if (count == 0) {
                    max = -1;
                } else if (histogram[max] == 0) {
                    max = -1;
                    for (int pv = ci - 1; pv >= 0; pv--) {
                        if (histogram[pv] > 0) {
                            max = pv;
                            break;
                        }
                    }
                }
            }
            return max;
        }

        @Override
        public void clear() {
            Arrays.fill(histogram, 0);
            count = 0;
            max = -1;
        }
    }

    static class LImage {
        private final PixelTransformation pf;
        private final MIPImage image;

        LImage(PixelTransformation pf, MIPImage image) {
            this.pf = pf;
            this.image = image;
        }

        MIPImage.ImageType getPixelType() {
            return pf.pixelTypeChange.apply(image.type);
        }

        int get(int x, int y) {
            return pf.apply(x, y, image.type, image.getPixel(x, y));
        }

        int width() {
            return this.image.width;
        }

        int height() {
            return this.image.height;
        }

        LImage map(ColorTransformation pf1) {
            return new LImage(new PixelTransformation(pf.pixelTypeChange.andThen(pf1.pixelTypeChange)) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    return pf1.apply(pf.pixelTypeChange.apply(pt), pf.apply(x, y, pt, pv));
                }
            }, image);
        }

        LImage mapi(PixelTransformation pf1) {
            return new LImage(new PixelTransformation(pf.pixelTypeChange.andThen(pf1.pixelTypeChange)) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    return pf1.apply(x, y, pt, pf.apply(x, y, this.pixelTypeChange.apply(pt), pv));
                }
            }, image);
        }

        LImage combineWith(LImage lImage, BinaryOperator<Integer> op) {
            return mapi(new PixelTransformation(pt -> pt) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    return op.apply(pv, lImage.get(x, y));
                }
            });
        }

        LImage combineWith(LImage lImage1, LImage lImage2, TriFunction<Integer, Integer, Integer, Integer> op) {
            return mapi(new PixelTransformation(pt -> pt) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    return op.apply(pv, lImage1.get(x, y), lImage2.get(x, y));
                }
            });
        }

        LImage mirror() {
            return new LImage(new PixelTransformation(pf.pixelTypeChange) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    return pf.apply(x, y, pt, get(width() - x - 1, y));
                }
            }, image);
        }

        LImage maxWithBoxPattern(double radius) {
            int kRadius = (int)Math.abs(radius);
            return new LImage(new PixelTransformation(pf.pixelTypeChange) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    int m = IntStream.rangeClosed(-kRadius, kRadius)
                            .map(dy -> y + dy)
                            .filter(ay -> ay >= 0 & ay < image.height)
                            .flatMap(ay -> IntStream.rangeClosed(-kRadius, kRadius)
                                    .map(dx -> x + dx)
                                    .filter(ax -> ax >= 0 && ax < image.width)
                                    .map(ax -> get(ax, ay)))
                            .reduce(Math::max)
                            .orElse(pv)
                            ;
                    return pf.apply(x, y, pt, m);
                }
            }, image);
        }

        LImage maxWithDiscPatternAndSlidingWindow(double radius) {
            return new LImage(new PixelTransformation(pf.pixelTypeChange) {
                final int[] radii = makeLineRadii(radius);
                final int kRadius = radii[radii.length - 1];
                final int kHeight = (radii.length - 1) / 2;
                final int[] imageCache = new int[kHeight*width()];
                final ColorHistogram histogram = getPixelType() == MIPImage.ImageType.RGB ? new RGBHistogram() : new Gray8Histogram();

                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    int m = -1;
                    if (x == 0) {
                        histogram.clear();
                        Arrays.fill(imageCache, 0);
                        for (int h = 0; h < kHeight; h++) {
                            int ay = y - kRadius + h;
                            if (ay >= 0 && ay < height()) {
                                for (int dx = 0; dx < radii[2 * h + 1]; dx++) {
                                    int ax = x + dx;
                                    if (ax < width()) {
                                        int p = get(ax, ay);
                                        imageCache[h * width() + ax] = p;
                                        m = histogram.add(p);
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    for (int h = 0; h < kHeight; h++) {
                        int ay = y - kRadius + h;
                        int nextx = x + radii[2 * h + 1];
                        int prevx = x + radii[2 * h];
                        if (ay >= 0 && ay < height()) {
                            if (nextx < width()) {
                                int p = get(nextx, ay);
                                imageCache[h * width() + nextx] = p;
                                m = histogram.add(p);
                            }
                            if (prevx > 0) {
                                m = histogram.remove(imageCache[h * width() + prevx - 1]);
                            }
                        }
                    }

                    return pf.apply(x, y, pt, m);
                }
            }, image);
        }

        LImage maxWithDiscPattern(double radius) {
            int[] radii = makeLineRadii(radius);
            int kRadius = radii[radii.length - 1];
            int kHeight = (radii.length - 1) / 2;

            return new LImage(new PixelTransformation(pf.pixelTypeChange) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    int m = pv;
                    BinaryOperator<Integer> maxOp = (p1, p2) -> {
                        switch (pt) {
                            case RGB:
                                int a = Math.max(((p1 >> 24) & 0xFF), ((p2 >> 24) & 0xFF));
                                int r = Math.max(((p1 >> 16) & 0xFF), ((p2 >> 16) & 0xFF));
                                int g = Math.max(((p1 >> 8) & 0xFF), ((p2 >> 8) & 0xFF));
                                int b = Math.max((p1 & 0xFF), (p2 & 0xFF));
                                return (a << 24) | (r << 16) | (g << 8) | b;
                            case GRAY8:
                            case GRAY16:
                            default:
                                return Math.max(p1, p2);
                        }
                    };
                    for (int h = 0; h < kHeight; h++) {
                        int ay = y + h - kRadius;
                        if (ay < 0 || ay >= height()) {
                            continue;
                        }
                        for (int ax = x + radii[2*h]; ax <= x + radii[2*h+1]; ax++) {
                            if (ax >= 0 && ax < width()) {
                                m = maxOp.apply(m, get(ax, ay));
                            }
                        }
                    }
                    return pf.apply(x, y, pt, m);
                }
            }, image);
        }

        private int[] makeLineRadii(double radiusArg) {
            double radius;
            if (radiusArg >= 1.5 && radiusArg < 1.75) //this code creates the same sizes as the previous RankFilters
                radius = 1.75;
            else if (radiusArg >= 2.5 && radiusArg < 2.85)
                radius = 2.85;
            else
                radius = radiusArg;
            int r2 = (int) (radius * radius) + 1;
            int kRadius = (int) (Math.sqrt(r2 + 1e-10));
            int kHeight = 2 * kRadius + 1;
            int[] kernel = new int[2 * kHeight + 1];
            kernel[2 * kRadius] = -kRadius;
            kernel[2 * kRadius + 1] = kRadius;
            for (int y = 1; y <= kRadius; y++) {        //lines above and below center together
                int dx = (int) (Math.sqrt(r2 - y * y + 1e-10));
                kernel[2 * (kRadius - y)] = -dx;
                kernel[2 * (kRadius - y) + 1] = dx;
                kernel[2 * (kRadius + y)] = -dx;
                kernel[2 * (kRadius + y) + 1] = dx;
            }
            kernel[kernel.length - 1] = kRadius;
            return kernel;
        }

        <R> R fold(R initialValue, BiFunction<Integer, R, R> acumulator) {
            R res = initialValue;
            int imageHeight = height();
            int imageWidth = width();
            for (int y = 0; y < imageHeight; y++) {
                for (int x = 0; x < imageWidth; x++) {
                    res = acumulator.apply(get(x, y), res);
                }
            }
            return res;
        }

        <R> R foldi(R initialValue, QuadFunction<Integer, Integer, Integer, R, R> acumulator) {
            R res = initialValue;
            int imageHeight = height();
            int imageWidth = width();
            for (int y = 0; y < imageHeight; y++) {
                for (int x = 0; x < imageWidth; x++) {
                    res = acumulator.apply(x, y, get(x, y), res);
                }
            }
            return res;
        }

        MIPImage asImage() {
            int[] pixels = new int[width() * height()];
            return new MIPImage(image.mipInfo, width(), height(), getPixelType(), foldi(pixels, (x, y, pv, pa) -> {pa[y * width() + x] = pv; return pa;}));
        }
    }

    static class ImageProcessing {

        static ImageProcessing createFor(MIPImage mipImage) {
            return new ImageProcessing(new LImage(new PixelTransformation(pt -> pt) {
                @Override
                public Integer apply(Integer x, Integer y, MIPImage.ImageType pt, Integer pv) {
                    return pv;
                }
            }, mipImage));
        }

        private final LImage lImage;

        private ImageProcessing(LImage lImage) {
            this.lImage = lImage;
        }

        ImageProcessing mask(int threshold) {
            return new ImageProcessing(lImage.map(ColorTransformation.mask(threshold)));
        }

        ImageProcessing toGray16() {
            return new ImageProcessing(lImage.map(ColorTransformation.toGray16()));
        }

        ImageProcessing toBinary8(int threshold) {
            return new ImageProcessing(lImage.map(ColorTransformation.toBinary8(threshold)));
        }

        ImageProcessing toBinary16(int threshold) {
            return new ImageProcessing(lImage.map(ColorTransformation.toBinary16(threshold)));
        }

        ImageProcessing maxWithDiscPattern(double radius) {
            return new ImageProcessing(lImage.maxWithDiscPatternAndSlidingWindow(radius));
        }

        ImageProcessing maxWithBoxPattern(double radius) {
            return new ImageProcessing(lImage.maxWithBoxPattern(radius));
        }

        ImageProcessing ijMaxFilter(double radius) {
            MIPImage image = asImage();
            RankFilters maxFilter = new RankFilters();
            ImageProcessor imageProcessor = image.getImageProcessor();
            maxFilter.rank(imageProcessor, radius, RankFilters.MAX);
            System.arraycopy(imageProcessor.getPixels(), 0, image.pixels, 0, image.pixels.length);
            return ImageProcessing.createFor(image);
        }

        ImageProcessing mirror() {
            return new ImageProcessing(lImage.mirror());
        }

        ImageProcessing toSignal() {
            return new ImageProcessing(lImage.map(ColorTransformation.toSignal()));
        }

        ImageProcessing combineWith(ImageProcessing processing, BinaryOperator<Integer> op) {
            return new ImageProcessing(lImage.combineWith(processing.lImage, op));
        }

        ImageProcessing combineWith(ImageProcessing p1, ImageProcessing p2, TriFunction<Integer, Integer, Integer, Integer> op) {
            return new ImageProcessing(lImage.combineWith(p1.lImage, p2.lImage, op));
        }

        ImageProcessing compose(Function<LImage, PixelTransformation> processing) {
            return new ImageProcessing(lImage.mapi(processing.apply(lImage)));
        }

        <R> R fold(R initialValue, BiFunction<Integer, R, R> acumulator) {
            return lImage.fold(initialValue, acumulator);
        }

        MIPImage asImage() {
            return lImage.asImage();
        }
    }
}