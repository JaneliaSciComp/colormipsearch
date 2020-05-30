package org.janelia.colormipsearch.api.imageprocessing;

import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

public class LImageUtils {

    public static LImage create(ImageArray imageArray) {
        return new LImageImpl(imageArray.type, imageArray.width, imageArray.height, imageArray::getPixel);
    }

    public static LImage combine2(LImage l1, LImage l2, BinaryOperator<Integer> op) {
        return new LImageImpl(l1.getPixelType(), l1.width(), l1.height(), (x, y) -> op.apply(l1.get(x, y), l2.get(x, y)));
    }

    public static LImage lazyCombine2(LImage l1, LImage l2, BiFunction<Supplier<Integer>, Supplier<Integer>, Integer> op) {
        return new LImageImpl(l1.getPixelType(), l1.width(), l1.height(), (x, y) -> op.apply(() -> l1.get(x, y), () -> l2.get(x, y)));
    }

    public static LImage lazyCombine3(LImage l1, LImage l2, LImage l3, TriFunction<Supplier<Integer>, Supplier<Integer>, Supplier<Integer>, Integer> op) {
        return new LImageImpl(l1.getPixelType(), l1.width(), l1.height(), (x, y) -> op.apply(() -> l1.get(x, y), () -> l2.get(x, y), () -> l3.get(x, y)));
    }

}