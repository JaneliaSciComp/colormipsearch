package org.janelia.colormipsearch.imageprocessing;

import java.io.Serializable;

/**
 * Wrapper for composing multiple image transformations.
 */
public class ImageProcessing implements Serializable {

    public static ImageProcessing create(ImageTransformation imageTransformation) {
        return new ImageProcessing(imageTransformation);
    }

    public static ImageProcessing create() {
        return new ImageProcessing();
    }

    private final ImageTransformation imageTransformation;

    private ImageProcessing() {
        this(ImageTransformation.IDENTITY);
    }

    private ImageProcessing(ImageTransformation imageTransformation) {
        this.imageTransformation = imageTransformation;
    }

    public final ImageProcessing maxFilter(double radius) {
        return new ImageProcessing(imageTransformation.extend(ImageTransformation.maxFilter(radius)));
    }

    public final ImageProcessing unsafeMaxFilter(double radius) {
        return new ImageProcessing(imageTransformation.extend(ImageTransformation.unsafeMaxFilter(radius)));
    }

    public final ImageProcessing horizontalMirror() {
        return new ImageProcessing(imageTransformation.extend(ImageTransformation.horizontalMirror()));
    }

    public final ImageProcessing applyColorTransformation(ColorTransformation colorTransformation) {
        return new ImageProcessing(imageTransformation.fmap(colorTransformation));
    }

    public final ImageProcessing thenExtend(ImageTransformation f) {
        return new ImageProcessing(imageTransformation.extend(f));
    }

    public LImage applyTo(LImage image) {
        return image.mapi(imageTransformation);
    }

    public LImage applyTo(org.janelia.colormipsearch.image.ImageArray image, int leftBorder, int topBorder, int rightBorder, int bottomBorder) {
        return applyTo(LImageUtils.create(image, leftBorder, topBorder, rightBorder, bottomBorder));
    }
}
