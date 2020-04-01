package org.janelia.colormipsearch.imageprocessing;

import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import com.google.common.base.Preconditions;

public abstract class ImageTransformation {

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

    public static ImageTransformation identity() {
        return new ImageTransformation() {
            @Override
            public int apply(int x, int y, LImage lImage) {
                return lImage.get(x, y);
            }
        };
    }

    public static ImageTransformation horizontalMirror() {
        return new ImageTransformation() {
            @Override
            public int apply(int x, int y, LImage lImage) {
                return lImage.get(lImage.width() - x - 1, y);
            }
        };
    }

    static ImageTransformation maxWithDiscPattern(double radius) {
        return new ImageTransformation() {
            private final int[] radii = makeLineRadii(radius);
            private final int kRadius = radii[radii.length - 1];
            private final int kHeight = (radii.length - 1) / 2;

            @Override
            public int apply(int x, int y, LImage lImage) {
                ColorHistogram histogram;
                int[] imageCache;
                if (lImage.imageProcessingContext.get("histogram") == null) {
                    histogram = lImage.getPixelType() == ImageType.RGB ? new RGBHistogram() : new Gray8Histogram();
                    lImage.imageProcessingContext.set("histogram", histogram);
                    imageCache = new int[kHeight*lImage.width()];
                    lImage.imageProcessingContext.set("imageCache", imageCache);
                } else {
                    histogram = (ColorHistogram) lImage.imageProcessingContext.get("histogram");
                    imageCache = (int[]) lImage.imageProcessingContext.get("imageCache");
                }
                int m = -1;
                if (x == 0) {
                    histogram.clear();
                    Arrays.fill(imageCache, 0);
                    for (int h = 0; h < kHeight; h++) {
                        int ay = y - kRadius + h;
                        if (ay >= 0 && ay < lImage.height()) {
                            for (int dx = 0; dx < radii[2 * h + 1]; dx++) {
                                int ax = x + dx;
                                if (ax < lImage.width()) {
                                    int p = lImage.get(ax, ay);
                                    imageCache[h * lImage.width() + ax] = p;
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
                    if (ay >= 0 && ay < lImage.height()) {
                        if (nextx < lImage.width()) {
                            int p = lImage.get(nextx, ay);
                            imageCache[h * lImage.width() + nextx] = p;
                            m = histogram.add(p);
                        }
                        if (prevx > 0) {
                            m = histogram.remove(imageCache[h * lImage.width() + prevx - 1]);
                        }
                    }
                }
                return m;
            }
        };
    }

    private static int[] makeLineRadii(double radiusArg) {
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

    static ImageTransformation combine2(ImageTransformation it1, ImageTransformation it2, BinaryOperator<Integer> op) {
        return new ImageTransformation(
                it1.pixelTypeChange.andThen(it2.pixelTypeChange)) {
            @Override
            public int apply(int x, int y, LImage lImage) {
                return op.apply(it1.apply(x, y, lImage), it2.apply(x, y, lImage));
            }
        };
    }

    static ImageTransformation combine3(ImageTransformation it1, ImageTransformation it2, ImageTransformation it3, TriFunction<Integer, Integer, Integer, Integer> op) {
        return new ImageTransformation(
                it1.pixelTypeChange.andThen(it2.pixelTypeChange).andThen(it3.pixelTypeChange)) {
            @Override
            public int apply(int x, int y, LImage lImage) {
                return op.apply(it1.apply(x, y, lImage), it2.apply(x, y, lImage), it3.apply(x, y, lImage));
            }
        };
    }

    final Function<ImageType, ImageType> pixelTypeChange;

    ImageTransformation() {
        this(Function.identity());
    }

    ImageTransformation(Function<ImageType, ImageType> pixelTypeChange) {
        this.pixelTypeChange = pixelTypeChange;
    }

    ImageTransformation andThen(ImageTransformation pixelTransformation) {
        ImageTransformation currentTransformation = this;
        return new ImageTransformation(
                pixelTypeChange.andThen(pixelTransformation.pixelTypeChange)) {

            @Override
            public int apply(int x, int y, LImage lImage) {
                LImage updatedImage;
                String updatedImageKey = "updatedBy" + currentTransformation.hashCode();
                if (lImage.imageProcessingContext.get(updatedImageKey) == null) {
                    updatedImage = lImage.mapi(currentTransformation);
                    lImage.imageProcessingContext.set(updatedImageKey, updatedImage);
                } else {
                    updatedImage = (LImage) lImage.imageProcessingContext.get(updatedImageKey);
                }
                return pixelTransformation.apply(x, y, updatedImage);
            }
        };
    }

    ImageTransformation andThen(ColorTransformation colorTransformation) {
        ImageTransformation currentTransformation = this;
        return new ImageTransformation(pixelTypeChange.andThen(colorTransformation.pixelTypeChange)) {
            @Override
            int apply(int x, int y, LImage lImage) {
                int p = currentTransformation.apply(x, y, lImage);
                ImageType pt = currentTransformation.pixelTypeChange.apply(lImage.getPixelType());
                return colorTransformation.apply(pt, p);
            }
        };
    }

    abstract int apply(int x, int y, LImage lImage);
}