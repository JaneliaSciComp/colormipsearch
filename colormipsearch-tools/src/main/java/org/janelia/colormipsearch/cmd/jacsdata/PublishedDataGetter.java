package org.janelia.colormipsearch.cmd.jacsdata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.janelia.colormipsearch.dao.PublishedLMImageDao;
import org.janelia.colormipsearch.dao.PublishedURLsDao;
import org.janelia.colormipsearch.model.AbstractBaseEntity;
import org.janelia.colormipsearch.model.PublishedLMImage;
import org.janelia.colormipsearch.model.NeuronPublishedURLs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublishedDataGetter {
    private static final Logger LOG = LoggerFactory.getLogger(PublishedDataGetter.class);

    private final PublishedLMImageDao publishedLMImageDao;
    private final PublishedURLsDao<NeuronPublishedURLs> publishedURLsDao;
    private final Map<String, Set<String>> publishedAlignmentSpaceAliases;

    public PublishedDataGetter(PublishedLMImageDao publishedLMImageDao,
                               PublishedURLsDao<NeuronPublishedURLs> publishedURLsDao,
                               Map<String, Set<String>> publishedAlignmentSpaceAliases) {
        this.publishedLMImageDao = publishedLMImageDao;
        this.publishedURLsDao = publishedURLsDao;
        this.publishedAlignmentSpaceAliases = publishedAlignmentSpaceAliases;
    }

    Map<String, List<PublishedLMImage>> retrievePublishedImages(String alignmentSpace, Set<String> sampleRefs) {
        return publishedLMImageDao.getPublishedImagesWithGal4BySampleObjectives(
                alignmentSpace,
                sampleRefs,
                null
        );
    }

    Map<Number, NeuronPublishedURLs> retrievePublishedURLs(Set<Number> neuronIds) {
        return publishedURLsDao.findByEntityIds(neuronIds).stream()
                .collect(Collectors.toMap(AbstractBaseEntity::getEntityId, urls -> urls));
    }

    void update3DStacksForAllMips(Collection<ColorDepthMIP> colorDepthMIPS) {
        LOG.info("Retrieve {} published images for {} MIPs", colorDepthMIPS.size());
        Map<String, List<PublishedLMImage>> publishedImagesBySampleRefsForAllAlignmentSpaces =
                retrievePublishedImages(
                        null,
                        colorDepthMIPS.stream().map(cdmip -> cdmip.sampleRef).collect(Collectors.toSet())
                    );
        colorDepthMIPS.forEach(cdmip -> {
            LOG.debug("Update 3D stack for mipID {}", cdmip.id);
            update3DStack(cdmip, publishedImagesBySampleRefsForAllAlignmentSpaces);
        });
    }

    private void update3DStack(ColorDepthMIP colorDepthMIP,
                               Map<String, List<PublishedLMImage>> publishedImagesBySampleRefs) {
        if (colorDepthMIP.sample != null) {
            PublishedLMImage publishedLMImage = findPublishedImage(colorDepthMIP, publishedImagesBySampleRefs.get(colorDepthMIP.sampleRef));
            colorDepthMIP.sample3DImageStack = publishedLMImage.getFile("VisuallyLosslessStack");
            colorDepthMIP.sampleGen1Gal4ExpressionImage = publishedLMImage.getGal4Expression4Image(colorDepthMIP.anatomicalArea);
        } else if (colorDepthMIP.emBody != null && colorDepthMIP.emBody.files != null) {
            colorDepthMIP.emSWCFile = colorDepthMIP.emBody.files.get("SkeletonSWC");
            colorDepthMIP.emOBJFile = colorDepthMIP.emBody.files.get("SkeletonOBJ");
        }
    }

    private PublishedLMImage findPublishedImage(ColorDepthMIP colorDepthMIP, List<PublishedLMImage> publishedLMImages) {
        if (CollectionUtils.isEmpty(publishedLMImages)) {
            LOG.warn("No published images provided to lookup {}:sample={}:as={}",
                    colorDepthMIP, colorDepthMIP.sampleRef, colorDepthMIP.alignmentSpace);
            return new PublishedLMImage();
        } else {
            Set<String> aliasesForAlignmentSpace = publishedAlignmentSpaceAliases.getOrDefault(
                    colorDepthMIP.alignmentSpace,
                    Collections.emptySet());
            // we lookup published images in the same alignment space
            return publishedLMImages.stream()
                    .filter(pi -> pi.getAlignmentSpace().equals(colorDepthMIP.alignmentSpace) ||
                            (CollectionUtils.isNotEmpty(aliasesForAlignmentSpace) && aliasesForAlignmentSpace.contains(pi.getAlignmentSpace())))
                    .filter(pi -> !pi.hasObjective() || !colorDepthMIP.hasLmObjective() || pi.getObjective().equals(colorDepthMIP.objective))
                    .findFirst()
                    .orElseGet(() -> {
                        LOG.warn("No published image found for {}:sample={}:as={}",
                                colorDepthMIP, colorDepthMIP.sampleRef, colorDepthMIP.alignmentSpace);
                        return new PublishedLMImage();
                    });
        }
    }

}
