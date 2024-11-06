package org.janelia.colormipsearch.image.algorithms.tensor;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.janelia.colormipsearch.image.RectIntervalHelper;

public class TensorBasedMaxFilterAlgorithm {
    public static <T extends IntegerType<T>> Img<T> dilate(RandomAccessibleInterval<T> input,
                                                           int xRadius, int yRadius, int zRadius,
                                                           ImgFactory<T> factory) {
        Device device = Device.fromName("cpu");
        try (NDManager manager = NDManager.newBaseManager(device)) {
            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(zRadius, yRadius, xRadius);
            NDArray maskArray = manager.create(kernelMask.getKernelMask(), new Shape(2 * zRadius + 1, 2 * yRadius + 1, 2 * xRadius + 1)).toDevice(device, true);
            // Convert input to NDArray
            long[] shapeValues = {
                    input.dimension(2), input.dimension(1), input.dimension(0)
            };
            RectIntervalHelper inputIntervalHelper = new RectIntervalHelper(shapeValues);
            int[] inputArray = new int[(int) inputIntervalHelper.getSize()];

            Cursor<T> inputCursor = Views.flatIterable(input).cursor();
            Shape shape = new Shape(shapeValues);
            int index = 0;
            while (inputCursor.hasNext()) {
                inputCursor.fwd();
                inputArray[index++] = inputCursor.get().getInteger();
            }
            // Apply max filter using DJL
            NDArray ndArrayInput = manager.create(inputArray, shape).toDevice(device, true);
            NDArray ndArrayOutput = ndArrayInput.duplicate();

            for (int z = 0; z < shapeValues[0]; z++) {
                for (int y = 0; y < shapeValues[1]; y++) {
                    for (int x = 0; x < shapeValues[2]; x++) {
                        NDIndex neighborhoodSlice = new NDIndex()
                                .addSliceDim(Math.max(0, z - zRadius), Math.min(z + zRadius + 1, shapeValues[0]))
                                .addSliceDim(Math.max(0, y - yRadius), Math.min(y + yRadius + 1, shapeValues[1]))
                                .addSliceDim(Math.max(0, x - xRadius), Math.min(x + xRadius + 1, shapeValues[2]));
                        NDIndex maskSlice = new NDIndex()
                                .addSliceDim(z >= zRadius ? 0 : zRadius - z, z + zRadius < shapeValues[0] ? 2 * zRadius + 1 : zRadius + (shapeValues[0] - z))
                                .addSliceDim(y >= yRadius ? 0 : yRadius - y, y + yRadius < shapeValues[1] ? 2 * yRadius + 1 : yRadius + (shapeValues[1] - y))
                                .addSliceDim(x >= xRadius ? 0 : xRadius - x, x + xRadius < shapeValues[2] ? 2 * xRadius + 1 : xRadius + (shapeValues[2] - x));
                        NDArray neighborhood = ndArrayInput.get(neighborhoodSlice);
                        NDArray mask = maskArray.get(maskSlice);
                        NDArray maxNeighborhood = neighborhood.mul(mask);
                        ndArrayOutput.set(new NDIndex(z, y, x), maxNeighborhood.max().getInt());
                    }
                }
            }

            // Convert output NDArray back to Img
            Img<T> output = factory.create(input);
            RandomAccess<T> outputRA = output.randomAccess();
            for (int z = 0; z < shapeValues[0]; z++) {
                for (int y = 0; y < shapeValues[1]; y++) {
                    for (int x = 0; x < shapeValues[2]; x++) {
                        outputRA.setPosition(new int[]{x, y, z});
                        outputRA.get().setInteger(ndArrayOutput.getInt(z, y, x));
                    }
                }
            }
            return output;
        }
    }

}
