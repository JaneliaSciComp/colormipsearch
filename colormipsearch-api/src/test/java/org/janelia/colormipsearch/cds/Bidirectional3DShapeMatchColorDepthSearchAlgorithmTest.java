package org.janelia.colormipsearch.cds;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.janelia.colormipsearch.ImageTestUtils;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.TestUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.io.SWCImageReader;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.mips.SWCImageLoader;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Bidirectional3DShapeMatchColorDepthSearchAlgorithmTest {

    private static final Logger LOG = LoggerFactory.getLogger(Bidirectional3DShapeMatchColorDepthSearchAlgorithmTest.class);

    // JRC2018_Unisex_20x_HR alignment space parameters
    private static final String ALIGNMENT_SPACE = "JRC2018_Unisex_20x_HR";

    @Test
    public void emToLmBidirectionalShapeScore() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/27329.png";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif";

        String emSWC = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmNRRD = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";

        long start = System.currentTimeMillis();

        // Load query CDM (EM)
        ImageArray queryImageArray = ImageReader.readImageArrayFromFile(emCDM);

        // Query variants: SkeletonSWC for the EM neuron
        Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers = new HashMap<>();
        queryVariantsSuppliers.put(
                ComputeFileType.SkeletonSWC,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(emSWC, () -> {
                    try (FileInputStream fis = new FileInputStream(emSWC)) {
                        return new SWCImageLoader(ALIGNMENT_SPACE, 1, 1).loadImage(emSWC, fis);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));

        // Create algorithm
        Bidirectional3DShapeMatchColorDepthSearchAlgorithm algorithm =
                new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                        queryImageArray,
                        queryVariantsSuppliers,
                        20,
                        false,
                        ALIGNMENT_SPACE,
                        ImageTestUtils.getExcludedRegionsPredicate(),
                        TestUtils::displayImage
                );

        long endInit = System.currentTimeMillis();
        LOG.info("Initialized bidirectional shape algorithm in {} secs", (endInit - start) / 1000.);

        // Load target CDM (LM)
        ImageArray targetImageArray = ImageReader.readImageArrayFromFile(lmCDM);

        // Target variants: Vol3DSegmentation from NRRD for the LM neuron
        Map<ComputeFileType, ComputeVariantImageSupplier> targetVariantsSuppliers = new HashMap<>();
        targetVariantsSuppliers.put(ComputeFileType.Vol3DSegmentation,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(lmNRRD, () -> ImageReader.readImageArrayFromFile(lmNRRD)));

        ShapeMatchScore shapeMatchScore = algorithm.calculateMatchingScore(
                targetImageArray,
                targetVariantsSuppliers
        );

        long end = System.currentTimeMillis();
        LOG.info("EM2LM bidirectional shape score: {} - init {} secs, score {} secs, total {} secs",
                shapeMatchScore.getScore(),
                (endInit - start) / 1000.,
                (end - endInit) / 1000.,
                (end - start) / 1000.);

        assertNotNull(shapeMatchScore);
        assertEquals("Expected a valid bidirectional score but got " + shapeMatchScore.getScore(),
                395, shapeMatchScore.getScore());
    }

    @Test
    public void lmToEmBidirectionalShapeScore() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/27329.png";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif";

        String emSWC = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmNRRD = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";

        long start = System.currentTimeMillis();

        // Load query CDM (EM)
        ImageArray queryImageArray = ImageReader.readImageArrayFromFile(lmCDM);

        // Query variants: SkeletonSWC for the EM neuron
        Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers = new HashMap<>();
        queryVariantsSuppliers.put(ComputeFileType.Vol3DSegmentation,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(lmNRRD, () -> ImageReader.readImageArrayFromFile(lmNRRD)));

        // Create algorithm
        Bidirectional3DShapeMatchColorDepthSearchAlgorithm algorithm =
                new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                        queryImageArray,
                        queryVariantsSuppliers,
                        20,
                        false,
                        ALIGNMENT_SPACE,
                        ImageTestUtils.getExcludedRegionsPredicate(),
                        TestUtils::displayImage
                );

        long endInit = System.currentTimeMillis();
        LOG.info("Initialized bidirectional shape algorithm in {} secs", (endInit - start) / 1000.);

        // Load target CDM (LM)
        ImageArray targetImageArray = ImageReader.readImageArrayFromFile(emCDM);

        // Target variants: Vol3DSegmentation from NRRD for the LM neuron
        Map<ComputeFileType, ComputeVariantImageSupplier> targetVariantsSuppliers = new HashMap<>();
        targetVariantsSuppliers.put(
                ComputeFileType.SkeletonSWC,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(emSWC, () -> {
                    try (FileInputStream fis = new FileInputStream(emSWC)) {
                        return new SWCImageLoader(ALIGNMENT_SPACE, 1, 1).loadImage(emSWC, fis);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));

        ShapeMatchScore shapeMatchScore = algorithm.calculateMatchingScore(
                targetImageArray,
                targetVariantsSuppliers
        );

        long end = System.currentTimeMillis();
        LOG.info("LM2EM bidirectional shape score: {} - init {} secs, score {} secs, total {} secs",
                shapeMatchScore.getScore(),
                (endInit - start) / 1000.,
                (end - endInit) / 1000.,
                (end - start) / 1000.);

        assertNotNull(shapeMatchScore);
        assertEquals("Expected a valid bidirectional score but got " + shapeMatchScore.getScore(),
                1746837, shapeMatchScore.getScore());
    }

    @Test
    public void emToLmBidirectionalShapeScoreWhenNoOverlap() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/100016_jrc18U_um.tif";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/BJD_100C09_AE_01-20171212_61_E6-40x-Brain-JRC2018_Unisex_20x_HR-2504454722857992290-CH2-01_CDM.tif";

        String emSWC = "src/test/resources/colormipsearch/api/cdsearch/100016_jrc18U_um.swc";
        String lmNRRD = "src/test/resources/colormipsearch/api/cdsearch/VT006415_100C09_AE_01-20171212_61_E6-f-CH2_01.nrrd";

        long start = System.currentTimeMillis();

        // Load query CDM (EM)
        ImageArray queryImageArray = ImageReader.readImageArrayFromFile(emCDM);

        // Query variants: SkeletonSWC
        Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers = new HashMap<>();
        queryVariantsSuppliers.put(
                ComputeFileType.SkeletonSWC,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(emSWC, () -> {
                    try (FileInputStream fis = new FileInputStream(emSWC)) {
                        return new SWCImageLoader(ALIGNMENT_SPACE, 1, 1).loadImage(emSWC, fis);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

        Bidirectional3DShapeMatchColorDepthSearchAlgorithm algorithm =
                new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                        queryImageArray,
                        queryVariantsSuppliers,
                        20,
                        true,
                        ALIGNMENT_SPACE,
                        ImageTestUtils.getExcludedRegionsPredicate(),
                        TestUtils::displayImage
                );

        long endInit = System.currentTimeMillis();

        // Load target CDM (LM)
        ImageArray targetImageArray = ImageReader.readImageArrayFromFile(lmCDM);

        // Target variants: Vol3DSegmentation from NRRD
        Map<ComputeFileType, ComputeVariantImageSupplier> targetVariantsSuppliers = new HashMap<>();
        targetVariantsSuppliers.put(
                ComputeFileType.Vol3DSegmentation,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(lmNRRD, () -> ImageReader.readImageArrayFromFile(lmNRRD))
        );

        ShapeMatchScore shapeMatchScore = algorithm.calculateMatchingScore(
                targetImageArray,
                targetVariantsSuppliers
        );

        long end = System.currentTimeMillis();
        LOG.info("EM2LM no-overlap bidirectional shape score: {} - init {} secs, score {} secs, total {} secs",
                shapeMatchScore.getScore(),
                (endInit - start) / 1000.,
                (end - endInit) / 1000.,
                (end - start) / 1000.);

        assertNotNull(shapeMatchScore);
        assertEquals("Expected -1 score when there is no overlap",
                -1, shapeMatchScore.getScore());
    }

    @Test
    public void bidirectionalShapeScoreWhenNoQueryVolume() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/ems/12191_JRC2018U.tif";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif";

        ImageArray queryImageArray = ImageReader.readImageArrayFromFile(emCDM);

        // No query variants → volumeSegmentationHelper won't be available
        Bidirectional3DShapeMatchColorDepthSearchAlgorithm algorithm =
                new Bidirectional3DShapeMatchColorDepthSearchAlgorithm(
                        queryImageArray,
                        Collections.emptyMap(),
                        20,
                        false,
                        ALIGNMENT_SPACE,
                        ImageTestUtils.getExcludedRegionsPredicate(),
                        TestUtils::displayImage
                );

        ImageArray targetImageArray = ImageReader.readImageArrayFromFile(lmCDM);

        ShapeMatchScore shapeMatchScore = algorithm.calculateMatchingScore(
                targetImageArray,
                Collections.emptyMap()
        );

        assertNotNull(shapeMatchScore);
        assertEquals("Expected -1 score when no 3D volume is available",
                -1, shapeMatchScore.getScore());
    }

    @Test
    public void bidirectionalShapeScoreViaProvider() {
        String emCDM = "src/test/resources/colormipsearch/api/cdsearch/27329.png";
        String lmCDM = "src/test/resources/colormipsearch/api/cdsearch/lms/VT033614_127B01_AE_01-20171124_64_H6-f-CH2_01.tif";
        String emSWC = "src/test/resources/colormipsearch/api/cdsearch/27329.swc";
        String lmNRRD = "src/test/resources/colormipsearch/api/cdsearch/1_VT000770_130A10_AE_01-20180810_61_G2-m-CH1_02__gen1_MCFO.nrrd";

        long start = System.currentTimeMillis();

        // Create provider (no query-specific info — that's the whole point of the refactor)
        ColorDepthSearchAlgorithmProvider<ShapeMatchScore> provider =
                ColorDepthSearchAlgorithmProviderFactory.createBidirectionalShapeMatchCDSAlgorithmProvider(
                        ALIGNMENT_SPACE,
                        false,
                        ImageTestUtils.getExcludedRegionsPredicate()
                );

        ImageArray queryImageArray = ImageReader.readImageArrayFromFile(emCDM);

        // Query variants: SkeletonSWC
        Map<ComputeFileType, ComputeVariantImageSupplier> queryVariantsSuppliers = new HashMap<>();
        queryVariantsSuppliers.put(
                ComputeFileType.SkeletonSWC,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(emSWC, () -> {
                    try (FileInputStream fis = new FileInputStream(emSWC)) {
                        return new SWCImageLoader(ALIGNMENT_SPACE, 1, 1).loadImage(emSWC, fis);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

        // Create algorithm via provider — query variants passed at algorithm creation time
        ColorDepthSearchAlgorithm<ShapeMatchScore> algorithm =
                provider.createColorDepthQuerySearchAlgorithmWithDefaultParams(
                        queryImageArray,
                        queryVariantsSuppliers,
                        20,
                        0
                );

        long endInit = System.currentTimeMillis();

        ImageArray targetImageArray = ImageReader.readImageArrayFromFile(lmCDM);

        // Target variants: Vol3DSegmentation from NRRD
        Map<ComputeFileType, ComputeVariantImageSupplier> targetVariantsSuppliers = new HashMap<>();
        targetVariantsSuppliers.put(
                ComputeFileType.Vol3DSegmentation,
                ComputeVariantImageSupplier.fromNameAndImageSupplier(lmNRRD, () -> ImageReader.readImageArrayFromFile(lmNRRD)));

        ShapeMatchScore shapeMatchScore = algorithm.calculateMatchingScore(
                targetImageArray,
                targetVariantsSuppliers
        );

        long end = System.currentTimeMillis();
        LOG.info("Bidirectional shape score via provider: {} - init {} secs, score {} secs, total {} secs",
                shapeMatchScore.getScore(),
                (endInit - start) / 1000.,
                (end - endInit) / 1000.,
                (end - start) / 1000.);

        assertNotNull(shapeMatchScore);
        assertEquals("Expected a valid bidirectional score but got " + shapeMatchScore.getScore(),
                395, shapeMatchScore.getScore());
    }
}
