package org.janelia.colormipsearch.image;

public interface GeomTransform {
    int getSourceDims();
    int getTargetDims();
    void apply(long[] source, long[] target);

    default GeomTransform compose(GeomTransform before) {
        GeomTransform thisOp = this;
        return new GeomTransform() {
            @Override
            public int getSourceDims() {
                return before.getSourceDims();
            }

            @Override
            public int getTargetDims() {
                return thisOp.getTargetDims();
            }

            @Override
            public void apply(long[] source, long[] target) {
                long[] tmp = new long[before.getTargetDims()];
                before.apply(source, tmp);
                thisOp.apply(tmp, target);
            }
        };
    }

    default GeomTransform andThen(GeomTransform after) {
        GeomTransform thisOp = this;
        return new GeomTransform() {
            @Override
            public int getSourceDims() {
                return thisOp.getSourceDims();
            }

            @Override
            public int getTargetDims() {
                return after.getTargetDims();
            }

            @Override
            public void apply(long[] source, long[] target) {
                long[] tmp = new long[thisOp.getTargetDims()];
                thisOp.apply(source, tmp);
                after.apply(tmp, target);
            }
        };
    }
}
