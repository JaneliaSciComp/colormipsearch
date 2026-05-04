package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public interface ImageArrayView extends ImageArray {
    ImageArray getSourceImage();
}
