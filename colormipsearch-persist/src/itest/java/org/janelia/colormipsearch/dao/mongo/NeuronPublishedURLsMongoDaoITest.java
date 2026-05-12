package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableMap;

import org.janelia.colormipsearch.dao.PublishedURLsDao;
import org.janelia.colormipsearch.model.AbstractBaseEntity;
import org.janelia.colormipsearch.model.NeuronPublishedURLs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NeuronPublishedURLsMongoDaoITest extends AbstractMongoDaoITest {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    static class TestNeuronPublishedURLs extends NeuronPublishedURLs {
        private String sampleRef;

        @Override
        public Map<String, String> getUrls() {
            return super.getUrls();
        }

        @JsonIgnore
        @Override
        public String getEntityClass() {
            return super.getEntityClass();
        }

        @JsonIgnore
        @Override
        public Date getCreatedDate() {
            return super.getCreatedDate();
        }

        @JsonProperty
        public String getSampleRef() {
            return sampleRef;
        }

        public void setSampleRef(String sampleRef) {
            this.sampleRef = sampleRef;
        }
    }

    private Map<Number, NeuronPublishedURLs> testData = new HashMap<>();

    private PublishedURLsDao<NeuronPublishedURLs> publishedURLsDao;

    @Before
    public void setUp() {
        publishedURLsDao = mongoDaosProvider.getNeuronPublishedUrlsDao();
        testData.putAll(createTestData());
    }

    @After
    public void tearDown() {
        testData.forEach((id, entry) -> publishedURLsDao.delete(entry));
    }

    private Map<Number, NeuronPublishedURLs> createTestData() {
        List<NeuronPublishedURLs> data = new ArrayList<>();

        TestNeuronPublishedURLs d1 = new TestNeuronPublishedURLs();
        d1.getUrls().putAll(ImmutableMap.of(
                "cdm", "cdm_URL_1",
                "cdm_thumbnail", "cdm_thumnail_URL_1",
                "searchable_neurons", "searchable_neurons_URL_1",
                "swc", "swc_URL_1",
                "obj", "obj_URL_1"
        ));
        d1.setSampleRef("s1");
        data.add(d1);

        TestNeuronPublishedURLs d2 = new TestNeuronPublishedURLs();
        d2.getUrls().putAll(ImmutableMap.of(
                "cdm", "cdm_URL_2",
                "cdm_thumbnail", "cdm_thumnail_URL_2",
                "searchable_neurons", "searchable_neurons_URL_2",
                "swc", "swc_URL_2",
                "obj", "obj_URL_2"
        ));
        d2.setSampleRef("s2");
        data.add(d2);

        TestNeuronPublishedURLs d3 = new TestNeuronPublishedURLs();
        d3.getUrls().putAll(ImmutableMap.of(
                "cdm", "cdm_URL_3",
                "cdm_thumbnail", "cdm_thumnail_URL_3",
                "searchable_neurons", "searchable_neurons_URL_3",
                "swc", "swc_URL_3",
                "obj", "obj_URL_3"
        ));
        d3.setSampleRef("s3");
        data.add(d3);

        TestNeuronPublishedURLs d4 = new TestNeuronPublishedURLs();
        d4.getUrls().putAll(ImmutableMap.of(
                "cdm", "cdm_URL_4",
                "cdm_thumbnail", "cdm_thumnail_URL_4",
                "searchable_neurons", "searchable_neurons_URL_4",
                "swc", "swc_URL_4",
                "obj", "obj_URL_4"
        ));
        d4.setSampleRef("s4");
        data.add(d4);

        publishedURLsDao.saveAll(data);
        return data.stream().collect(Collectors.toMap(AbstractBaseEntity::getEntityId, i -> i));
    }

    @Test
    public void testGetURLs() {
        List<NeuronPublishedURLs> persistedData = publishedURLsDao.findByEntityIds(testData.keySet());
        assertEquals(testData.size(), persistedData.size());
        persistedData.forEach(d -> {
            assertNotNull(testData.get(d.getEntityId()));
        });
    }

}
