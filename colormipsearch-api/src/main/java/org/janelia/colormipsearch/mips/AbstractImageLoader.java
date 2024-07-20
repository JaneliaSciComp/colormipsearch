package org.janelia.colormipsearch.mips;

import java.util.HashMap;
import java.util.Map;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.model.FileData;

public abstract class AbstractImageLoader<P extends IntegerType<P>> implements ImageLoader<P> {

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

    AbstractImageLoader(String alignmentSpace) {
        this.alignmentSpace = alignmentSpace;
    }

    @Override
    public int[] getExpectedSize() {
        AlignmentSpaceParams asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("Invalid alignment space: " + alignmentSpace);
        }
        return new int[] {
            asParams.width, asParams.height, asParams.depth
        };
    }

    @Override
    public double[] getVoxelSpacing() {
        AlignmentSpaceParams asParams = ALIGNMENT_SPACE_PARAMS.get(alignmentSpace);
        if (asParams == null) {
            throw new IllegalArgumentException("Invalid alignment space: " + alignmentSpace);
        }
        return new double[] {
                asParams.xScaling, asParams.yScaling, asParams.zScaling
        };
    }

    @Override
    public RandomAccessibleInterval<P> loadImage(FileData fd) {
        return null;
    }





}
