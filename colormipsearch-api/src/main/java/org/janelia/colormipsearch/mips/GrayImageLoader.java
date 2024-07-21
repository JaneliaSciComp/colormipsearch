package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.InputStream;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrayImageLoader<P extends IntegerType<P> & NativeType<P>> extends AbstractImageLoader<P> {

    private static final Logger LOG = LoggerFactory.getLogger(GrayImageLoader.class);

    private P pxType;

    public GrayImageLoader(String alignmentSpace, P pxType) {
        super(alignmentSpace);
        this.pxType = pxType;
    }

    @Override
    public RandomAccessibleInterval<P> loadImage(FileData fd) {
        if (StringUtils.endsWithIgnoreCase(fd.getName(), ".nrrd")) {
            return ImageReader.readNRRD(fd.getFileName(), pxType);
        } else {
            return loadGrayImage(fd);
        }
    }

    private RandomAccessibleInterval<P> loadGrayImage(FileData fd) {
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
            return ImageReader.readImageFromStream(inputStream, pxType);
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
