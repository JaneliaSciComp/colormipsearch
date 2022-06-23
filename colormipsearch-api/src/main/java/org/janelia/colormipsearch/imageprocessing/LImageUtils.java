package org.janelia.colormipsearch.imageprocessing;

import java.util.function.BinaryOperator;

/**
 * Utils for lazy image data type
 */
public class LImageUtils {

    public static LImage create(ImageArray<?> imageArray) {
        return create(imageArray, 0, 0, 0, 0);
    }

    public static LImage create(ImageArray<?> imageArray, int leftBorder, int topBorder, int rightBorder, int bottomBorder) {
        return new LImageImpl(imageArray.type, imageArray.width, imageArray.height,
                leftBorder, topBorder,rightBorder,bottomBorder,
                imageArray::getPixel);
    }

    /**
     * Combine 2 images, pixel wise, using the given operator
     * @param l1
     * @param l2
     * @param op
     * @return
     */
    public static LImage combine2(LImage l1, LImage l2, BinaryOperator<Integer> op) {
        return new LImageImpl(l1.getPixelType(),
                l1.width(), l1.height(),
                l1.leftBorder(), l1.topBorder(), l1.rightBorder(), l1.bottomBorder(),
                (x, y) -> op.apply(l1.get(x, y), l2.get(x, y)));
    }

    /**
     * Combine 4 images, pixel wise using the given operator.
     *
     * @param l1
     * @param l2
     * @param l3
     * @param l4
     * @param op
     * @return
     */
    public static LImage combine4(LImage l1, LImage l2, LImage l3, LImage l4, QuadFunction<Integer, Integer, Integer, Integer, Integer> op) {
        return new LImageImpl(l1.getPixelType(),
                l1.width(), l1.height(),
                l1.leftBorder(), l1.topBorder(), l1.rightBorder(), l1.bottomBorder(),
                (x, y) -> op.apply(l1.get(x, y), l2.get(x, y), l3.get(x, y), l4.get(x, y)));
    }

}
