package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;

public class MaskedImageViewAdapter extends AbstractImageViewAdapter {

    // if either the value or the position match the conditions specified by the maskPredicate, i.e.,
    // maskPredicate.checkPixelPos(pixelCoords) || maskPredicate.checkPixelVal(pixelVal),
    // then the returned pixel value at the corresponding coordinates will be the maskValue
    private final ImageMaskPredicate maskPredicate;
    private final int maskValue;

    public MaskedImageViewAdapter(ImageMaskPredicate maskPredicate, int maskValue) {
        this.maskPredicate = maskPredicate;
        this.maskValue = maskValue;
    }

    @Override
    public int getPackedIntValAtIndex(ImageArray imageArray, int pi) {
        int pixelVal = imageArray.getPackedIntValAtIndex(pi);
        if (maskPredicate.checkPixelVal(pixelVal)) {
            return maskValue;
        } else {
            int xySize = imageArray.getWidth() * imageArray.getHeight();
            int xyIndex = pi % xySize;
            int x = xyIndex % imageArray.getWidth();
            int y = xyIndex / imageArray.getWidth();
            int z = pi / xySize;
            if (maskPredicate.checkPixelPos(imageArray, x, y, z)) {
                return maskValue;
            } else {
                return pixelVal;
            }
        }
    }

    @Override
    public int getChannelIntValAtIndex(ImageArray imageArray, int pi, int ch) {
        int channelVal = imageArray.getChannelIntValAtIndex(pi, ch);
        if (maskPredicate.checkPixelVal(channelVal)) {
            return maskValue;
        } else {
            int xySize = imageArray.getWidth() * imageArray.getHeight();
            int xyIndex = pi % xySize;
            int x = xyIndex % imageArray.getWidth();
            int y = xyIndex / imageArray.getWidth();
            int z = pi / xySize;
            if (maskPredicate.checkPixelPos(imageArray, x, y, z)) {
                return maskValue;
            } else {
                return channelVal;
            }
        }
    }

    @Override
    public int getPackedIntValAtCoords(ImageArray imageArray, int x, int y, int z) {
        if (maskPredicate.checkPixelPos(imageArray, x, y, z)) {
            return maskValue;
        } else {
            int pixelVal = imageArray.getPackedIntValAtCoords(x, y, z);
            if (maskPredicate.checkPixelVal(pixelVal))
                return maskValue;
            else
                return pixelVal;
        }
    }

    @Override
    public int getChannelIntValAtCoords(ImageArray imageArray, int x, int y, int z, int ch) {
        if (maskPredicate.checkPixelPos(imageArray, x, y, z)) {
            return maskValue;
        } else {
            int channelVal = imageArray.getChannelIntValAtCoords(x, y, z, ch);
            if (maskPredicate.checkPixelVal(channelVal))
                return maskValue;
            else
                return channelVal;
        }
    }
}
