package org.janelia.colormipsearch.mips;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Native;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.io.ImageReader;
import org.janelia.colormipsearch.model.FileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SWCImageLoader<P extends IntegerType<P> & NativeType<P>> extends AbstractImageLoader<P> {

    private static final Logger LOG = LoggerFactory.getLogger(SWCImageLoader.class);

    private final P foregroundPx;
    private final double scale;
    private final double radius;

    public SWCImageLoader(String alignmentSpace, double scale, double radius, P foregroundPx) {
        super(alignmentSpace);
        this.scale = scale;
        this.radius = radius;
        this.foregroundPx = foregroundPx;
    }

    @Override
    public RandomAccessibleInterval<P> loadImage(FileData fd) {
        return loadSWC(fd);
    }

    private RandomAccessibleInterval<P> loadSWC(FileData fd) {
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
            int[] dims = getExpectedSize();
            double[] voxelSpacing = getVoxelSpacing();
            return ImageReader.readSWCStream(
                    inputStream,
                    (int)(dims[0] * scale), (int)(dims[1] * scale), (int)(dims[2] * scale),
                    voxelSpacing[0] / scale, voxelSpacing[1] / scale, voxelSpacing[2] / scale,
                    radius,
                    foregroundPx);
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
