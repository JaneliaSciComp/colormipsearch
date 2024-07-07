package org.janelia.colormipsearch.cmd;

import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.imglib2.type.numeric.IntegerType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedMIPsUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CachedMIPsUtils.class);
    private static class NeuronMIPKey<N extends AbstractNeuronEntity, P extends RGBPixelType<P>, G extends IntegerType<G>> {
        private final N neuron;
        private final ComputeFileType fileType;
        private final P rgbPixelType;
        private final G grayPixelType;

        NeuronMIPKey(N neuron, ComputeFileType fileType, P rgbPixelType, G grayPixelType) {
            this.neuron = neuron;
            this.fileType = fileType;
            this.rgbPixelType = rgbPixelType;
            this.grayPixelType = grayPixelType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            NeuronMIPKey<?, ?, ?> that = (NeuronMIPKey<?, ?, ?>) o;

            return new EqualsBuilder()
                    .append(neuron, that.neuron)
                    .append(fileType, that.fileType)
                    .append(rgbPixelType, that.rgbPixelType)
                    .append(grayPixelType, that.grayPixelType)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(neuron)
                    .append(fileType)
                    .append(rgbPixelType)
                    .append(grayPixelType)
                    .toHashCode();
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("neuron", neuron)
                    .append("fileType", fileType)
                    .append("rgb", rgbPixelType)
                    .append("gray", grayPixelType)
                    .toString();
        }
    }

    private static LoadingCache<NeuronMIPKey<? extends AbstractNeuronEntity, ?, ?>, NeuronMIP<? extends AbstractNeuronEntity, ?>> mipsImagesCache;

    public static void initializeCache(long maxSize) {
        if (maxSize > 0) {
            LOG.info("Initialize cache: size={}", maxSize);
            CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder()
                    .concurrencyLevel(8)
                    .maximumSize(maxSize);

            mipsImagesCache = cacheBuilder
                    .build(new CacheLoader<NeuronMIPKey<? extends AbstractNeuronEntity, ?, ?>, NeuronMIP<? extends AbstractNeuronEntity, ?>>() {
                        @Override
                        public NeuronMIP<? extends AbstractNeuronEntity, ?> load(NeuronMIPKey<? extends AbstractNeuronEntity, ?, ?> neuronMIPKey) {
                            return tryMIPLoad(neuronMIPKey);
                        }
                    });
        } else {
            mipsImagesCache = null;
        }
    }

    public static void cleanCache() {
        mipsImagesCache.invalidateAll();
    }

    @SuppressWarnings("unchecked")
    public static <N extends AbstractNeuronEntity, P extends RGBPixelType<P>, G extends IntegerType<G>>
    NeuronMIP<N, P> loadRGBMIP(N mipInfo, ComputeFileType computeFileType, P rgbPixelType) {
        try {
            if (mipInfo == null) {
                return null;
            }
            NeuronMIP<N, P> mipsImageResult;
            if (mipsImagesCache != null) {
                mipsImageResult = (NeuronMIP<N, P>) mipsImagesCache.get(new NeuronMIPKey<>(mipInfo, computeFileType, rgbPixelType, (G) null));
            } else {
                mipsImageResult = (NeuronMIP<N, P>) tryMIPLoad(new NeuronMIPKey<>(mipInfo, computeFileType, rgbPixelType, (G) null));
            }
            return mipsImageResult;
        } catch (ExecutionException e) {
            LOG.error("Error loading {}", mipInfo, e);
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <N extends AbstractNeuronEntity, P extends RGBPixelType<P>, G extends IntegerType<G>>
    NeuronMIP<N, G> loadGrayMIP(N mipInfo, ComputeFileType computeFileType, G grayPixelType) {
        try {
            if (mipInfo == null) {
                return null;
            }
            NeuronMIP<N, G> mipsImageResult;
            if (mipsImagesCache != null) {
                mipsImageResult = (NeuronMIP<N, G>) mipsImagesCache.get(new NeuronMIPKey<>(mipInfo, computeFileType, null, grayPixelType));
            } else {
                mipsImageResult = (NeuronMIP<N, G>) tryMIPLoad(new NeuronMIPKey<N, P, G>(mipInfo, computeFileType, null, grayPixelType));
            }
            return mipsImageResult;
        } catch (ExecutionException e) {
            LOG.error("Error loading {}", mipInfo, e);
            throw new IllegalStateException(e);
        }
    }

    private static <N extends AbstractNeuronEntity, P extends RGBPixelType<P>, G extends IntegerType<G>> NeuronMIP<N, ?> tryMIPLoad(
            NeuronMIPKey<N, P, G> mipKey) {
        try {
            if (mipKey.rgbPixelType != null) {
                return NeuronMIPUtils.loadRGBComputeFile(mipKey.neuron, mipKey.fileType, mipKey.rgbPixelType);
            } else if (mipKey.grayPixelType != null) {
                return NeuronMIPUtils.loadGrayComputeFile(mipKey.neuron, mipKey.fileType, mipKey.grayPixelType);
            } else {
                throw new IllegalStateException("Can't load MIP using " + mipKey);
            }
        } catch (Exception e) {
            LOG.error("Error loading {}", mipKey, e);
            return new NeuronMIP<>(mipKey.neuron, null, null);
        }
    }

}
