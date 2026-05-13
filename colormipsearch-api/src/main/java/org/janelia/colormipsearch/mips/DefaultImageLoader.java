package org.janelia.colormipsearch.mips;

import java.io.InputStream;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.io.ImageReader;

public class DefaultImageLoader extends AbstractImageLoader {

    public DefaultImageLoader(String alignmentSpace) {
        super(alignmentSpace);
    }

    @Override
    protected ImageArray loadImageFromStream(String fname, InputStream inputStream) {
        try {
            return ImageReader.readImageArrayFromStream(fname, inputStream);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
