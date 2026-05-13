package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractImageLoader implements ImageLoader {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractImageLoader.class);

    static class AlignmentSpaceParams {
        final int width;
        final int height;
        final int depth;
        final double xScaling;
        final double yScaling;
        final double zScaling;

        private AlignmentSpaceParams(int width, int height, int depth,
                                     double xScaling, double yScaling, double zScaling) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.xScaling = xScaling;
            this.yScaling = yScaling;
            this.zScaling = zScaling;
        }
    }

    static final Map<String, AlignmentSpaceParams> ALIGNMENT_SPACE_PARAMS = new HashMap<String, AlignmentSpaceParams>() {{
        put("JRC2018_Unisex_20x_HR", new AlignmentSpaceParams/*brain*/(
                1210, 566, 174,
                0.5189161, 0.5189161,1.0
        ));
        put("JRC2018_VNC_Unisex_40x_DS", new AlignmentSpaceParams/*vnc*/(
                573, 1119, 219,
                0.4611220, 0.4611220, 0.7
        ));
    }};

    final String alignmentSpace;

    AbstractImageLoader(String alignmentSpace) {
        this.alignmentSpace = alignmentSpace;
    }

    @Override
    public int getExpectedWidth() {
        return ALIGNMENT_SPACE_PARAMS.get(alignmentSpace).width;
    }

    @Override
    public int getExpectedHeight() {
        return ALIGNMENT_SPACE_PARAMS.get(alignmentSpace).height;
    }

    @Override
    public int getExpectedDepth() {
        return ALIGNMENT_SPACE_PARAMS.get(alignmentSpace).depth;
    }

    @Override
    public ImageArray loadImage(FileData imageFileData) {
        long startTime = System.currentTimeMillis();
        InputStream inputStream;
        try {
            inputStream = FileDataUtils.openInputStream(imageFileData);
            if (inputStream == null) {
                LOG.debug("No input stream for {}", imageFileData);
                return null;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try {
            LOG.trace("Load image array from {}", imageFileData);
            return loadImageFromStream(imageFileData.getName(), inputStream);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignore) {
            }
            LOG.trace("Loaded image from {} in {}ms", imageFileData, System.currentTimeMillis() - startTime);
        }
    }

    protected abstract ImageArray loadImageFromStream(String name, InputStream inputStream);
}
