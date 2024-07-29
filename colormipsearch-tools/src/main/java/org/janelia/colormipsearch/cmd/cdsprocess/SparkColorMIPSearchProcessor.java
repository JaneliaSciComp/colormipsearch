package org.janelia.colormipsearch.cmd.cdsprocess;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.colormipsearch.cds.PixelMatchScore;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.cds.ColorMIPSearch;
import org.janelia.colormipsearch.cmd.CachedMIPsUtils;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.mips.NeuronMIP;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SparkColorMIPSearchProcessor<M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
        extends AbstractColorMIPSearchProcessor<M, T>
        implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(SparkColorMIPSearchProcessor.class);

    private transient final JavaSparkContext sparkContext;

    public SparkColorMIPSearchProcessor(Number cdsRunId,
                                        String appName,
                                        ColorMIPSearch colorMIPSearch,
                                        int localProcessingPartitionSize,
                                        Set<String> tags) {
        super(cdsRunId, colorMIPSearch, localProcessingPartitionSize, tags);
        this.sparkContext = new JavaSparkContext(new SparkConf().setAppName(appName));
    }

    @Override
    public List<CDMatchEntity<M, T>> findAllColorDepthMatches(List<M> queryMIPs, List<T> targetMIPs) {
        long startTime = System.currentTimeMillis();
        int nQueries = queryMIPs.size();
        int nTargets = targetMIPs.size();

        LOG.info("Searching {} masks against {} targets", nQueries, nTargets);

        JavaRDD<T> targetMIPsRDD = sparkContext.parallelize(targetMIPs);
        LOG.info("Created {} partitions for {} targets", targetMIPsRDD.getNumPartitions(), nTargets);

        @SuppressWarnings("unchecked")
        List<CDMatchEntity<M, T>> cdsResults = ItemsHandling.partitionCollection(queryMIPs, localProcessingPartitionSize).entrySet().stream().parallel()
                .map(indexedQueryMIPsPartition -> targetMIPsRDD.mapPartitions(targetMIPsItr -> {
                    List<T> localTargetMIPs = Lists.newArrayList(targetMIPsItr);
                    return indexedQueryMIPsPartition.getValue().stream()
                            .map(queryMIP -> NeuronMIPUtils.loadQueryVariant(
                                    queryMIP,
                                    ComputeFileType.InputColorDepthImage
                            ))
                            .filter(queryImage -> queryImage != null && queryImage.hasImageArray())
                            .flatMap(queryImage -> {
                                @SuppressWarnings("unchecked")
                                ColorDepthSearchAlgorithm<PixelMatchScore> queryColorDepthSearch = colorMIPSearch.createQueryColorDepthSearchWithDefaultThreshold(
                                        (RandomAccessibleInterval<? extends RGBPixelType<?>>) queryImage.getImageArray(),
                                        Collections.emptyMap()
                                );
                                if (queryColorDepthSearch.isNotAvailable()) {
                                    return Stream.of();
                                } else {
                                    return localTargetMIPs.stream()
                                            .map(targetMIP -> CachedMIPsUtils.loadMIP(
                                                    targetMIP,
                                                    ComputeFileType.InputColorDepthImage))
                                            .filter(targetImage -> targetImage != null && targetImage.hasImageArray())
                                            .map(targetImage -> findPixelMatch(queryColorDepthSearch, queryImage, targetImage))
                                            .filter(m -> m.isMatchFound() && m.hasNoErrors())
                                            ;
                                }
                            })
                            .iterator();
                }).collect())
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        LOG.info("Found {} cds results in {}ms", cdsResults.size(), System.currentTimeMillis() - startTime);
        return cdsResults;
    }

    @Override
    public void terminate() {
        sparkContext.close();
    }
}
