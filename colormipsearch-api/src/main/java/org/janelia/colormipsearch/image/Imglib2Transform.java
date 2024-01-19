package org.janelia.colormipsearch.image;

import net.imglib2.transform.Transform;

public class Imglib2Transform implements GeomTransform {

    private final Transform transform;

    public Imglib2Transform(Transform transform) {
        this.transform = transform;
    }

    @Override
    public int getSourceDims() {
        return transform.numSourceDimensions();
    }

    @Override
    public int getTargetDims() {
        return transform.numTargetDimensions();
    }

    @Override
    public long[] apply(long[] coords) {
        long[] target = new long[getTargetDims()];
        transform.apply(coords, target);
        return target;
    }
}
