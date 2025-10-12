package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.EMNeuronEntity;
import org.janelia.colormipsearch.model.LMNeuronEntity;
import org.janelia.colormipsearch.model.PPPMatchEntity;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

public class PPPMatchesMongoDaoITest extends AbstractMongoDaoITest {

    private final List<? extends PPPMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> testData = new ArrayList<>();

    @After
    public <M extends AbstractNeuronEntity,
            T extends AbstractNeuronEntity>
    void tearDown() {
        // delete the data that was created for testing
        NeuronMatchesDao<PPPMatchEntity<M, T>> neuronMatchesDao = daosProvider.getPPPMatchesDao();
        @SuppressWarnings("unchecked")
        List<PPPMatchEntity<M, T>> toDelete = (List<PPPMatchEntity<M, T>>) testData;
        deleteAll(neuronMatchesDao, toDelete);
    }

    @Test
    public void persistPPPMatchTestData() {
        NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao = daosProvider.getNeuronMetadataDao();
        EMNeuronEntity em = createNeuronEntity(
                neuronMetadataDao,
                new TestNeuronEntityBuilder<>(EMNeuronEntity::new)
                        .mipId("10")
                        .get()
                );
        try {
            PPPMatchEntity<EMNeuronEntity, LMNeuronEntity> testPPPMatch =
                    createTestPPPMatch(em, 0.5);
            NeuronMatchesDao<PPPMatchEntity<EMNeuronEntity, LMNeuronEntity>> neuronMatchesDao =
                    daosProvider.getPPPMatchesDao();
            neuronMatchesDao.save(testPPPMatch);
            PPPMatchEntity<EMNeuronEntity, LMNeuronEntity> persistedPPPMatch = neuronMatchesDao.findByEntityId(testPPPMatch.getEntityId());
            assertNotNull(persistedPPPMatch);
            assertNotSame(testPPPMatch, persistedPPPMatch);
            assertEquals(testPPPMatch.getEntityId(), persistedPPPMatch.getEntityId());
            assertNotNull(persistedPPPMatch.getMaskImage());
            assertNull(persistedPPPMatch.getMatchedImage());
            assertEquals(testPPPMatch.getMaskImageRefId().toString(), persistedPPPMatch.getMaskImageRefId().toString());
            assertNull(persistedPPPMatch.getMatchedImageRefId());
        } finally {
            deleteAll(neuronMetadataDao, Collections.singletonList(em));
        }
    }

    private <N extends AbstractNeuronEntity> N createNeuronEntity(NeuronMetadataDao<AbstractNeuronEntity> neuronMetadataDao, N neuronMetadata) {
        neuronMetadataDao.save(neuronMetadata);
        return neuronMetadata;
    }

    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity>
    PPPMatchEntity<M, T> createTestPPPMatch(M maskNeuron, double rank) {
        PPPMatchEntity<M, T> testMatch = new PPPMatchEntity<>();
        testMatch.setSourceEmName("sourceEm");
        testMatch.setMaskImage(maskNeuron);
        testMatch.setSourceLmName("sourceLm");
        testMatch.setRank(rank);
        addTestData(testMatch);
        return testMatch;
    }

    @SuppressWarnings("unchecked")
    private <M extends AbstractNeuronEntity, T extends AbstractNeuronEntity, R extends PPPMatchEntity<M, T>> void addTestData(R o) {
        ((List<PPPMatchEntity<M, T>>) testData).add(o);
    }
}
