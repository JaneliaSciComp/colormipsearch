package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.InputStream;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrayImageLoader<P extends IntegerType<P>> extends AbstractImageLoader<P> {

    private static final Logger LOG = LoggerFactory.getLogger(GrayImageLoader.class);

    private final P pxType;

    public GrayImageLoader(String alignmentSpace, P pxType) {
        super(alignmentSpace);
        this.pxType = pxType;
    }

    @Override
    public RandomAccessibleInterval<P> loadImage(FileData fd) {
        return loadGrayImage(fd, pxType);
    }

    private RandomAccessibleInterval<P> loadGrayImage(FileData fd, P p) {
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
            return ImageReader.readImageFromStream(inputStream, p);
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
