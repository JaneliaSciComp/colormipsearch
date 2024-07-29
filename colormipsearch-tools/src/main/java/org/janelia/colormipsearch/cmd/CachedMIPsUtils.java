package org.janelia.colormipsearch.cmd;

import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedMIPsUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CachedMIPsUtils.class);

    private static class NeuronMIPKey<N extends AbstractNeuronEntity> {
        private final N neuron;
        private final ComputeFileType fileType;

        NeuronMIPKey(N neuron, ComputeFileType fileType) {
            this.neuron = neuron;
            this.fileType = fileType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            NeuronMIPKey<?> that = (NeuronMIPKey<?>) o;

            return new EqualsBuilder()
                    .append(neuron, that.neuron)
                    .append(fileType, that.fileType)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(neuron)
                    .append(fileType)
                    .toHashCode();
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("neuron", neuron)
                    .append("fileType", fileType)
                    .toString();
        }
    }

    private static LoadingCache<NeuronMIPKey<? extends AbstractNeuronEntity>, NeuronMIP<? extends AbstractNeuronEntity>> mipsImagesCache;

    public static void initializeCache(long maxSize) {
        if (maxSize > 0) {
            LOG.info("Initialize cache: size={}", maxSize);
            CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder()
                    .concurrencyLevel(8)
                    .maximumSize(maxSize);

            mipsImagesCache = cacheBuilder
                    .build(new CacheLoader<NeuronMIPKey<? extends AbstractNeuronEntity>, NeuronMIP<? extends AbstractNeuronEntity>>() {
                        @Override
                        public NeuronMIP<? extends AbstractNeuronEntity> load(NeuronMIPKey<? extends AbstractNeuronEntity> neuronMIPKey) {
                            LOG.debug("Cache {}", neuronMIPKey);
                            return tryLoadMIP(neuronMIPKey);
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
    public static <N extends AbstractNeuronEntity>
    NeuronMIP<N> loadMIP(N mipInfo, ComputeFileType computeFileType) {
        try {
            if (mipInfo == null) {
                return null;
            }
            NeuronMIP<N> mipsImageResult;
            if (mipsImagesCache != null) {
                mipsImageResult = (NeuronMIP<N>) mipsImagesCache.get(new NeuronMIPKey<>(mipInfo, computeFileType));
            } else {
                mipsImageResult = tryLoadMIP(new NeuronMIPKey<>(mipInfo, computeFileType));
            }
            return mipsImageResult;
        } catch (ExecutionException e) {
            LOG.error("Error loading {}", mipInfo, e);
            throw new IllegalStateException(e);
        }
    }

    private static <N extends AbstractNeuronEntity> NeuronMIP<N> tryLoadMIP(NeuronMIPKey<N> mipKey) {
        try {
            return NeuronMIPUtils.loadTargetVariant(mipKey.neuron, mipKey.fileType);
        } catch (Exception e) {
            LOG.error("Error loading {}", mipKey, e);
            return new NeuronMIP<>(mipKey.neuron, null, null);
        }
    }

}
