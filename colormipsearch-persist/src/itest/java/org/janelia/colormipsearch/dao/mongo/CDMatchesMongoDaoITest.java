package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.model.AbstractBaseEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EntityField;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.FileType;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CDMatchesMongoDaoITest extends AbstractMongoDaoITest {

    private final List<? extends CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> testData = new ArrayList<>();

    @After
    public <M extends AbstractNeuronEntity,
            T extends AbstractNeuronEntity>
    void tearDown() {
        // delete the data that was created for testing
        NeuronMatchesDao<CDMatchEntity<M, T>> neuronMatchesDao = daosProvider.getCDMatchesDao();
        @SuppressWarnings("unchecked")
        List<CDMatchEntity<M, T>> toDelete = (List<CDMatchEntity<M, T>>) testData;
        deleteAll(neuronMatchesDao, toDelete);
    }

    @Test
    public void persistCDMatchWithDummyImages() {
        CDMatchEntity<EMNeuronEntity, LMNeuronEntity> testCDMatch =
                createTestCDMatch(
                        new TestNeuronEntityBuilder<>(EMNeuronEntity::new)
                                .entityId(10)
                                .get(),
                        new TestNeuronEntityBuilder<>(LMNeuronEntity::new)
                                .entityId(20)
                                .get(),
                        113,
                        .5
                );
        NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                daosProvider.getCDMatchesDao();
        neuronMatchesDao.save(testCDMatch);
        CDMatchEntity<EMNeuronEntity, LMNeuronEntity> persistedCDMatch = neuronMatchesDao.findByEntityId(testCDMatch.getEntityId());
        assertNotNull(persistedCDMatch);
        assertNotSame(testCDMatch, persistedCDMatch);
        assertEquals(testCDMatch.getEntityId(), persistedCDMatch.getEntityId());
        assertNull(persistedCDMatch.getMatchedImage());
        assertNull(persistedCDMatch.getMaskImage());
        assertEquals(testCDMatch.getMaskImageRefId().toString(), persistedCDMatch.getMaskImageRefId().toString());
        assertEquals(testCDMatch.getMatchedImageRefId().toString(), persistedCDMatch.getMatchedImageRefId().toString());
    }

    @Test
    public void persistCDMatchWithImages() {
        NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                daosProvider.getCDMatchesDao();

        verifyMultipleCDMatcheshWithImages(
                1,
                neuronMatchesDao,
                ms -> Collections.singletonList(neuronMatchesDao.findByEntityId(ms.get(0).getEntityId())));
    }

    @Test
    public void findMultipleCDMatchesByEntityIds() {
        NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                daosProvider.getCDMatchesDao();

        verifyMultipleCDMatcheshWithImages(
                20,
                neuronMatchesDao,
                ms -> neuronMatchesDao.findByEntityIds(
                        ms.stream()
                                .map(AbstractBaseEntity::getEntityId)
                                .collect(Collectors.toList())
                )
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    public void findAllCDMatchesWithoutPagination() {
        NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                daosProvider.getCDMatchesDao();

        Class<?> searchedType = CDMatchEntity.class;
        verifyMultipleCDMatcheshWithImages(
                20,
                neuronMatchesDao,
                ms -> neuronMatchesDao.findAll((Class<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>>) searchedType, new PagedRequest()).getResultList()
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    public void findAllCDMatchesWithPagination() {
        NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao = daosProvider.getNeuronMetadataDao();
        Pair<EMNeuronEntity, LMNeuronEntity> neuronImages = createMatchingImages(neuronMetadataDao);
        NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                daosProvider.getCDMatchesDao();
        int nTestMatches = 27;
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> testCDMatches = createTestCDMatches(nTestMatches, neuronImages, neuronMatchesDao);
        try {
            int pageSize = 5;
            Class<?> searchedType = CDMatchEntity.class;
            for (int i = 0; i < nTestMatches; i += pageSize) {
                int page = i / pageSize;
                retrieveAndCompareCDMatcheshWithImages(
                        testCDMatches.subList(i, Math.min(testCDMatches.size(), i + pageSize)),
                        ms -> neuronMatchesDao.findAll(
                                (Class<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>>) searchedType,
                                new PagedRequest().setPageNumber(page).setPageSize(pageSize)).getResultList());
            }
        } finally {
            deleteAll(neuronMetadataDao, Arrays.asList(neuronImages.getLeft(), neuronImages.getRight()));
        }
    }

    @Test
    public void findCDMatchesUsingNeuronSelectors() {
        NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao = daosProvider.getNeuronMetadataDao();
        Pair<EMNeuronEntity, LMNeuronEntity> neuronImages = createMatchingImages(neuronMetadataDao);
        NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                daosProvider.getCDMatchesDao();
        int nTestMatches = 27;
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> testCDMatches = createTestCDMatches(nTestMatches, neuronImages, neuronMatchesDao);
        try {
            int pageSize = 5;
            NeuronsMatchFilter<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> matchesFilter =
                    new NeuronsMatchFilter<>();
            NeuronSelector emNeuronSelector = new NeuronSelector()
                    .addLibrary("FlyEM Hemibrain")
                    .addMipID("123232232423232")
                    .addName("23232345");
            NeuronSelector lmNeuronSelector = new NeuronSelector()
                    .addLibrary("Split GAL4")
                    .addMipID("5565655454545432")
                    .addName("S1234");
            for (int i = 0; i < nTestMatches; i += pageSize) {
                int page = i / pageSize;
                PagedRequest pagedRequest = new PagedRequest().setPageNumber(page).setPageSize(pageSize);
                retrieveAndCompareCDMatcheshWithImages(
                        testCDMatches.subList(i, Math.min(testCDMatches.size(), i + pageSize)),
                        ms -> neuronMatchesDao.findNeuronMatches(
                                matchesFilter,
                                emNeuronSelector,
                                lmNeuronSelector,
                                pagedRequest).getResultList());
            }
            // retrieve all
            retrieveAndCompareCDMatcheshWithImages(
                    testCDMatches,
                    ms -> neuronMatchesDao.findNeuronMatches(
                            matchesFilter,
                            emNeuronSelector,
                            lmNeuronSelector,
                            new PagedRequest()).getResultList());
            // retrieve none
            retrieveAndCompareCDMatcheshWithImages(
                    Collections.emptyList(),
                    ms -> neuronMatchesDao.findNeuronMatches(
                            matchesFilter,
                            lmNeuronSelector,
                            emNeuronSelector,
                            new PagedRequest()).getResultList());
            assertEquals(
                    nTestMatches,
                    neuronMatchesDao.countNeuronMatches(
                            matchesFilter,
                            emNeuronSelector,
                            lmNeuronSelector));
            assertEquals(
                    0,
                    neuronMatchesDao.countNeuronMatches(
                            matchesFilter,
                            lmNeuronSelector,
                            emNeuronSelector));
        } finally {
            deleteAll(neuronMetadataDao, Arrays.asList(neuronImages.getLeft(), neuronImages.getRight()));
        }
    }

    @Test
    public void findAndUpdate() {
        NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao = daosProvider.getNeuronMetadataDao();
        Pair<EMNeuronEntity, LMNeuronEntity> neuronImages = createMatchingImages(neuronMetadataDao);
        try {
            NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                    daosProvider.getCDMatchesDao();

            CDMatchEntity<EMNeuronEntity, LMNeuronEntity> testCDMatch =
                    createTestCDMatch(neuronImages.getLeft(), neuronImages.getRight(), 113, 0.76,
                            "1.0", "1.1");

            List<Function<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>, EntityField<?>>> fieldsToUpdate = Arrays.asList(
                    m -> new EntityField<>("matchingPixels", false, m.getMatchingPixels()),
                    m -> new EntityField<>("matchingPixelsRatio", false, m.getMatchingPixelsRatio()),
                    m -> new EntityField<>("gradientAreaGap", false, m.getGradientAreaGap()),
                    m -> new EntityField<>("highExpressionArea", false ,m.getHighExpressionArea()),
                    m -> new EntityField<>("normalizedScore", false, m.getNormalizedScore()),
                    m -> new EntityField<>("tags", true, m.getTags())
            );

            assertEquals(
                    0, // nothing was created yet
                    neuronMatchesDao.countNeuronMatches(
                            new NeuronsMatchFilter<>(),
                            null,
                            null)
            );
            List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> testCDMatches = Collections.singletonList(testCDMatch);
            // save the matches multiple times and check the count is still 1
            for (int i = 0; i < 3; i++) {
                testCDMatch.setMatchingPixels(testCDMatch.getMatchingPixels() + i);
                testCDMatch.setMatchingPixelsRatio(testCDMatch.getMatchingPixelsRatio() / (i + 1));
                neuronMatchesDao.createOrUpdateAll(testCDMatches, fieldsToUpdate);
                // check that it was saved
                assertEquals(
                        1,
                        neuronMatchesDao.countNeuronMatches(
                                new NeuronsMatchFilter<>(),
                                null,
                                null)
                );
                List<?> retrievedMatches = neuronMatchesDao.findNeuronMatches(
                        new NeuronsMatchFilter<>(),
                        new NeuronSelector(),
                        new NeuronSelector(),
                        new PagedRequest()).getResultList();
                assertEquals(1, retrievedMatches.size());
                retrievedMatches.forEach(m -> assertTrue(m instanceof CDMatchEntity));
            }
        } finally {
            deleteAll(neuronMetadataDao, Arrays.asList(neuronImages.getLeft(), neuronImages.getRight()));
        }
    }

    @Test
    public void updateExisting() {
        NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao = daosProvider.getNeuronMetadataDao();
        Pair<EMNeuronEntity, LMNeuronEntity> neuronImages = createMatchingImages(neuronMetadataDao);
        try {
            NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                    daosProvider.getCDMatchesDao();

            CDMatchEntity<EMNeuronEntity, LMNeuronEntity> testCDMatch =
                    createTestCDMatch(neuronImages.getLeft(), neuronImages.getRight(), 113, 0.76,
                            "1.0", "1.1");

            assertEquals(
                    0, // nothing was created yet
                    neuronMatchesDao.countNeuronMatches(
                            new NeuronsMatchFilter<>(),
                            null,
                            null)
            );
            List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> testCDMatches = Collections.singletonList(testCDMatch);
            neuronMatchesDao.createOrUpdateAll(testCDMatches, Collections.emptyList());

            List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> retrievedMatches = neuronMatchesDao.findNeuronMatches(
                    new NeuronsMatchFilter<>(),
                    new NeuronSelector(),
                    new NeuronSelector(),
                    new PagedRequest()).getResultList();

            // check that it was saved
            assertEquals(1, retrievedMatches.size());
            List<Function<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>, Pair<String, ?>>> fieldsToUpdate = Arrays.asList(
                    m -> Pair.of("matchingPixels", m.getMatchingPixels()),
                    m -> Pair.of("matchingPixelsRatio", m.getMatchingPixelsRatio()),
                    m -> Pair.of("gradientAreaGap", m.getGradientAreaGap()),
                    m -> Pair.of("highExpressionArea", m.getHighExpressionArea()),
                    m -> Pair.of("normalizedScore", m.getNormalizedScore())
            );
            long nUpdates = neuronMatchesDao.updateExistingMatches(retrievedMatches, fieldsToUpdate);
            assertEquals(1, nUpdates);

        } finally {
            deleteAll(neuronMetadataDao, Arrays.asList(neuronImages.getLeft(), neuronImages.getRight()));
        }
    }

    private void verifyMultipleCDMatcheshWithImages(int nTestMatches,
                                                    NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao,
                                                    Function<List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>>,
                                                    List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>>> dataRetriever) {
        NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao =
                daosProvider.getNeuronMetadataDao();
        Pair<EMNeuronEntity, LMNeuronEntity> neuronImages = createMatchingImages(neuronMetadataDao);
        try {
            List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> testCDMatches = createTestCDMatches(nTestMatches, neuronImages, neuronMatchesDao);
            retrieveAndCompareCDMatcheshWithImages(testCDMatches, dataRetriever);
        } finally {
            deleteAll(neuronMetadataDao, Arrays.asList(neuronImages.getLeft(), neuronImages.getRight()));
        }
    }

    private Pair<EMNeuronEntity, LMNeuronEntity> createMatchingImages(NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao) {
        EMNeuronEntity emNeuronMetadata = new TestNeuronEntityBuilder<>(EMNeuronEntity::new)
                .mipId("123232232423232")
                .publishedName("23232345")
                .library("FlyEM Hemibrain")
                .computeFileData(ComputeFileType.InputColorDepthImage, FileData.fromString("mask-mipSegmentation"))
                .computeFileData(ComputeFileType.SourceColorDepthImage, FileData.fromString("mask-sourceMip"))
                .get();
        LMNeuronEntity lmNeuronMetadata = new TestNeuronEntityBuilder<>(LMNeuronEntity::new)
                .mipId("5565655454545432")
                .publishedName("S1234")
                .library("Split GAL4")
                .computeFileData(ComputeFileType.InputColorDepthImage, FileData.fromString("match-mipSegmentation"))
                .computeFileData(ComputeFileType.SourceColorDepthImage, FileData.fromString("match-sourceMip"))
                .get();

        neuronMetadataDao.save(lmNeuronMetadata);
        neuronMetadataDao.save(emNeuronMetadata);
        return ImmutablePair.of(emNeuronMetadata, lmNeuronMetadata);
    }

    private List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> createTestCDMatches(
            int nTestMatches,
            Pair<EMNeuronEntity, LMNeuronEntity> matchingImages,
            NeuronMatchesDao<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao) {
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> testCDMatches = new ArrayList<>();
        for (int i = 0; i < nTestMatches; i++) {
            CDMatchEntity<EMNeuronEntity, LMNeuronEntity> testCDMatch =
                    createTestCDMatch(matchingImages.getLeft(), matchingImages.getRight(), 113 + i, 0.76 / i);
            neuronMatchesDao.save(testCDMatch);
            testCDMatches.add(testCDMatch);
        }
        return testCDMatches;
    }

    private void retrieveAndCompareCDMatcheshWithImages(
            List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> testCDMatches,
            Function<List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>>,
                    List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>>> dataRetriever) {
        List<CDMatchEntity<EMNeuronEntity, LMNeuronEntity>> persistedCDMatches = dataRetriever.apply(testCDMatches);
        assertEquals(testCDMatches.size(), persistedCDMatches.size());
        persistedCDMatches.sort(Comparator.comparingLong(m -> m.getEntityId().longValue()));
        for (int i = 0; i < testCDMatches.size(); i++) {
            CDMatchEntity<EMNeuronEntity, LMNeuronEntity> testCDMatch = testCDMatches.get(i);
            CDMatchEntity<EMNeuronEntity, LMNeuronEntity> persistedCDMatch = persistedCDMatches.get(i);
            assertEquals(testCDMatch.getEntityId(), persistedCDMatch.getEntityId());
            assertEquals(testCDMatch.getMaskImage(), persistedCDMatch.getMaskImage());
            assertEquals(testCDMatch.getMatchedImage(), persistedCDMatch.getMatchedImage());
            assertEquals(testCDMatch.getCreatedDate(), persistedCDMatch.getCreatedDate());
        }
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    CDMatchEntity<M, T> createTestCDMatch(M maskNeuron, T targetNeuron, int pixelMatch, double pixelMatchRatio, String... tags) {
        CDMatchEntity<M, T> testMatch = new CDMatchEntity<>();
        testMatch.setMaskImage(maskNeuron);
        testMatch.setMatchedImage(targetNeuron);
        testMatch.setMatchingPixels(pixelMatch);
        testMatch.setMatchingPixelsRatio((float) pixelMatchRatio);
        testMatch.addAllTags(Arrays.asList(tags));
        addTestData(testMatch);
        return testMatch;
    }

    @SuppressWarnings("unchecked")
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity, R extends CDMatchEntity<M, T>> void addTestData(R o) {
        ((List<CDMatchEntity<M, T>>) testData).add(o);
    }
}
