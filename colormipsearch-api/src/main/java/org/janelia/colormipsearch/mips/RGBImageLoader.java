package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.InputStream;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RGBImageLoader<P extends RGBPixelType<P>> extends AbstractImageLoader<P> {

    private static final Logger LOG = LoggerFactory.getLogger(RGBImageLoader.class);

    private final P pxType;

    public RGBImageLoader(String alignmentSpace, P pxType) {
        super(alignmentSpace);
        this.pxType = pxType;
    }

    @Override
    public RandomAccessibleInterval<P> loadImage(FileData fd) {
        return loadRGBImage(fd, pxType);
    }

    private RandomAccessibleInterval<P> loadRGBImage(FileData fd, P p) {
        long startTime = System.currentTimeMillis();
        InputStream inputStream;
        try {
            inputStream = FileDataUtils.openInputStream(fd);
            if (inputStream == null) {
                return null;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try {
            return ImageReader.readRGBImageFromStream(inputStream, p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignore) {
            }
            LOG.trace("Loaded image from {} in {}ms", fd, System.currentTimeMillis() - startTime);
        }
    }

}
