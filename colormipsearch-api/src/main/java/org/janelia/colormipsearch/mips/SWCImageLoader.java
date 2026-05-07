package org.janelia.colormipsearch.mips;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.io.SWCImageReader;

public class SWCImageLoader implements ImageLoader {

    private static class AlignmentSpaceParams {
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

    private static final Map<String, AlignmentSpaceParams> ALIGNMENT_SPACE_PARAMS = new HashMap<String, AlignmentSpaceParams>() {{
        put("JRC2018_Unisex_20x_HR", new AlignmentSpaceParams/*brain*/(
                1210, 566, 174,
                0.5189161, 0.5189161,1.0
        ));
        put("JRC2018_VNC_Unisex_40x_DS", new AlignmentSpaceParams/*vnc*/(
                573, 1119, 219,
                0.4611220, 0.4611220, 0.7
        ));
    }};

    private final String alignmentSpace;

    public SWCImageLoader(String alignmentSpace) {
        this.alignmentSpace = alignmentSpace;
    }

    @Override
    public ImageArray loadImage(String fname, InputStream inputStream) {
        AlignmentSpaceParams asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("Invalid alignment space: " + alignmentSpace);
        }
        return SWCImageReader.readSWCStream(
                inputStream,
                asParams.width, asParams.height, asParams.depth,
                asParams.xScaling, asParams.yScaling, asParams.zScaling,
                1
        );
    }
}
