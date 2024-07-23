package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.BiConverter;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.GeomTransform;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.QuadConverter;
import org.janelia.colormipsearch.image.RGBPixelHistogram;
import org.janelia.colormipsearch.image.type.RGBPixelType;

/**
 * Common methods that can be used by various ColorDepthQuerySearchAlgorithm implementations.
 * @param <S> score type
 */
public abstract class AbstractColorDepthSearchAlgorithm<S extends ColorDepthMatchScore> implements ColorDepthSearchAlgorithm<S> {

    final int queryThreshold;
    final int targetThreshold;
    final boolean withMirrorFlag;

    protected AbstractColorDepthSearchAlgorithm(int queryThreshold, int targetThreshold, boolean withMirrorFlag) {
        this.queryThreshold = queryThreshold;
        this.targetThreshold = targetThreshold;
        this.withMirrorFlag = withMirrorFlag;
    }

    @SuppressWarnings("unchecked")
    <P extends IntegerType<P>> RandomAccessibleInterval<P> getVariantImage(Supplier<RandomAccessibleInterval<? extends IntegerType<?>>> variantImageSupplier,
                                                                           Supplier<RandomAccessibleInterval<? extends IntegerType<?>>> defaultImageSupplier) {
        if (variantImageSupplier != null) {
            return (RandomAccessibleInterval<P>) variantImageSupplier.get();
        } else if (defaultImageSupplier != null) {
            return (RandomAccessibleInterval<P>) defaultImageSupplier.get();
        } else {
            return null;
        }
    }

    RandomAccessibleInterval<? extends IntegerType<?>> getFirstReifiableVariant(
            List<Supplier<RandomAccessibleInterval<? extends IntegerType<?>>>> variantImageSuppliers
    ) {
        for (Supplier<RandomAccessibleInterval<? extends IntegerType<?>>> variantImageSupplier : variantImageSuppliers) {
            if (variantImageSupplier != null) {
                return variantImageSupplier.get();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    <P extends IntegerType<P>> RandomAccessibleInterval<P> applyTransformToImage(RandomAccessibleInterval<? extends IntegerType<?>> img,
                                                                                  GeomTransform transform) {
        return ImageTransforms.createGeomTransformation((RandomAccessibleInterval<P>) img, transform);
    }

    @SuppressWarnings("unchecked")
    <P extends RGBPixelType<P>> RandomAccessibleInterval<P> applyRGBThreshold(RandomAccessibleInterval<? extends RGBPixelType<?>> img, int threshold) {
        return ImageTransforms.maskRGBPixelsBelowThreshold((RandomAccessibleInterval<P>) img, threshold);
    }

    @SuppressWarnings("unchecked")
    <P extends IntegerType<P>> RandomAccessibleInterval<P> applyMaskCond(RandomAccessibleInterval<? extends IntegerType<?>> img, BiPredicate<long[], ? extends IntegerType<?>> cond) {
        return ImageTransforms.maskPixelsMatchingCond((RandomAccessibleInterval<P>) img, (BiPredicate<long[], P>)cond, null);
    }

    @SuppressWarnings("unchecked")
    <P extends IntegerType<P>, M extends IntegerType<M>> RandomAccessibleInterval<P> applyMask(RandomAccessibleInterval<? extends IntegerType<?>> img,
                                                                                               RandomAccessibleInterval<? extends IntegerType<?>> mask) {
        return ImageTransforms.maskPixelsUsingMaskImage((RandomAccessibleInterval<P>)img, (RandomAccessibleInterval<M>)mask, null);
    }

    @SuppressWarnings("unchecked")
    <P extends RGBPixelType<P>> RandomAccessibleInterval<UnsignedByteType> rgb2Signal(RandomAccessibleInterval<? extends RGBPixelType<?>> img, int threshold) {
        return ImageTransforms.rgbToSignalTransformation((RandomAccessibleInterval<P>) img, threshold);
    }

    @SuppressWarnings("unchecked")
    <P extends RGBPixelType<P>> RandomAccessibleInterval<P> getDilation(RandomAccessibleInterval<? extends RGBPixelType<?>> img, int radius) {
        int[] radii = new int[img.numDimensions()];
        Arrays.fill(radii, radius);
        P pxType = (P) img.randomAccess().get().createVariable();
        return ImageTransforms.dilateImage(
                (RandomAccessibleInterval<P>) img,
                () -> new RGBPixelHistogram<>(pxType),
                radii
        );
    }

    @SuppressWarnings("unchecked")
    <P extends IntegerType<P>, Q extends IntegerType<Q>, T extends IntegerType<T>>
    RandomAccessibleInterval<T> applyBinaryOp(RandomAccessibleInterval<? extends IntegerType<?>> img1,
                                              RandomAccessibleInterval<? extends IntegerType<?>> img2,
                                              BiConverter<? super P, ? super Q, ? super T> op,
                                              T resultPxType) {
        return ImageTransforms.createBinaryPixelOperation(
                (RandomAccessibleInterval<P>)img1,
                (RandomAccessibleInterval<Q>)img2,
                op,
                resultPxType
        );

    }

    @SuppressWarnings("unchecked")
    <P extends IntegerType<P>, Q extends IntegerType<Q>, R extends IntegerType<R>, S extends IntegerType<S>, T extends IntegerType<T>>
    RandomAccessibleInterval<T> applyQuadOp(RandomAccessibleInterval<? extends IntegerType<?>> img1,
                                            RandomAccessibleInterval<? extends IntegerType<?>> img2,
                                            RandomAccessibleInterval<? extends IntegerType<?>> img3,
                                            RandomAccessibleInterval<? extends IntegerType<?>> img4,
                                            QuadConverter<? super P, ? super Q, ? super R, ? super S, ? super T> op,
                                            T resultPxType) {
        return ImageTransforms.createQuadPixelOperation(
                (RandomAccessibleInterval<P>)img1,
                (RandomAccessibleInterval<Q>)img2,
                (RandomAccessibleInterval<R>)img3,
                (RandomAccessibleInterval<S>)img4,
                op,
                resultPxType
        );

    }
}
