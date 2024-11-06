package org.janelia.colormipsearch.image.algorithms.tensor;

import ai.djl.Device;
import ai.djl.engine.Engine;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.pytorch.jni.JniUtils;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.HyperEllipsoidMask;

public class TensorBasedMaxFilterAlgorithm {
    public static <T extends IntegerType<T>> Img<T> dilate(RandomAccessibleInterval<T> input,
                                                           int xRadius, int yRadius, int zRadius,
                                                           ImgFactory<T> factory) {
        Device device = Device.fromName("mps");
        try (NDManager manager = NDManager.newBaseManager(device)) {
            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(zRadius, yRadius, xRadius);
            NDArray maskArray = manager.create(kernelMask.getKernelMask(), new Shape(2 * zRadius + 1, 2 * yRadius + 1, 2 * xRadius + 1)).toDevice(device, false);
            // Convert input to NDArray
            long[] shape = {
                    input.dimension(2), input.dimension(1), input.dimension(0)
            };
            NDArray ndArrayFromInput = manager.create(new Shape(shape), DataType.INT32);
            Cursor<T> inputCursor = Views.flatIterable(input).localizingCursor();
            long[] pos = {
                    0, 0, 0
            };
            long[] min = input.minAsLongArray();
            while (inputCursor.hasNext()) {
                inputCursor.fwd();
                inputCursor.localize(pos);
                ndArrayFromInput.set(
                        new NDIndex(pos[2] - min[2], pos[1] - min[1], pos[0] - min[0]),
                        inputCursor.get().getInteger()
                );
            }
            NDArray workingArray = ndArrayFromInput.toDevice(device, false);
            // Apply max filter using DJL
            NDArray outputArray = workingArray.duplicate();

            for (int z = 0; z < shape[0]; z++) {
                for (int y = 0; y < shape[1]; y++) {
                    for (int x = 0; x < shape[2]; x++) {
                        NDIndex neighborhoodSlice = new NDIndex()
                                .addSliceDim(Math.max(0, z - zRadius), Math.min(z + zRadius + 1, shape[0]))
                                .addSliceDim(Math.max(0, y - yRadius), Math.min(y + yRadius + 1, shape[1]))
                                .addSliceDim(Math.max(0, x - xRadius), Math.min(x + xRadius + 1, shape[2]));
                        NDIndex maskSlice = new NDIndex()
                                .addSliceDim(z >= zRadius ? 0 : zRadius - z, z + zRadius < shape[0] ? 2 * zRadius + 1 : zRadius + (shape[0] - z) )
                                .addSliceDim(y >= yRadius ? 0 : yRadius - y, y + yRadius < shape[1] ? 2 * yRadius + 1 : yRadius + (shape[1] - y))
                                .addSliceDim(x >= xRadius ? 0 : xRadius - x, x + xRadius < shape[2] ? 2 * xRadius + 1 : xRadius + (shape[2] - x))
                                ;
                        NDArray neighborhood = workingArray.get(neighborhoodSlice);
                        NDArray mask = maskArray.get(maskSlice);
                        NDArray maxNeighborhood = neighborhood.mul(mask);
                        outputArray.set(new NDIndex(z, y, x), maxNeighborhood.max().getInt());
                    }
                }
            }


            // Convert output NDArray back to Img
            Img<T> output = factory.create(input);
            RandomAccess<T> outputRA = output.randomAccess();
            for (int z = 0; z < shape[0]; z++) {
                for (int y = 0; y < shape[1]; y++) {
                    for (int x = 0; x < shape[2]; x++) {
                        outputRA.setPosition(new int[]{x, y, z});
                        outputRA.get().setInteger(outputArray.getInt(z, y, x));
                    }
                }
            }
            return output;
        }
    }

}
