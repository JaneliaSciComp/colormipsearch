package org.janelia.colormipsearch.image.view;

import java.awt.Image;

import org.janelia.colormipsearch.image.CoordUtils;
import org.janelia.colormipsearch.image.Dimensions;
import org.janelia.colormipsearch.image.ImageArray;

public class HistogramRGBMaxFilterImageViewAdapter extends AbstractImageViewAdapter {

    private final HyperEllipsoidRegion currentRegion;
    private final HyperEllipsoidRegion prevRegion;
    private final ValuesHistogram rHistogram;
    private final ValuesHistogram gHistogram;
    private final ValuesHistogram bHistogram;
    private boolean unintialized;

    public HistogramRGBMaxFilterImageViewAdapter(int xRadius, int yRadius, int zRadius) {
        currentRegion = new HyperEllipsoidRegion(xRadius, yRadius, zRadius);
        prevRegion = new HyperEllipsoidRegion(xRadius, yRadius, zRadius);
        rHistogram = new ValuesHistogram(8);
        gHistogram = new ValuesHistogram(8);
        bHistogram = new ValuesHistogram(8);
        unintialized = true;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        int xySize = imageArray.getWidth() * imageArray.getHeight();
        int xyIndex = pi % xySize;
        int x = xyIndex % imageArray.getWidth();
        int y = xyIndex / imageArray.getWidth();
        int z = pi / xySize;
        return getPackedIntValAtCoords(imageArray, x, y, z);
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        int xySize = imageArray.getWidth() * imageArray.getHeight();
        int xyIndex = pi % xySize;
        int x = xyIndex % imageArray.getWidth();
        int y = xyIndex / imageArray.getWidth();
        int z = pi / xySize;
        return getChannelIntValAtCoords(imageArray, x, y, z, ch);
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        updatePos(imageArray, x, y, z);
        return getVal();
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        updatePos(imageArray, x, y, z);
        return getChVal(ch);
    }

    void resetChVals() {
        rHistogram.clear();
        gHistogram.clear();
        bHistogram.clear();
    }

    int getVal() {
        int maxR = rHistogram.maxVal();
        int maxG = gHistogram.maxVal();
        int maxB = bHistogram.maxVal();
        return maxR << 16 | maxG << 8 | maxB;
    }

    int getChVal(int ch) {
        switch (ch) {
            case 0: return rHistogram.maxVal();
            case 1: return gHistogram.maxVal();
            case 2: return bHistogram.maxVal();
            default: throw new IllegalArgumentException("Invalid channel: " + ch);
        }
    }

    private void addChVals(ImageArray imageArray, int x, int y, int z) {
        int pi =  imageArray.getSpatialLinearIndex(x, y, z);
        rHistogram.add(imageArray.getChannelIntValAtIndex(pi, 0));
        gHistogram.add(imageArray.getChannelIntValAtIndex(pi, 1));
        bHistogram.add(imageArray.getChannelIntValAtIndex(pi, 2));
    }

    private void removeChVals(ImageArray imageArray, int x, int y, int z) {
        int pi =  imageArray.getSpatialLinearIndex(x, y, z);
        rHistogram.remove(imageArray.getChannelIntValAtIndex(pi, 0));
        gHistogram.remove(imageArray.getChannelIntValAtIndex(pi, 1));
        bHistogram.remove(imageArray.getChannelIntValAtIndex(pi, 2));
    }

    private void updatePos(ImageArray imageArray, int cx, int cy, int cz) {
        prevRegion.setLocationTo(currentRegion);
        currentRegion.setCenter(cx, cy, cz);
        if (unintialized || CoordUtils.intersectIsVoid(currentRegion.min(), currentRegion.max(), prevRegion.min(), prevRegion.max())) {
            // No overlap with previous position — full initialization
            unintialized = false;
            resetChVals();
            currentRegion.scan(
                    0,
                    (centerCoords, distance, axis) ->
                            initialScanSegment(imageArray, centerCoords, distance, axis),
                    HyperEllipsoidRegion.ScanDirection.LOW_TO_HIGH
            );

        } else if (!currentRegion.hasSameLocationAs(prevRegion)) {
            // Incremental update: add new pixels, remove old pixels
            // Scan current region HIGH_TO_LOW: add pixels not in previous region
            currentRegion.scan(
                    0,
                    (centerCoords, distance, axis) ->
                            addNewPixels(imageArray, centerCoords, distance, axis),
                    HyperEllipsoidRegion.ScanDirection.HIGH_TO_LOW
            );
            // Scan previous region LOW_TO_HIGH: remove pixels not in current region
            prevRegion.scan(
                    0,
                    (centerCoords, distance, axis) ->
                            removeOldPixels(imageArray, centerCoords, distance, axis),
                    HyperEllipsoidRegion.ScanDirection.LOW_TO_HIGH
            );

        }
    }

    private void initialScanSegment(ImageArray imageArray, int[] pos, int distance, int axis) {
        CoordUtils.addCoords(currentRegion.center(), pos, currentRegion.tmpCoords);
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();
        for (int r = -distance; r <= distance; r++) {
            currentRegion.tmpCoords[axis] = Dimensions.selectDim(currentRegion.cx, currentRegion.cy, currentRegion.cz, axis) + r;
            if (isInBounds(currentRegion.tmpCoords, width, height, depth)) {
                addChVals(imageArray, currentRegion.tmpCoords[0], currentRegion.tmpCoords[1], currentRegion.tmpCoords[2]);
            }
        }
    }

    private void addNewPixels(ImageArray imageArray, int[] pos, int distance, int axis) {
        CoordUtils.addCoords(currentRegion.center(), pos, currentRegion.tmpCoords);
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();
        for (int r = distance; r > 0; r--) {
            // positive side
            currentRegion.tmpCoords[axis] = Dimensions.selectDim(currentRegion.cx, currentRegion.cy, currentRegion.cz, axis) + r;
            if (isInBounds(currentRegion.tmpCoords, width, height, depth)) {
                if (prevRegion.containsLocation(currentRegion.tmpCoords)) {
                    return; // hit intersection — everything inward is shared
                }
                addChVals(imageArray, currentRegion.tmpCoords[0], currentRegion.tmpCoords[1], currentRegion.tmpCoords[2]);
            }
            // negative side
            currentRegion.tmpCoords[axis] = Dimensions.selectDim(currentRegion.cx, currentRegion.cy, currentRegion.cz, axis) - r;
            if (isInBounds(currentRegion.tmpCoords, width, height, depth)) {
                if (prevRegion.containsLocation(currentRegion.tmpCoords)) {
                    return;
                }
                addChVals(imageArray, currentRegion.tmpCoords[0], currentRegion.tmpCoords[1], currentRegion.tmpCoords[2]);
            }
        }
        // center of segment
        currentRegion.tmpCoords[axis] = Dimensions.selectDim(currentRegion.cx, currentRegion.cy, currentRegion.cz, axis);
        if (isInBounds(currentRegion.tmpCoords, width, height, depth)) {
            if (!prevRegion.containsLocation(currentRegion.tmpCoords)) {
                addChVals(imageArray, currentRegion.tmpCoords[0], currentRegion.tmpCoords[1], currentRegion.tmpCoords[2]);
            }
        }

    }

    private void removeOldPixels(ImageArray imageArray, int[] pos, int distance, int axis) {
        // TOD
        CoordUtils.addCoords(prevRegion.center(), pos, prevRegion.tmpCoords);
        int width = imageArray.getWidth();
        int height = imageArray.getHeight();
        int depth = imageArray.getDepth();
        for (int r = distance; r > 0; r--) {
            // positive side
            prevRegion.tmpCoords[axis] = Dimensions.selectDim(prevRegion.cx, prevRegion.cy, prevRegion.cz, axis) + r;
            if (isInBounds(prevRegion.tmpCoords, width, height, depth)) {
                if (currentRegion.containsLocation(prevRegion.tmpCoords)) {
                    return;
                }
                removeChVals(imageArray, prevRegion.tmpCoords[0], prevRegion.tmpCoords[1], prevRegion.tmpCoords[2]);
            }
            // negative side
            prevRegion.tmpCoords[axis] = Dimensions.selectDim(prevRegion.cx, prevRegion.cy, prevRegion.cz, axis) - r;
            if (isInBounds(prevRegion.tmpCoords, width, height, depth)) {
                if (currentRegion.containsLocation(prevRegion.tmpCoords)) {
                    return;
                }
                removeChVals(imageArray, prevRegion.tmpCoords[0], prevRegion.tmpCoords[1], prevRegion.tmpCoords[2]);
            }
        }
        // center of segment
        prevRegion.tmpCoords[axis] = Dimensions.selectDim(prevRegion.cx, prevRegion.cy, prevRegion.cz, axis);
        if (isInBounds(prevRegion.tmpCoords, width, height, depth)) {
            if (!currentRegion.containsLocation(prevRegion.tmpCoords)) {
                removeChVals(imageArray, prevRegion.tmpCoords[0], prevRegion.tmpCoords[1], prevRegion.tmpCoords[2]);
            }
        }
    }

    private boolean isInBounds(int[] coords, int width, int height, int depth) {
        return coords[0] >= 0 && coords[0] < width
                && coords[1] >= 0 && coords[1] < height
                && coords[2] >= 0 && coords[2] < depth;
    }
}
