package org.janelia.colormipsearch.image.algorithms.tensor;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDArrays;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.convolutional.Conv3d;
import ai.djl.nn.pooling.Pool;
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

public class DJLMaxFilterAlgorithm {
    private static final Logger LOG = LoggerFactory.getLogger(DJLMaxFilterAlgorithm.class);

    public static <T extends RGBPixelType<T>> Img<T> maxFilter2DWithEllipticalKernel(RandomAccessibleInterval<T> input,
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
            Shape kernelShape = new Shape(2L * yRadius + 1, 2L * xRadius + 1);

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
            Shape borders = new Shape(xRadius, xRadius, yRadius, yRadius);
            // Apply max filter using DJL
            NDArray ndArrayInput = manager.create(inputArray, inputShape)
                    .pad(borders, 0)
                    .toType(DataType.FLOAT32, false);
            NDList patches = new NDList();
            for (int c = 0; c < 3; c++) {
                for (int h = yRadius; h < ndArrayInput.getShape().get(2) - yRadius; h++) {
                    for (int w = xRadius; w < ndArrayInput.getShape().get(3) - xRadius; w++) {
                        NDIndex patchIndex = new NDIndex()
                                .addSliceDim(0, 1)
                                .addSliceDim(c, c + 1)
                                .addSliceDim(h - yRadius, h + yRadius + 1)
                                .addSliceDim(w - xRadius, w + xRadius + 1);
                        NDArray patch = ndArrayInput.get(patchIndex);
                        patches.add(patch);
                    }
                }
            }
            LOG.info("Unfolding {} patches took {} secs", patches.size(), (System.currentTimeMillis() - startTime) / 1000.0);
            NDArray stackedPatches = NDArrays.stack(patches, 0)
                    .reshape(patches.size(), 1, kernelShape.get(0), kernelShape.get(1))
                    .toType(DataType.FLOAT32, true);
            HyperEllipsoidMask kernelMask = new HyperEllipsoidMask(yRadius, xRadius);
            NDArray ndArrayMask = manager.create(kernelMask.getKernelMask(), kernelShape)
                    .toType(DataType.FLOAT32, true);
            NDArray maskedStackedPatches = stackedPatches.mul(ndArrayMask);

            NDArray ndArrayOutput = Pool.maxPool2d(maskedStackedPatches, kernelShape, kernelShape, new Shape(0, 0), false)
                    .reshape(3, shapeValues[0], shapeValues[1]);

            LOG.info("Dilation {}:{} took {} secs", xRadius, yRadius, (System.currentTimeMillis() - startTime) / 1000.0);
            // Convert output NDArray back to Img
            float[] outputArray = ndArrayOutput.toFloatArray();
            Img<T> output = factory.create(input);
            Cursor<T> outputCursor = output.cursor();
            int outputIndex = 0;
            while (outputCursor.hasNext()) {
                outputCursor.fwd();
                int r = (int) outputArray[outputIndex];
                int g = (int) outputArray[outputIndex + (int) shapeValues[0] * (int) shapeValues[1]];
                int b = (int) outputArray[outputIndex + 2 * (int) shapeValues[0] * (int) shapeValues[1]];
                outputCursor.get().setFromRGB(r, g, b);
                outputIndex += 1;
            }
            return output;
        }
    }

    public static <T extends IntegerType<T>> Img<T> maxFilter3DWithEllipsoidKernel(RandomAccessibleInterval<T> input,
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
            Shape paddingShape = new Shape(zRadius, yRadius, xRadius);

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
            NDArray ndArrayInput = manager.create(inputArray, inputShape).toType(DataType.FLOAT32, true);

            NDList ndConvOutput = Conv3d.conv3d(ndArrayInput, ndArrayMask, null, new Shape(1, 1, 1), paddingShape, new Shape(1, 1, 1));

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