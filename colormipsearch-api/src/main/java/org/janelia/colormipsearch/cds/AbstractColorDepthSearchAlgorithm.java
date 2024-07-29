package org.janelia.colormipsearch.cds;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.BiConverter;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.colormipsearch.image.GeomTransform;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.QuadConverter;
import org.janelia.colormipsearch.image.RGBPixelHistogram;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common methods that can be used by various ColorDepthQuerySearchAlgorithm implementations.
 * @param <S> score type
 */
public abstract class AbstractColorDepthSearchAlgorithm<S extends ColorDepthMatchScore> implements ColorDepthSearchAlgorithm<S> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractColorDepthSearchAlgorithm.class);

    final int queryThreshold;
    final int targetThreshold;
    final boolean withMirrorFlag;

    protected AbstractColorDepthSearchAlgorithm(int queryThreshold, int targetThreshold, boolean withMirrorFlag) {
        this.queryThreshold = queryThreshold;
        this.targetThreshold = targetThreshold;
        this.withMirrorFlag = withMirrorFlag;
    }

    @SuppressWarnings("unchecked")
    <P extends IntegerType<P>> RandomAccessibleInterval<P> getVariantImage(ComputeVariantImageSupplier<? extends IntegerType<?>> variantImageSupplier,
                                                                           ComputeVariantImageSupplier<? extends IntegerType<?>> defaultImageSupplier) {
        if (variantImageSupplier != null) {
            LOG.debug("Get Image supplier for {}", variantImageSupplier.getName());
            return (RandomAccessibleInterval<P>) variantImageSupplier.getImage();
        } else if (defaultImageSupplier != null) {
            LOG.debug("Get default mage supplier for {}", defaultImageSupplier.getName());
            return (RandomAccessibleInterval<P>) defaultImageSupplier.getImage();
        } else {
            return null;
        }
    }

    ComputeVariantImageSupplier<? extends IntegerType<?>> getFirstFoundVariant(
            List<ComputeVariantImageSupplier<? extends IntegerType<?>>> variantImageSuppliers
    ) {
        for (ComputeVariantImageSupplier<? extends IntegerType<?>> variantImageSupplier : variantImageSuppliers) {
            if (variantImageSupplier != null) {
                return variantImageSupplier;
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
        P sourcePxType = (P) img.randomAccess().get().createVariable();
        return ImageTransforms.maskPixelsUsingMaskImage((RandomAccessibleInterval<P>)img, (RandomAccessibleInterval<M>)mask, sourcePxType);
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
