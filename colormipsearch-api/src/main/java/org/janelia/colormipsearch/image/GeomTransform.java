package org.janelia.colormipsearch.image;

/**
 * This type of Geometric transformation preserves dimensionality.
 */
public interface GeomTransform {
    void apply(long[] currentPos, long[] originPos);

    default GeomTransform compose(GeomTransform before) {
        GeomTransform thisOp = this;
        return (source, target) -> {
            assert source.length == target.length;
            long[] tmp = new long[source.length];
            before.apply(source, tmp);
            thisOp.apply(tmp, target);
        };
    }

    default GeomTransform andThen(GeomTransform after) {
        GeomTransform thisOp = this;
        return (source, target) -> {
            assert source.length == target.length;
            long[] tmp = new long[source.length];
            thisOp.apply(source, tmp);
            after.apply(tmp, target);
        };
    }
}
