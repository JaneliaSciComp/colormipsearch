package org.janelia.colormipsearch.mips;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.model.FileData;

public interface ImageLoader {
    int getExpectedWidth();
    int getExpectedHeight();
    int getExpectedDepth();
    ImageArray loadImage(FileData imageFileData);
}
