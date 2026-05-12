package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.janelia.colormipsearch.dao.PublishedLMImageDao;
import org.janelia.colormipsearch.model.AbstractBaseEntity;
import org.janelia.colormipsearch.model.PublishedLMImage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PublishedLMImageMongoDaoITest extends AbstractMongoDaoITest {

    private Map<Number, PublishedLMImage> testImages = new HashMap<>();

    private PublishedLMImageDao publishedLMImageDao;

    @Before
    public void setUp() {
        publishedLMImageDao = mongoDaosProvider.getPublishedImageDao();
        testImages.putAll(createTestImages());
    }

    @After
    public void tearDown() {
        testImages.forEach((id, img) -> publishedLMImageDao.delete(img));
    }

    private Map<Number, PublishedLMImage> createTestImages() {
        List<PublishedLMImage> images = new ArrayList<>();

        PublishedLMImage image1 = new PublishedLMImage();
        image1.setLine("line 1");
        image1.setSampleRef("Sample#1234");
        image1.setArea("brain");
        image1.setTile("tile 1");
        image1.setReleaseName("Gen1 GAL4");
        image1.setSlideCode("line-date_1_A1");
        image1.setOriginalLine("original-line-1");
        image1.setObjective("40x");
        image1.setAlignmentSpace("JRC2018_Unisex_20x_HR");
        image1.addFile("VisuallyLosslessStack", "http://s3/images/etc");
        images.add(image1);

        PublishedLMImage image2 = new PublishedLMImage();
        image2.setLine("line 2");
        image2.setSampleRef("Sample#5678");
        image2.setArea("brain");
        image2.setTile("tile 2");
        image2.setReleaseName("Gen1 GAL4");
        image2.setSlideCode("line-date_2_B3");
        image2.setOriginalLine("original-line-2");
        image2.setObjective("40x");
        image2.setAlignmentSpace("JRC2018_Unisex_20x_HR");
        image2.addFile("VisuallyLosslessStack", "http://s3/images/etc2");
        images.add(image2);

        PublishedLMImage image3 = new PublishedLMImage();
        image3.setLine("line 3");
        image3.setSampleRef("Sample#1357");
        image3.setArea("brain");
        image3.setTile("tile 3");
        image3.setReleaseName("Gen1 LexA");
        image3.setSlideCode("line-date_3_C5");
        image3.setOriginalLine("original-line-3");
        image3.setObjective("40x");
        image3.setAlignmentSpace("JRC2018_Unisex_20x_HR");
        image3.addFile("VisuallyLosslessStack", "http://s3/images/etc3");
        images.add(image3);

        PublishedLMImage image4 = new PublishedLMImage();
        image4.setLine("line 3");
        image4.setSampleRef("Sample#1357");
        image4.setArea("vnc");
        image4.setTile("tile 3");
        image4.setReleaseName("Gen1 LexA");
        image4.setSlideCode("line-date_3_C5");
        image4.setOriginalLine("original-line-3");
        image4.setObjective("40x");
        image4.setAlignmentSpace("JRC2018_VNC_Unisex_40x_DS");
        image4.addFile("VisuallyLosslessStack", "http://s3/images/etc3");
        images.add(image4);

        publishedLMImageDao.saveAll(images);
        return images.stream().collect(Collectors.toMap(AbstractBaseEntity::getEntityId, i -> i));
    }

    @Test
    public void testGetImage() {
        Map<Pair<String, String>, List<PublishedLMImage>> testImagesByAlignmentSpaceAndObjective =
                testImages.values().stream().collect(Collectors.groupingBy(
                        i -> ImmutablePair.of(i.getAlignmentSpace(), i.getObjective()),
                        Collectors.toList()
                ));

        testImagesByAlignmentSpaceAndObjective.forEach((asAndObjective, testImagesSubset) -> {
            Set<String> testSampleRefs = testImagesSubset.stream().map(PublishedLMImage::getSampleRef).collect(Collectors.toSet());
            Map<String, List<PublishedLMImage>> foundImages = publishedLMImageDao.getPublishedImagesWithGal4BySampleObjectives(asAndObjective.getLeft(), testSampleRefs, asAndObjective.getRight());
            assertEquals(testSampleRefs.size(), foundImages.size());
            compareImages(testImagesSubset, foundImages.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
        });
    }

    private void compareImages(Collection<PublishedLMImage> referenceImages, Collection<PublishedLMImage> toCheck) {
        Map<Number, PublishedLMImage> indexedReferenceImages = referenceImages.stream().collect(Collectors.toMap(AbstractBaseEntity::getEntityId, i -> i));
        toCheck.forEach(foundImage -> {
            PublishedLMImage image = indexedReferenceImages.get(foundImage.getEntityId());
            assertNotNull(image);
            // test a few key attributes
            assertEquals(image.getEntityId(), foundImage.getEntityId());
            assertEquals(image.getSampleRef(), foundImage.getSampleRef());
            assertEquals(image.getTile(), foundImage.getTile());
            assertEquals(image.getOriginalLine(), foundImage.getOriginalLine());
            assertNull(image.getGal4Expressions());
            assertNotNull(foundImage.getGal4Expressions());
            assertEquals(1, foundImage.getGal4Expressions().size());
            for (String key: image.getFiles().keySet()) {
                assertTrue(foundImage.getFiles().containsKey(key));
                assertEquals(image.getFiles().get(key), foundImage.getFiles().get(key));
            }
        });
    }


}
