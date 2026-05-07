package org.janelia.colormipsearch.mips;

import java.io.InputStream;

import org.janelia.colormipsearch.image.ImageArray;

public interface ImageLoader {
    int getExpectedWidth();
    int getExpectedHeight();
    int getExpectedDepth();
    ImageArray loadImage(String name, InputStream inputStream);
}
