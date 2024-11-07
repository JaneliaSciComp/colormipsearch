package org.janelia.colormipsearch.image.algorithms.tensor;

import java.nio.IntBuffer;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.convolutional.Conv3d;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TensorBasedMaxFilterAlgorithm {
    private static final Logger LOG = LoggerFactory.getLogger(TensorBasedMaxFilterAlgorithm.class);

    public static <T extends IntegerType<T>> Img<T> dilate(RandomAccessibleInterval<T> input,
                                                           int xRadius, int yRadius, int zRadius,
                                                           ImgFactory<T> factory) {
        long startTime = System.currentTimeMillis();
        Device device = Device.fromName("mps");
        try (NDManager manager = NDManager.newBaseManager(device)) {
            long[] shapeValues = {
                    input.dimension(2), input.dimension(1), input.dimension(0)
            };
            Shape inputShape = new Shape(1, 1, shapeValues[0]/*dim-z*/, shapeValues[1]/*dim-y*/, shapeValues[2]/*dim-x*/);
            Shape kernelShape = new Shape(1, 1, 2L * zRadius + 1, 2L * yRadius + 1, 2L * xRadius + 1);
            Shape paddedShape = new Shape(xRadius, xRadius, yRadius, yRadius, zRadius, zRadius);

            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(xRadius, yRadius, zRadius);
            NDArray ndArrayMask = manager.create(kernelMask.getKernelMask(), kernelShape).toType(DataType.FLOAT32, true);
            // Convert input to NDArray
            int[] inputArray = new int[(int) inputShape.size()];

            Cursor<T> inputCursor = Views.flatIterable(input).cursor();
            int index = 0;
            while (inputCursor.hasNext()) {
                inputCursor.fwd();
                inputArray[index++] = inputCursor.get().getInteger();
            }
            // Apply max filter using DJL
            NDArray ndArrayInput = manager.create(inputArray, inputShape).pad(paddedShape, 0).toType(DataType.FLOAT32, true);

            NDList ndConvOutput = Conv3d.conv3d(ndArrayInput, ndArrayMask);
            NDArray ndArrayOutput = ndConvOutput.singletonOrThrow();

            LOG.info("Dilation {}:{}:{} took {} secs", xRadius, yRadius,zRadius, (System.currentTimeMillis() - startTime) / 1000.0);
            // Convert output NDArray back to Img
            float[] outputArray = ndArrayOutput.toFloatArray();
            Img<T> output = factory.create(input);
            Cursor<T> outputCursor = output.cursor();
            int outputIndex = 0;
            while (outputCursor.hasNext()) {
                outputCursor.fwd();
                outputCursor.get().setReal(outputArray[outputIndex++]);
            }
            return output;
        }
    }

}
