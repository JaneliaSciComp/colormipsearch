package org.janelia.colormipsearch.image.minmax;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class HyperSphereMask {

    final long[] radii;
    // region center
    final long[] center;
    // region boundaries
    final long[] min;
    final long[] max;
    final long[] tmpCoords;
    Img<UnsignedByteType> maskBytes;


    public HyperSphereMask(long[] radii) {
        this.radii = radii;
        this.center = new long[radii.length];
        this.min = new long[radii.length];
        this.max = new long[radii.length];
        this.tmpCoords = new long[radii.length];
        this.maskBytes = createMask(radii);
    }

    private HyperSphereMask(HyperSphereMask c) {
        this.radii = c.radii.clone();
        this.center = c.center.clone();
        this.min = c.min.clone();
        this.max = c.max.clone();
        this.tmpCoords = c.tmpCoords.clone();
        this.maskBytes = c.maskBytes.copy();
    }

    private Img<UnsignedByteType> createMask(long[] radii) {
        long[] dims = new long[radii.length];
        long[] ellipseCenter = new long[radii.length];
        for (int d = 0; d < radii.length; d++) {
            dims[d] = 2 * (radii[d]+1);
            ellipseCenter[d] = 1+radii[d];
        }
        Img<UnsignedByteType> ellipseImg = ArrayImgs.unsignedBytes(dims);
        Cursor<UnsignedByteType> c = Views.flatIterable(ellipseImg).localizingCursor();
        long[] p = new long[radii.length];
        while (c.hasNext()) {
            UnsignedByteType px = c.next();
            c.localize(p);
            if (insideEllipsoid(p, ellipseCenter, radii)) {
                px.set(255);
            } else {
                px.set(0);
            }
        }
        return ellipseImg;
    }

    private boolean insideEllipsoid(long[] p, long[] c, long[] r) {
        double dist = 0;
        for (int d = 0; d < p.length; d++) {
            double delta = p[d] - c[d];
            dist += (delta * delta) / (r[d] * r[d]);
        }
        return dist <= 1;
    }

    public RandomAccessibleInterval<UnsignedByteType> getMaskBytes() {
        return maskBytes;
    }

    public RandomAccessibleInterval<UnsignedByteType> getMaskInterval(Interval interval) {
        if (interval == null) {
            return maskBytes;
        } else {
            return Views.interval(maskBytes, interval);
        }
    }

    public HyperSphereMask copy() {
        return new HyperSphereMask(this);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("center", center)
                .append("radii", radii)
                .toString();
    }
}
