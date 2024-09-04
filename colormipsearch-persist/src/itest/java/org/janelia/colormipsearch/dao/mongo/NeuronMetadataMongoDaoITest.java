package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.janelia.colormipsearch.dao.AppendFieldValueHandler;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.FileData;
import org.janelia.colormipsearch.model.Gender;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.janelia.colormipsearch.model.ProcessingType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NeuronMetadataMongoDaoITest extends AbstractMongoDaoITest {

    private final List<AbstractNeuronEntity> testData = new ArrayList<>();

    private NeuronMetadataDao<AbstractNeuronEntity> testDao;

    @Before
    public void setUp() {
        testDao = daosProvider.getNeuronMetadataDao();
    }

    @After
    public void tearDown() {
        deleteAll(testDao, testData);
    }

    @Test
    public void createOrUpdateEmNeuron() {
        String testTag = "createOrUpdateEmNeuron";
        EMNeuronEntity testEmNeuron = createEMTestNeuron(
                EMNeuronEntity::new,
                "flyem",
                "123445",
                "mip123",
                Collections.singleton(testTag));
        AbstractNeuronEntity createdEmNeuron = testDao.createOrUpdate(testEmNeuron);
        assertNull(testEmNeuron.getEntityId());
        assertNotNull(createdEmNeuron.getEntityId());
        // set the entity ID so that this gets deleted at the end of the test
        testEmNeuron.setEntityId(createdEmNeuron.getEntityId());
        assertEquals(testEmNeuron, createdEmNeuron);

        testEmNeuron.setComputeFileData(ComputeFileType.GradientImage, FileData.fromString("GradientImage"));
        testDao.createOrUpdate(testEmNeuron);

        AbstractNeuronEntity persistedEmNeuron = testDao.findByEntityId(testEmNeuron.getEntityId());
        assertEquals(testEmNeuron, persistedEmNeuron);
        assertNotSame(testEmNeuron, persistedEmNeuron);
        assertTrue(persistedEmNeuron.getTags().contains(testTag));
    }

    @Test
    public void createOrUpdateNonIdentifiableEmNeuron() {
        EMNeuronEntity testEmNeuron = createEMTestNeuron(
                EMNeuronEntity::new,
                "flyem",
                "123445",
                "mip123",
                Collections.singleton("createOrUpdateNonIdentifiableEmNeuron"));
        EMNeuronEntity n1 = testEmNeuron.duplicate();
        n1.setComputeFileData(ComputeFileType.InputColorDepthImage, null); // reset input file
        testData.add(n1);
        EMNeuronEntity n2 = n1.duplicate();
        n1.setComputeFileData(ComputeFileType.InputColorDepthImage, null); // reset input file
        testData.add(n2);

        AbstractNeuronEntity createdEmNeuron = testDao.createOrUpdate(n1);
        assertNotNull(n1.getEntityId());
        assertNotNull(createdEmNeuron.getEntityId());
        assertEquals(n1, createdEmNeuron);

        testEmNeuron.setComputeFileData(ComputeFileType.GradientImage, FileData.fromString("GradientImage"));
        AbstractNeuronEntity anotherCreatedEmNeuron = testDao.createOrUpdate(n2);
        assertNotNull(n2.getEntityId());
        assertNotNull(anotherCreatedEmNeuron.getEntityId());
        assertNotEquals(createdEmNeuron.getEntityId(), anotherCreatedEmNeuron.getEntityId());

        AbstractNeuronEntity persistedEmNeuron = testDao.findByEntityId(n2.getEntityId());
        assertEquals(n2, persistedEmNeuron);
        assertNotSame(n2, persistedEmNeuron);
    }

    @Test
    public void saveFollowedByCreateOrUpdateEmNeuron() {
        EMNeuronEntity testEmNeuron = createEMTestNeuron(
                EMNeuronEntity::new,
                "flyem",
                "123445",
                "mip123",
                Collections.singleton("saveFollowedByCreateOrUpdateEmNeuron"));
        testDao.save(testEmNeuron);
        Number testEmId = testEmNeuron.getEntityId();

        EMNeuronEntity testEmNeuronCopy = testEmNeuron.duplicate();
        testEmNeuronCopy.setComputeFileData(ComputeFileType.GradientImage, FileData.fromString("GradientImage"));
        AbstractNeuronEntity updatedEmNeuron = testDao.createOrUpdate(testEmNeuronCopy);
        assertEquals(testEmId, updatedEmNeuron.getEntityId());

        AbstractNeuronEntity persistedEmNeuron = testDao.findByEntityId(testEmId);
        assertEquals(testEmId, persistedEmNeuron.getEntityId());

        assertEquals(testEmNeuron, persistedEmNeuron);
        assertNotSame(testEmNeuron, persistedEmNeuron);
    }

    @Test
    public void tagsAreNoUpdated() {
        String testTag = "tagsAreNoUpdated";
        EMNeuronEntity testEmNeuron = createEMTestNeuron(
                EMNeuronEntity::new,
                "flyem",
                "123445",
                "mip123",
                Collections.singleton(testTag));
        testDao.save(testEmNeuron);
        Number testEmId = testEmNeuron.getEntityId();

        EMNeuronEntity testEMNeuronCopy = testEmNeuron.duplicate();
        testEMNeuronCopy.setComputeFileData(ComputeFileType.GradientImage, FileData.fromString("GradientImage"));
        String newTestTag = "newTagWhenUpdate";
        testEmNeuron.addTag(newTestTag);
        AbstractNeuronEntity updatedEmNeuron = testDao.createOrUpdate(testEmNeuron);
        assertEquals(testEmId, updatedEmNeuron.getEntityId());

        AbstractNeuronEntity persistedEmNeuron = testDao.findByEntityId(testEmNeuron.getEntityId());
        assertEquals(testEmId, persistedEmNeuron.getEntityId());
        assertEquals(testEmNeuron, persistedEmNeuron);
        assertNotSame(testEmNeuron, persistedEmNeuron);
        // check tags
        assertTrue(persistedEmNeuron.getTags().contains(testTag)); // tag added on create should be present
        assertFalse(persistedEmNeuron.getTags().contains(newTestTag)); // tag added on update should not be set
    }

    @Test
    public void persistEmNeuron() {
        EMNeuronEntity testEmNeuron = createEMTestNeuron(
                EMNeuronEntity::new,
                "flyem",
                "123445",
                "mip123",
                Collections.singleton("persistEmNeuron"));
        testDao.save(testEmNeuron);
        AbstractNeuronEntity persistedEmNeuron = testDao.findByEntityId(testEmNeuron.getEntityId());
        assertEquals(testEmNeuron, persistedEmNeuron);
        assertNotSame(testEmNeuron, persistedEmNeuron);
    }

    @Test
    public void persistLmNeuron() {
        String testTag = "persistLmNeuron";
        LMNeuronEntity testLmNeuron = createLMTestNeuron(
                LMNeuronEntity::new,
                "flylight_mcfo",
                "123445",
                "mip123",
                Collections.singleton(testTag));
        testDao.save(testLmNeuron);
        AbstractNeuronEntity persistedLmNeuron = testDao.findByEntityId(testLmNeuron.getEntityId());
        assertEquals(testLmNeuron, persistedLmNeuron);
        assertTrue(persistedLmNeuron.getTags().contains(testTag));
        assertNotSame(testLmNeuron, persistedLmNeuron);
    }

    @Test
    public void findByLibrary() {
        String testLibrary = "flyem";
        EMNeuronEntity testEmNeuron = createEMTestNeuron(
                EMNeuronEntity::new,
                testLibrary,
                "123445",
                "mip123",
                Collections.singleton("findByLibrary"));
        testDao.save(testEmNeuron);
        PagedResult<? extends AbstractNeuronEntity> persistedEmNeurons = testDao.findNeurons(
                new NeuronSelector().addLibrary(testLibrary),
                new PagedRequest());
        assertEquals(1, persistedEmNeurons.getResultList().size());
        AbstractNeuronEntity persistedEmNeuron = persistedEmNeurons.getResultList().get(0);
        assertEquals(testEmNeuron, persistedEmNeuron);
        assertNotSame(testEmNeuron, persistedEmNeuron);
    }

    @Test
    public void findByLibraryAndType() {
        String testLibrary = "flyem";
        EMNeuronEntity testEmNeuron = createEMTestNeuron(
                EMNeuronEntity::new,
                testLibrary,
                "123445",
                "mip123",
                Collections.singleton("findByLibraryAndType"));
        testDao.save(testEmNeuron);
        PagedResult<? extends AbstractNeuronEntity> persistedEmNeurons = testDao.findNeurons(
                new NeuronSelector().addLibrary(testLibrary).setNeuronClassname(EMNeuronEntity.class.getName()),
                new PagedRequest());
        assertEquals(1, persistedEmNeurons.getResultList().size());
        AbstractNeuronEntity persistedEmNeuron = persistedEmNeurons.getResultList().get(0);
        assertEquals(testEmNeuron, persistedEmNeuron);
        assertNotSame(testEmNeuron, persistedEmNeuron);
    }

    @Test
    public void findDistinctNeurons() {
        String testLibrary = "flyem";
        EMNeuronEntity testEmNeuron = createEMTestNeuron(
                EMNeuronEntity::new,
                testLibrary,
                "123445",
                "mip123",
                Collections.singleton("findDistinctNeurons"));
        int nNeurons = 3;
        for (int i = 0; i < nNeurons; i++) {
            EMNeuronEntity n = testEmNeuron.duplicate();
            testData.add(n);
            testDao.save(n);
        }
        PagedResult<Map<String, Object>> distinctNeurons = testDao.findDistinctNeuronAttributeValues(
                Collections.singletonList("mipId"),
                new NeuronSelector().addLibrary(testLibrary).setNeuronClassname(EMNeuronEntity.class.getName()),
                new PagedRequest());
        assertEquals(1, distinctNeurons.getResultList().size());
    }

    @Test
    public void addProcessingTags() {
        String testLibrary = "flyem";
        int nNeurons = 3;
        Map<Number, String> nIds = new HashMap<>();
        for (int i = 0; i < nNeurons; i++) {
            EMNeuronEntity n = createEMTestNeuron(
                    EMNeuronEntity::new,
                    testLibrary,
                    "1234456",
                    "mip1234",
                    Collections.singleton("addProcessingTags"));
            testDao.save(n);
            nIds.put(n.getEntityId(), n.getMipId());
        }
        int iterations = 3;
        for (int iter = 0; iter < iterations; iter++) {
            Set<String> colorDepthTags = ImmutableSet.of("cd1-" + (iter + 1), "cd2-" + (iter + 1));
            Set<String> pppTags = ImmutableSet.of("ppp1-" + (iter + 1), "ppp2-" + (iter + 1));
            testDao.addProcessingTagsToMIPIDs(nIds.values(), ProcessingType.ColorDepthSearch, colorDepthTags);
            testDao.addProcessingTagsToMIPIDs(nIds.values(), ProcessingType.PPPMatch, pppTags);
            List<AbstractNeuronEntity> persistedNeurons = testDao.findByEntityIds(nIds.keySet());
            assertEquals(nNeurons, persistedNeurons.size());
            persistedNeurons.forEach(n -> {
                assertTrue(n.hasProcessedTags(ProcessingType.ColorDepthSearch, colorDepthTags));
                assertTrue(n.hasProcessedTags(ProcessingType.PPPMatch, pppTags));
            });
        }
    }

    @Test
    public void updateTags() {
        AbstractNeuronEntity[] testNeurons = new AbstractNeuronEntity[]{
                createEMTestNeuron(new TestNeuronEntityBuilder<>(EMNeuronEntity::new)
                        .library("l1")
                        .addTags(Arrays.asList("t1", "t2"))
                        .addProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton("cd1"))
                        .addProcessedTags(ProcessingType.PPPMatch, Collections.singleton("ppp1"))
                ),
                createEMTestNeuron(new TestNeuronEntityBuilder<>(EMNeuronEntity::new)
                        .library("l1")
                        .addTags(Arrays.asList("t1", "t2", "t3"))
                        .addProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton("cd2"))
                        .addProcessedTags(ProcessingType.PPPMatch, Collections.singleton("ppp2"))
                ),
                createLMTestNeuron(new TestNeuronEntityBuilder<>(LMNeuronEntity::new)
                        .library("l2")
                        .addTags(Arrays.asList("t1", "t2"))
                        .addProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton("cd1"))
                        .addProcessedTags(ProcessingType.PPPMatch, Collections.singleton("ppp1"))
                ),
                createLMTestNeuron(new TestNeuronEntityBuilder<>(LMNeuronEntity::new)
                        .library("l2")
                        .addTags(Arrays.asList("t1", "t2", "t3"))
                        .addProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton("cd2"))
                        .addProcessedTags(ProcessingType.PPPMatch, Collections.singleton("ppp2"))
                )
        };
        testDao.saveAll(Arrays.asList(testNeurons));
        // update tags based on data tags
        long n1 = testDao.updateAll(
                new NeuronSelector()
                        .addTags(Arrays.asList("t1", "t2"))
                        .addExcludedTag("t3"),
                ImmutableMap.of("tags", new AppendFieldValueHandler<>(Collections.singleton("newTag1")))
        );
        assertEquals(2, n1);
        PagedResult<? extends AbstractNeuronEntity> neuronsUpdatedWithNewTag1 = testDao.findNeurons(new NeuronSelector().addTag("newTag1"), new PagedRequest());
        assertEquals(2, neuronsUpdatedWithNewTag1.getResultList().size());
        neuronsUpdatedWithNewTag1.getResultList().forEach(n -> {
            assertFalse(n.getTags().contains("t3"));
        });
        // update tags based on a single processed tag and excluded tag
        assertEquals(0, // nothing should be updated because all "cd2" and "ppp2" have "t3"
                testDao.updateAll(
                        new NeuronSelector()
                                .addProcessedTag("ColorDepthSearch", "cd2")
                                .addProcessedTag("PPPMatch", "ppp2")
                                .addExcludedTag("t3"),
                        ImmutableMap.of("tags", new AppendFieldValueHandler<>(Collections.singleton("newTag2")))
                ));
        // update tags based on a single processed tag
        long n2 = testDao.updateAll(
                new NeuronSelector()
                        .addProcessedTag("ColorDepthSearch", "cd2")
                        .addProcessedTag("PPPMatch", "ppp2"),
                ImmutableMap.of("tags", new AppendFieldValueHandler<>(Collections.singleton("newTag2")))
        );
        assertEquals(2, n2);
        PagedResult<? extends AbstractNeuronEntity> neuronsUpdatedWithNewTag2 = testDao.findNeurons(new NeuronSelector().addTag("newTag2"), new PagedRequest());
        assertEquals(2, neuronsUpdatedWithNewTag2.getResultList().size());
        neuronsUpdatedWithNewTag2.getResultList().forEach(n -> {
            assertTrue(n.hasProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton("cd2")));
        });
        // update tags based on a multiple processed conflicting tags
        long n3 = testDao.updateAll(
                new NeuronSelector()
                        .addProcessedTag("ColorDepthSearch", "cd1")
                        .addProcessedTag("PPPMatch", "ppp2"),
                ImmutableMap.of("tags", new AppendFieldValueHandler<>(Collections.singleton("newTag3")))
        );
        assertEquals(0, n3);

        // update tags based on a multiple processed tags selections
        long n4 = testDao.updateAll(
                new NeuronSelector()
                        .addProcessedTag("ColorDepthSearch", "cd1")
                        .addNewProcessedTagSelection("PPPMatch", "ppp2")
                        .addProcessedTag("ColorDepthSearch", "cd2"),
                ImmutableMap.of("tags", new AppendFieldValueHandler<>(Collections.singleton("newTag3")))
        );
        assertEquals(4, n4);
    }

    private <N extends EMNeuronEntity> N createEMTestNeuron(Supplier<N> neuronGenerator,
                                                            String libraryName,
                                                            String name,
                                                            String mipId,
                                                            Collection<String> tags) {
        return createEMTestNeuron(
                new TestNeuronEntityBuilder<>(neuronGenerator)
                        .library(libraryName)
                        .publishedName(name)
                        .mipId(mipId)
                        .addTags(tags)
                        .computeFileData(ComputeFileType.InputColorDepthImage, FileData.fromString("mipSegmentation"))
                        .computeFileData(ComputeFileType.SourceColorDepthImage, FileData.fromString("sourceMip"))
                        .addProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton("cds"))
                        .addProcessedTags(ProcessingType.PPPMatch, Collections.singleton("pppm"))
        );
    }

    private <N extends LMNeuronEntity> N createLMTestNeuron(Supplier<N> neuronGenerator,
                                                            String libraryName,
                                                            String name,
                                                            String mipId,
                                                            Collection<String> tags) {
        return createLMTestNeuron(new TestNeuronEntityBuilder<>(neuronGenerator)
                .library(libraryName)
                .publishedName(name)
                .mipId(mipId)
                .addTags(tags)
                .computeFileData(ComputeFileType.InputColorDepthImage, FileData.fromString("mipSegmentation"))
                .computeFileData(ComputeFileType.SourceColorDepthImage, FileData.fromString("sourceMip"))
                .addProcessedTags(ProcessingType.ColorDepthSearch, Collections.singleton("cds"))
                .addProcessedTags(ProcessingType.PPPMatch, Collections.singleton("pppm"))
        );
    }

    private <N extends EMNeuronEntity> N createEMTestNeuron(TestNeuronEntityBuilder<N> neuronGenerator) {
        N testNeuron = neuronGenerator.get();
        testData.add(testNeuron);
        return testNeuron;
    }

    private <N extends LMNeuronEntity> N createLMTestNeuron(TestNeuronEntityBuilder<N> neuronGenerator) {
        N testNeuron = neuronGenerator.get();
        testNeuron.setGender(Gender.f);
        testData.add(testNeuron);
        return testNeuron;
    }

}
