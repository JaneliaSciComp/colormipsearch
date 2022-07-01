package org.janelia.colormipsearch.cmd.cdsprocess;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.colormipsearch.cds.PixelMatchScore;
import org.janelia.colormipsearch.cds.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.cds.ColorMIPSearch;
import org.janelia.colormipsearch.cmd.CachedMIPsUtils;
import org.janelia.colormipsearch.mips.NeuronMIPUtils;
import org.janelia.colormipsearch.model.AbstractNeuronMetadata;
import org.janelia.colormipsearch.model.CDSMatch;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SparkColorMIPSearchProcessor<M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> extends AbstractColorMIPSearchProcessor<M, T>
                                                                                                              implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(SparkColorMIPSearchProcessor.class);

    private transient final JavaSparkContext sparkContext;

    public SparkColorMIPSearchProcessor(String appName,
                                        ColorMIPSearch colorMIPSearch,
                                        int localProcessingPartitionSize) {
        super(colorMIPSearch, localProcessingPartitionSize);
        this.sparkContext = new JavaSparkContext(new SparkConf().setAppName(appName));
    }

    @Override
    public List<CDSMatch<M, T>> findAllColorDepthMatches(List<M> queryMIPs, List<T> targetMIPs) {
        long startTime = System.currentTimeMillis();
        int nQueries = queryMIPs.size();
        int nTargets = targetMIPs.size();

        LOG.info("Searching {} masks against {} targets", nQueries, nTargets);

        JavaRDD<T> targetMIPsRDD = sparkContext.parallelize(targetMIPs);
        LOG.info("Created {} partitions for {} targets", targetMIPsRDD.getNumPartitions(), nTargets);

        List<CDSMatch<M, T>> cdsResults = ItemsHandling.partitionCollection(queryMIPs, localProcessingPartitionSize).stream().parallel()
                .map(queryMIPsPartition -> targetMIPsRDD.mapPartitions(targetMIPsItr -> {
                    List<T> localTargetMIPs = Lists.newArrayList(targetMIPsItr);
                    return queryMIPsPartition.stream()
                            .map(queryMIP -> NeuronMIPUtils.loadComputeFile(queryMIP, ComputeFileType.InputColorDepthImage))
                            .filter(queryImage -> queryImage != null && queryImage.hasImageArray())
                            .flatMap(queryImage -> {
                                ColorDepthSearchAlgorithm<PixelMatchScore> queryColorDepthSearch = colorMIPSearch.createQueryColorDepthSearchWithDefaultThreshold(queryImage.getImageArray());
                                if (queryColorDepthSearch.getQuerySize() == 0) {
                                    return Stream.of();
                                } else {
                                    return localTargetMIPs.stream()
                                            .map(targetMIP -> CachedMIPsUtils.loadMIP(targetMIP, ComputeFileType.InputColorDepthImage))
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
