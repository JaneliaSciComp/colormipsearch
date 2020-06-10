package org.janelia.colormipsearch.tools;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedMIPsUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CachedMIPsUtils.class);

    private static LoadingCache<MIPMetadata, MIPImage> mipsImagesCache;

    public static void initializeCache(long maxSize, long expirationInSeconds) {
        LOG.info("Initialize cache: size={} and expiration={}s", maxSize, expirationInSeconds);
        mipsImagesCache = CacheBuilder.newBuilder()
                .concurrencyLevel(16)
                .maximumSize(maxSize)
                .expireAfterAccess(Duration.ofSeconds(expirationInSeconds))
                .build(new CacheLoader<MIPMetadata, MIPImage>() {
                    @Override
                    public MIPImage load(MIPMetadata mipInfo) {
                        return MIPsUtils.loadMIP(mipInfo);
                    }
                });
    }

    public static MIPImage loadMIP(MIPMetadata mipInfo) {
        try {
            if (mipInfo == null || !MIPsUtils.exists(mipInfo)) {
                return null;
            } else {
                return mipsImagesCache == null ? MIPsUtils.loadMIP(mipInfo) : mipsImagesCache.get(mipInfo);
            }
        } catch (ExecutionException e) {
            LOG.error("Error loading {}", mipInfo, e);
            throw new IllegalStateException(e);
        }
    }

}
