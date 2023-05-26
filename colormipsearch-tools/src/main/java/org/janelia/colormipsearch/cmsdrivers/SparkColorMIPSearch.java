package org.janelia.colormipsearch.cmsdrivers;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.colormipsearch.api.Utils;
import org.janelia.colormipsearch.api.cdmips.MIPImage;
import org.janelia.colormipsearch.api.cdmips.MIPMetadata;
import org.janelia.colormipsearch.api.cdmips.MIPsUtils;
import org.janelia.colormipsearch.api.cdsearch.ColorDepthSearchAlgorithm;
import org.janelia.colormipsearch.api.cdsearch.ColorMIPMatchScore;
import org.janelia.colormipsearch.api.cdsearch.ColorMIPSearch;
import org.janelia.colormipsearch.api.cdsearch.ColorMIPSearchResult;
import org.janelia.colormipsearch.api.imageprocessing.ImageArray;
import org.janelia.colormipsearch.api.imageprocessing.MappingFunction;
import org.janelia.colormipsearch.utils.CachedMIPsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perform color depth mask search on a Spark cluster.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class SparkColorMIPSearch implements ColorMIPSearchDriver, Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(SparkColorMIPSearch.class);

    private final ColorMIPSearch colorMIPSearch;
    private final int localProcessingPartitionSize;
    private final List<String> gradientsLocations;
    private final MappingFunction<String, String> gradientVariantSuffixMapping;
    private final List<String> zgapMasksLocations;
    private final MappingFunction<String, String> zgapMaskVariantSuffixMapping;
    private transient final JavaSparkContext sparkContext;

    public SparkColorMIPSearch(String appName,
                               ColorMIPSearch colorMIPSearch,
                               int localProcessingPartitionSize,
                               List<String> gradientsLocations,
                               MappingFunction<String, String> gradientVariantSuffixMapping,
                               List<String> zgapMasksLocations,
                               MappingFunction<String, String> zgapMaskVariantSuffixMapping) {
        this.colorMIPSearch = colorMIPSearch;
        this.localProcessingPartitionSize = localProcessingPartitionSize > 0 ? localProcessingPartitionSize : 1;
        this.gradientsLocations = gradientsLocations;
        this.gradientVariantSuffixMapping = gradientVariantSuffixMapping;
        this.zgapMasksLocations = zgapMasksLocations;
        this.zgapMaskVariantSuffixMapping = zgapMaskVariantSuffixMapping;
        this.sparkContext = new JavaSparkContext(new SparkConf().setAppName(appName));
    }

    @Override
    public List<ColorMIPSearchResult> findAllColorDepthMatches(List<MIPMetadata> queryMIPS, List<MIPMetadata> targetMIPS) {
        long nTargets = targetMIPS.size();
        long nQueries = queryMIPS.size();
        LOG.info("Searching {} queries against {} targets", nQueries, nTargets);

        JavaRDD<MIPImage> targetImagesRDD = sparkContext.parallelize(targetMIPS)
                .filter(targetMIP -> {
                    if (!MIPsUtils.exists(targetMIP)) {
                        LOG.warn("No image found for target {}", targetMIP);
                        return false;
                    } else {
                        return true;
                    }
                })
                .map(MIPsUtils::loadMIP);
        LOG.info("Created {} partitions for {} targets", targetImagesRDD.getNumPartitions(), nTargets);

        List<ColorMIPSearchResult> cdsResults = Utils.partitionCollection(queryMIPS, localProcessingPartitionSize).stream().parallel()
                .map(queriesPartition -> targetImagesRDD.mapPartitions(targetImagesItr -> {
                    List<MIPImage> localTargetImages = Lists.newArrayList(targetImagesItr);
                    List<ColorMIPSearchResult> localMatches = queriesPartition.stream()
                            .filter(queryMIP -> {
                                if (!MIPsUtils.exists(queryMIP)) {
                                    LOG.warn("No image found for query {}", queryMIP);
                                    return false;
                                } else {
                                    return true;
                                }
                            })
                            .map(MIPsUtils::loadMIP)
                            .flatMap(queryImage -> {
                                ColorDepthSearchAlgorithm<ColorMIPMatchScore> queryColorDepthSearch = colorMIPSearch.createQueryColorDepthSearchWithDefaultThreshold(queryImage);
                                if (queryColorDepthSearch.getQuerySize() == 0) {
                                    return Stream.of();
                                } else {
                                    return localTargetImages.stream()
                                            .map(targetImage -> search(queryColorDepthSearch, queryImage, targetImage))
                                            .filter(r -> r.isMatch());
                                }
                            })
                            .collect(Collectors.toList());
                    return localMatches.iterator();
                }).collect())
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        LOG.info("Found {} cds results", cdsResults.size());
        return cdsResults;
    }

    private ColorMIPSearchResult search(ColorDepthSearchAlgorithm<ColorMIPMatchScore> queryColorDepthSearch,
                                        MIPImage queryImage,
                                        MIPImage targetImage) {
        try {
            LOG.info("Compare query {} with target {}", queryImage, targetImage);
            Set<String> requiredVariantTypes = queryColorDepthSearch.getRequiredTargetVariantTypes();
            Map<String, Supplier<ImageArray<?>>> variantImageSuppliers = new HashMap<>();
            if (requiredVariantTypes.contains("gradient")) {
                variantImageSuppliers.put("gradient", () -> {
                    MIPImage gradientImage = CachedMIPsUtils.loadMIP(MIPsUtils.getMIPVariantInfo(
                            MIPsUtils.getMIPMetadata(targetImage),
                            "gradient",
                            gradientsLocations,
                            gradientVariantSuffixMapping,
                            null));
                    return MIPsUtils.getImageArray(gradientImage);
                });
            }
            if (requiredVariantTypes.contains("zgap")) {
                variantImageSuppliers.put("zgap", () -> {
                    MIPImage targetZGapMaskImage = CachedMIPsUtils.loadMIP(MIPsUtils.getMIPVariantInfo(
                            MIPsUtils.getMIPMetadata(targetImage),
                            "zgap",
                            zgapMasksLocations,
                            zgapMaskVariantSuffixMapping,
                            null));
                    return MIPsUtils.getImageArray(targetZGapMaskImage);
                });
            }

            ColorMIPMatchScore colorMIPMatchScore = queryColorDepthSearch.calculateMatchingScore(
                    MIPsUtils.getImageArray(targetImage),
                    variantImageSuppliers);
            boolean isMatch = colorMIPSearch.isMatch(colorMIPMatchScore);
            return new ColorMIPSearchResult(
                    MIPsUtils.getMIPMetadata(queryImage),
                    MIPsUtils.getMIPMetadata(targetImage),
                    colorMIPMatchScore,
                    isMatch,
                    false);
        } catch (Exception e) {
            LOG.error("Error searching {} with {}", queryImage, targetImage, e);
            return new ColorMIPSearchResult(
                    MIPsUtils.getMIPMetadata(queryImage),
                    MIPsUtils.getMIPMetadata(targetImage),
                    ColorMIPMatchScore.NO_MATCH,
                    false,
                    true);
        }
    }

    @Override
    public void terminate() {
        sparkContext.close();
    }

}
