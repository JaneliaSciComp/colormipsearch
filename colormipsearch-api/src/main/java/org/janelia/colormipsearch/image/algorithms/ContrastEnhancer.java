package org.janelia.colormipsearch.image.algorithms;

import java.util.function.Supplier;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.ImageAccessUtils;

public class ContrastEnhancer {
    public static <T extends IntegerType<T>> void stretchHistogram(RandomAccessibleInterval<T> img,
                                                                   Supplier<T> pixelSupplier,
                                                                   double saturationLimit,
                                                                   int minIntensityParam,
                                                                   int maxIntensityParam,
                                                                   int nbins) {
        ImageAccessUtils.ContrastStretchingParams params = ImageAccessUtils.computeContrastStretchingParams(
                img, saturationLimit, minIntensityParam, maxIntensityParam, nbins);
        if (params.threshold <= params.minIntensity) return; // nothing to do
        Cursor<T> imgCursor = Views.flatIterable(img).cursor();
        while (imgCursor.hasNext()) {
            T pixel = imgCursor.next();
            int value = pixel.getInteger();
            if (value > 0) {
                if (value >= params.threshold) {
                    pixel.setInteger(params.maxIntensity);
                } else {
                    double scaledValue = ((double) params.maxIntensity * (value - params.defaultMinIntensity)) / (double) (params.threshold - params.defaultMinIntensity);
                    pixel.setInteger((int) scaledValue);
                }
            }
        }

    }
}
