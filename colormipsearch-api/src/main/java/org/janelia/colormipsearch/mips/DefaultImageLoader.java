package org.janelia.colormipsearch.mips;

import java.io.InputStream;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.io.ImageReader;

public class DefaultImageLoader implements ImageLoader {
    @Override
    public ImageArray loadImage(String fname, InputStream inputStream) {
        try {
            return ImageReader.readImageArrayFromStream(fname, inputStream);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
