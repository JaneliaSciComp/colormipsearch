package org.janelia.colormipsearch.image.algorithms;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.colormipsearch.image.HyperEllipsoidRegion;

public class MaxFilterAlgorithm {

    public static <T extends IntegerType<T>> Img<T> dilate(RandomAccessibleInterval<T> input,
                                                           int xRadius, int yRadius, int zRadius,
                                                           ImgFactory<T> factory) {
        long width = input.dimension(0);
        long height = input.dimension(1);
        long depth = input.dimension(2);

        Img<T> output = factory.create(input);

        RandomAccess<T> inputRA = input.randomAccess(input);
        RandomAccess<T> outputRA = output.randomAccess();
        HyperEllipsoidRegion kernel = new HyperEllipsoidRegion(xRadius, yRadius, zRadius);

        int minz = (int) input.min(2);
        int miny = (int) input.min(1);
        int minx = (int) input.min(0);
        for (int z = 0; z < depth; z++) {
            inputRA.setPosition(minz+z, 2);
            outputRA.setPosition(z, 2);
            for (int y = 0; y < height; y++) {
                inputRA.setPosition(miny+y, 1);
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

    public static <T extends IntegerType<T>> void applyX(RandomAccessibleInterval<T> input,
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

    public static <T extends IntegerType<T>> void applyY(RandomAccessibleInterval<T> input,
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

    public static <T extends IntegerType<T>> void applyZ(RandomAccessibleInterval<T> input,
                                                         RandomAccessibleInterval<T> output,
                                                         int radius) {
        long width = input.dimension(0);
        long height = input.dimension(1);
        long depth = input.dimension(2);

        RandomAccess<T> outputRA = output.randomAccess();
        RandomAccess<T> inputRA = input.randomAccess();

        for (int x = 0; x < width; x++) {
            inputRA.setPosition(x, 0);
            outputRA.setPosition(x, 0);
            for (int y = 0; y < height; y++) {
                inputRA.setPosition(y, 1);
                outputRA.setPosition(y, 1);
                for (int z = 0; z < depth; z++) {
                    int maxIntensity = 0;
                    for (int r = -radius; r <= radius; r++) {
                        int zz = z + r;
                        if (zz >= 0 && zz < depth) {
                            inputRA.setPosition(zz, 2);
                            int val = inputRA.get().getInteger();
                            if (val > maxIntensity) maxIntensity = val;
                        }
                    }
                    outputRA.setPosition(z, 2);
                    outputRA.get().setInteger(maxIntensity);
                }
            }
        }
    }

}
