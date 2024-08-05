package org.janelia.colormipsearch.image.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class MaxFilterAlgorithm {

    public static <T extends IntegerType<T>> Img<T> dilateMT(RandomAccessibleInterval<T> input,
                                                             int xRadius, int yRadius, int zRadius,
                                                             ImgFactory<T> factory,
                                                             ExecutorService executorService) {
        long width = input.dimension(0);
        long height = input.dimension(1);
        long depth = input.dimension(2);

        Img<T> output = factory.create(input);

        HyperEllipsoidMask kernel = new HyperEllipsoidMask(xRadius, yRadius, zRadius);

        int minz = (int) input.min(2);
        int miny = (int) input.min(1);
        int minx = (int) input.min(0);

        final AtomicInteger ai1 = new AtomicInteger(0);
        List<Callable<Void>> dilationTasks = new ArrayList<>();
        // create a dilation tasks for each slice
        for (int zi = 0; zi < depth; zi++) {
            int z = zi;
            dilationTasks.add(() -> {
                RandomAccess<T> inputRA = input.randomAccess(input);
                RandomAccess<T> outputRA = output.randomAccess();
                inputRA.setPosition(minz + z, 2);
                outputRA.setPosition(z, 2);
                for (int y = 0; y < height; y++) {
                    inputRA.setPosition(miny + y, 1);
                    outputRA.setPosition(y, 1);
                    for (int x = 0; x < width; x++) {
                        int maxIntensity = 0;
                        for (int rz = -zRadius; rz <= zRadius; rz++) {
                            if (z + rz >= 0 && z + rz < depth) {
                                inputRA.setPosition(minz + z + rz, 2);
                                for (int ry = -yRadius; ry <= yRadius; ry++) {
                                    if (y + ry >= 0 && y + ry < height) {
                                        inputRA.setPosition(miny + y + ry, 1);
                                        for (int rx = -xRadius; rx <= xRadius; rx++) {
                                            if (x + rx >= 0 && x + rx < width) {
                                                inputRA.setPosition(minx + x + rx, 0);
                                                if (kernel.contains(Math.abs(rx), Math.abs(ry), Math.abs(rz))) {
                                                    int val = inputRA.get().getInteger();
                                                    if (val > maxIntensity)
                                                        maxIntensity = val;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        outputRA.setPosition(x, 0);
                        outputRA.get().setInteger(maxIntensity);
                    }
                }
                return null;
            });
        }

        try {
            executorService.invokeAll(dilationTasks);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return output;
    }

    public static <T extends IntegerType<T>> Img<T> dilate(RandomAccessibleInterval<T> input,
                                                           int xRadius, int yRadius, int zRadius,
                                                           ImgFactory<T> factory) {
        long width = input.dimension(0);
        long height = input.dimension(1);
        long depth = input.dimension(2);

        Img<T> output = factory.create(input);

        HyperEllipsoidMask kernel = new HyperEllipsoidMask(xRadius, yRadius, zRadius);

        RandomAccess<T> inputRA = input.randomAccess(input);
        RandomAccess<T> outputRA = output.randomAccess();

        int minz = (int) input.min(2);
        int miny = (int) input.min(1);
        int minx = (int) input.min(0);
        for (int z = 0; z < depth; z++) {
            inputRA.setPosition(minz + z, 2);
            outputRA.setPosition(z, 2);
            for (int y = 0; y < height; y++) {
                inputRA.setPosition(miny + y, 1);
                outputRA.setPosition(y, 1);
                for (int x = 0; x < width; x++) {
                    int maxIntensity = 0;
                    for (int rz = -zRadius; rz <= zRadius; rz++) {
                        if (z + rz >= 0 && z + rz < depth) {
                            inputRA.setPosition(minz + z + rz, 2);
                            for (int ry = -yRadius; ry <= yRadius; ry++) {
                                if (y + ry >= 0 && y + ry < height) {
                                    inputRA.setPosition(miny + y + ry, 1);
                                    for (int rx = -xRadius; rx <= xRadius; rx++) {
                                        if (x + rx >= 0 && x + rx < width) {
                                            inputRA.setPosition(minx + x + rx, 0);
                                            if (kernel.contains(Math.abs(rx), Math.abs(ry), Math.abs(rz))) {
                                                int val = inputRA.get().getInteger();
                                                if (val > maxIntensity)
                                                    maxIntensity = val;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    outputRA.setPosition(x, 0);
                    outputRA.get().setInteger(maxIntensity);
                }
            }
        }

        return output;
    }

    public static <T extends IntegerType<T>> void maxFilterInX(RandomAccessibleInterval<T> input,
                                                               RandomAccessibleInterval<T> output,
                                                               int radius) {
        long width = input.dimension(0);
        long height = input.dimension(1);
        long depth = input.dimension(2);

        RandomAccess<T> outputRA = output.randomAccess();
        RandomAccess<T> inputRA = input.randomAccess();

        for (int z = 0; z < depth; z++) {
            if (input.numDimensions() > 2) {
                inputRA.setPosition(z, 2);
                outputRA.setPosition(z, 2);
            }
            for (int y = 0; y < height; y++) {
                inputRA.setPosition(y, 1);
                outputRA.setPosition(y, 1);
                for (int x = 0; x < width; x++) {
                    int maxIntensity = 0;
                    for (int r = -radius; r <= radius; r++) {
                        int xx = x + r;
                        if (xx >= 0 && xx < width) {
                            inputRA.setPosition(xx, 0);
                            int val = inputRA.get().getInteger();
                            if (val > maxIntensity) maxIntensity = val;
                        }
                    }
                    outputRA.setPosition(x, 0);
                    outputRA.get().setInteger(maxIntensity);
                }
            }
        }
    }

    public static <T extends IntegerType<T>> void maxFilterInY(RandomAccessibleInterval<T> input,
                                                               RandomAccessibleInterval<T> output,
                                                               int radius) {
        long width = input.dimension(0);
        long height = input.dimension(1);
        long depth = input.dimension(2);

        RandomAccess<T> outputRA = output.randomAccess();
        RandomAccess<T> inputRA = input.randomAccess();

        for (int z = 0; z < depth; z++) {
            if (input.numDimensions() > 2) {
                inputRA.setPosition(z, 2);
                outputRA.setPosition(z, 2);
            }
            for (int x = 0; x < width; x++) {
                inputRA.setPosition(x, 0);
                outputRA.setPosition(x, 0);
                for (int y = 0; y < height; y++) {
                    int maxIntensity = 0;
                    for (int r = -radius; r <= radius; r++) {
                        int yy = y + r;
                        if (yy >= 0 && yy < height) {
                            inputRA.setPosition(yy, 1);
                            int val = inputRA.get().getInteger();
                            if (val > maxIntensity) maxIntensity = val;
                        }
                    }
                    outputRA.setPosition(y, 1);
                    outputRA.get().setInteger(maxIntensity);
                }
            }
        }
    }

    public static <S extends RGBPixelType<S>, T extends RGBPixelType<T>> void rgbMaxFilterInX(RandomAccessibleInterval<S> input,
                                                                                              RandomAccessibleInterval<T> output,
                                                                                              int radius) {
        long width = input.dimension(0);
        long height = input.dimension(1);
        long depth = input.dimension(2);

        RandomAccess<S> inputRA = input.randomAccess();
        RandomAccess<T> outputRA = output.randomAccess();

        for (int z = 0; z < depth; z++) {
            if (input.numDimensions() > 2) {
                inputRA.setPosition(z, 2);
                outputRA.setPosition(z, 2);
            }
            for (int y = 0; y < height; y++) {
                inputRA.setPosition(y, 1);
                outputRA.setPosition(y, 1);
                for (int x = 0; x < width; x++) {
                    int maxIntensityR = 0;
                    int maxIntensityG = 0;
                    int maxIntensityB = 0;
                    for (int r = -radius; r <= radius; r++) {
                        int xx = x + r;
                        if (xx >= 0 && xx < width) {
                            inputRA.setPosition(xx, 0);
                            S spx = inputRA.get();
                            int rv = spx.getRed();
                            int gv = spx.getGreen();
                            int bv = spx.getBlue();
                            if (rv > maxIntensityR) maxIntensityR = rv;
                            if (gv > maxIntensityG) maxIntensityG = gv;
                            if (bv > maxIntensityB) maxIntensityB = bv;
                        }
                    }
                    outputRA.setPosition(x, 0);
                    T tpx = outputRA.get();
                    tpx.setFromRGB(maxIntensityR, maxIntensityG, maxIntensityB);
                }
            }
        }
    }

    public static <S extends RGBPixelType<S>, T extends RGBPixelType<T>> void rgbMaxFilterInY(RandomAccessibleInterval<S> input,
                                                                                              RandomAccessibleInterval<T> output,
                                                                                              int radius) {
        long width = input.dimension(0);
        long height = input.dimension(1);
        long depth = input.dimension(2);

        RandomAccess<S> inputRA = input.randomAccess();
        RandomAccess<T> outputRA = output.randomAccess();

        for (int z = 0; z < depth; z++) {
            if (input.numDimensions() > 2) {
                inputRA.setPosition(z, 2);
                outputRA.setPosition(z, 2);
            }
            for (int x = 0; x < width; x++) {
                inputRA.setPosition(x, 0);
                outputRA.setPosition(x, 0);
                for (int y = 0; y < height; y++) {
                    int maxIntensityR = 0;
                    int maxIntensityG = 0;
                    int maxIntensityB = 0;
                    for (int r = -radius; r <= radius; r++) {
                        int yy = y + r;
                        if (yy >= 0 && yy < height) {
                            inputRA.setPosition(yy, 1);
                            S spx = inputRA.get();
                            int rv = spx.getRed();
                            int gv = spx.getGreen();
                            int bv = spx.getBlue();
                            if (rv > maxIntensityR) maxIntensityR = rv;
                            if (gv > maxIntensityG) maxIntensityG = gv;
                            if (bv > maxIntensityB) maxIntensityB = bv;
                        }
                    }
                    outputRA.setPosition(y, 1);
                    T tpx = outputRA.get();
                    tpx.setFromRGB(maxIntensityR, maxIntensityG, maxIntensityB);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends RGBPixelType<T>> Img<T> rgbMaxFilterInXandY(RandomAccessibleInterval<? extends RGBPixelType<?>> input,
                                                                         int radius,
                                                                         int threshold) {
        RandomAccessibleInterval<T> maskedInput = ImageTransforms.maskRGBPixelsBelowThreshold(
                (RandomAccessibleInterval<T>) input,
                threshold
        );
        T inputPxType = (T) input.randomAccess().get().createVariable();
        Img<T> temp = new ArrayImgFactory<>(inputPxType).create(input);
        Img<T> output = new ArrayImgFactory<>(inputPxType).create(input);

        rgbMaxFilterInX(maskedInput, temp, radius);
        rgbMaxFilterInY(temp, output, radius);

        return output;
    }

}
