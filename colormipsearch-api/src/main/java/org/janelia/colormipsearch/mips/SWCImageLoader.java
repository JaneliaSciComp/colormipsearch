package org.janelia.colormipsearch.mips;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.io.SWCImageReader;

public class SWCImageLoader extends AbstractImageLoader {

    private final double scale;
    private final int radius;

    public SWCImageLoader(String alignmentSpace, double scale, int radius) {
        super(alignmentSpace);
        this.scale = scale;
        this.radius = radius;
    }

    @Override
    public ImageArray loadImage(String fname, InputStream inputStream) {
        AlignmentSpaceParams asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("Invalid alignment space: " + alignmentSpace);
        }
        return SWCImageReader.readSWCStream(
                inputStream,
                (int) (asParams.width * scale), (int) (asParams.height * scale), (int) (asParams.depth * scale),
                asParams.xScaling / scale, asParams.yScaling / scale, asParams.zScaling / scale,
                radius
        );
    }
}
