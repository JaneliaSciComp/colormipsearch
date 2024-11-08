package org.janelia.colormipsearch.image.algorithms.tensor;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.convolutional.Conv2d;
import ai.djl.nn.convolutional.Conv3d;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.HyperEllipsoidMask;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TensorBasedMaxFilterAlgorithm {
    private static final Logger LOG = LoggerFactory.getLogger(TensorBasedMaxFilterAlgorithm.class);

    public static <T extends RGBPixelType<T>> Img<T> dilate2D(RandomAccessibleInterval<T> input,
                                                              int xRadius, int yRadius,
                                                              ImgFactory<T> factory,
                                                              String deviceName) {
        long startTime = System.currentTimeMillis();
        Device device = Device.fromName(deviceName);
        try (NDManager manager = NDManager.newBaseManager(device)) {
            long[] shapeValues = {
                    input.dimension(1), input.dimension(0)
            };
            Shape inputShape = new Shape(1, 3, shapeValues[0]/*dim-y*/, shapeValues[1]/*dim-x*/);
            Shape kernelShape = new Shape(3, 3, 2L * yRadius + 1, 2L * xRadius + 1);
            Shape paddedShape = new Shape(xRadius, xRadius, yRadius, yRadius);

            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(xRadius, yRadius);
            NDArray ndArrayMask = manager.create(kernelMask.getKernelMask(3, 3), kernelShape).toType(DataType.INT32, false);
            // Convert input to NDArray
            int[] inputArray = new int[(int) inputShape.size()];

            Cursor<T> inputCursor = Views.flatIterable(input).cursor();
            int index = 0;
            while (inputCursor.hasNext()) {
                inputCursor.fwd();
                T px = inputCursor.get();
                inputArray[index] = px.getRed();
                inputArray[index + (int) shapeValues[0] * (int) shapeValues[1]] = px.getGreen();
                inputArray[index + 2 * (int) shapeValues[0] * (int) shapeValues[1]] = px.getBlue();
                index++;
            }
            // Apply max filter using DJL
            NDArray ndArrayInput = manager.create(inputArray, inputShape).pad(paddedShape, 0).toType(DataType.INT32, false);
            NDList ndConvOutput = Conv2d.conv2d(ndArrayInput, ndArrayMask);
            NDArray ndArrayOutput = ndConvOutput.singletonOrThrow();

            LOG.info("Dilation {}:{} took {} secs", xRadius, yRadius, (System.currentTimeMillis() - startTime) / 1000.0);
            // Convert output NDArray back to Img
            int[] outputArray = ndArrayOutput.toIntArray();
            Img<T> output = factory.create(input);
            Cursor<T> outputCursor = output.cursor();
            int outputIndex = 0;
            while (outputCursor.hasNext()) {
                outputCursor.fwd();
                int r = outputArray[outputIndex];
                int g = outputArray[outputIndex + (int)shapeValues[0] * (int)shapeValues[1]];
                int b = outputArray[outputIndex + 2 * (int)shapeValues[0] * (int)shapeValues[1]];
                outputCursor.get().setFromRGB(r, g, b);
                outputIndex += 1;
            }
            return output;
        }
    }

    public static <T extends IntegerType<T>> Img<T> dilate3D(RandomAccessibleInterval<T> input,
                                                             int xRadius, int yRadius, int zRadius,
                                                             ImgFactory<T> factory,
                                                             String deviceName) {
        long startTime = System.currentTimeMillis();
        Device device = Device.fromName(deviceName);
        try (NDManager manager = NDManager.newBaseManager(device)) {
            long[] shapeValues = {
                    input.dimension(2), input.dimension(1), input.dimension(0)
            };
            Shape inputShape = new Shape(1, 1, shapeValues[0]/*dim-z*/, shapeValues[1]/*dim-y*/, shapeValues[2]/*dim-x*/);
            Shape kernelShape = new Shape(1, 1, 2L * zRadius + 1, 2L * yRadius + 1, 2L * xRadius + 1);
            Shape paddedShape = new Shape(xRadius, xRadius, yRadius, yRadius, zRadius, zRadius);

            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(xRadius, yRadius, zRadius);
            NDArray ndArrayMask = manager.create(kernelMask.getKernelMask(1, 1), kernelShape).toType(DataType.FLOAT32, true);
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

            LOG.info("Dilation {}:{}:{} took {} secs", xRadius, yRadius, zRadius, (System.currentTimeMillis() - startTime) / 1000.0);
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
