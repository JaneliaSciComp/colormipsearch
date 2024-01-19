package org.janelia.colormipsearch.image;

import java.util.function.UnaryOperator;

public interface GeomTransform extends UnaryOperator<long[]> {
    int getSourceDims();
    int getTargetDims();
    default GeomTransform compose(GeomTransform before) {
        int currentTargetDims = getTargetDims();
        UnaryOperator<long[]> operator = this;
        return new GeomTransform() {
            @Override
            public int getSourceDims() {
                return before.getSourceDims();
            }

            @Override
            public int getTargetDims() {
                return currentTargetDims;
            }

            @Override
            public long[] apply(long[] r) {
                return operator.apply(before.apply(r));
            }
        };
    }

    default GeomTransform andThen(GeomTransform after) {
        int currentSourceDims = getSourceDims();
        UnaryOperator<long[]> operator = this;

        return new GeomTransform() {
            @Override
            public int getSourceDims() {
                return currentSourceDims;
            }

            @Override
            public int getTargetDims() {
                return after.getTargetDims();
            }

            @Override
            public long[] apply(long[] s) {
                return after.apply(operator.apply(s));
            }
        };
    }
}
