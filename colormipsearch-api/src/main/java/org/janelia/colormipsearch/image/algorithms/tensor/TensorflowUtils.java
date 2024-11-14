package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.Arrays;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.EagerSession;
import org.tensorflow.TensorFlow;
import org.tensorflow.ndarray.IntNdArray;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.ndarray.buffer.IntDataBuffer;
import org.tensorflow.proto.ConfigProto;
import org.tensorflow.proto.GPUOptions;

class TensorflowUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TensorflowUtils.class);

    static EagerSession createEagerSession() {
        LOG.info("Using tensorflow: {}", TensorFlow.version());

        // Create a ConfigProto object
        ConfigProto.Builder configBuilder = ConfigProto.newBuilder()
                .setLogDevicePlacement(true)
                .setAllowSoftPlacement(true);

        // Set GPU options if needed
        GPUOptions.Builder gpuOptionsBuilder = GPUOptions.newBuilder()
                .setAllowGrowth(false)
                .setForceGpuCompatible(false)
                .clearVisibleDeviceList();

        configBuilder.setGpuOptions(gpuOptionsBuilder);

        return EagerSession.options().async(true).config(configBuilder.build()).build();
    }

    static <T extends IntegerType<T>> void copyIntDataToSingleChannelImg(IntDataBuffer dataBuffer, RandomAccessibleInterval<T> target) {
        long startTime = System.currentTimeMillis();
        long dataBufferIndex = 0;
        Cursor<T> targetCursor = Views.flatIterable(target).cursor();
        while (targetCursor.hasNext()) {
            targetCursor.fwd();
            targetCursor.get().setInteger(dataBuffer.getInt(dataBufferIndex));
            dataBufferIndex++;
        }
        LOG.info("Copied data buffer to a single channel {} image in {} secs",
                Arrays.asList(target.dimensionsAsLongArray()),
                (System.currentTimeMillis() - startTime) / 1000.0);
    }

    static <T extends IntegerType<T>> void copyIntDataToSingleChannelImg(IntNdArray dataBuffer, RandomAccessibleInterval<T> target) {
        long startTime = System.currentTimeMillis();
        long dataBufferIndex = 0;
        Cursor<T> targetCursor = Views.flatIterable(target).cursor();
        while (targetCursor.hasNext()) {
            targetCursor.fwd();
            targetCursor.get().setInteger(dataBuffer.getInt(dataBufferIndex));
            dataBufferIndex++;
        }
        LOG.info("Copied data buffer to a single channel {} image in {} secs",
                Arrays.asList(target.dimensionsAsLongArray()),
                (System.currentTimeMillis() - startTime) / 1000.0);
    }

    /**
     * Copy the data from the data buffer to an RGB image.
     * @param dataBuffer - data buffer of shape 3 x img-dimensions
     * @param target
     * @param <T>
     */
    static <T extends RGBPixelType<T>> void copyIntDataToRGBImg(IntDataBuffer dataBuffer, RandomAccessibleInterval<T> target) {
        long startTime = System.currentTimeMillis();
        long targetSize = Views.iterable(target).size();
        long rDataBufferIndex = 0;
        long gDataBufferIndex = targetSize;
        long bDataBufferIndex = 2 * targetSize;
        Cursor<T> targetCursor = Views.flatIterable(target).cursor();
        while (targetCursor.hasNext()) {
            targetCursor.fwd();
            int r = dataBuffer.getInt(rDataBufferIndex);
            int g = dataBuffer.getInt(gDataBufferIndex);
            int b = dataBuffer.getInt(bDataBufferIndex);
            targetCursor.get().setFromRGB(r, g, b);
            rDataBufferIndex++; gDataBufferIndex++; bDataBufferIndex++;
        }
        LOG.info("Copied data buffer to an RGB {} image in {} secs",
                Arrays.asList(target.dimensionsAsLongArray()),
                (System.currentTimeMillis() - startTime) / 1000.0);
    }

    static <T extends IntegerType<T>> IntDataBuffer createIntDataFromSingleChannelImg(RandomAccessibleInterval<T> input) {
        long startTime = System.currentTimeMillis();
        long inputSize = Views.iterable(input).size();
        IntDataBuffer dataBuffer = DataBuffers.ofInts(inputSize);
        Cursor<T> inputCursor = Views.flatIterable(input).cursor();
        int inputIndex = 0;
        while (inputCursor.hasNext()) {
            inputCursor.fwd();
            T px = inputCursor.get();
            dataBuffer.setInt(px.getInteger(), inputIndex);
            inputIndex++;
        }
        LOG.info("Created data buffer from a single channel {} image in {} secs",
                Arrays.asList(input.dimensionsAsLongArray()),
                (System.currentTimeMillis() - startTime) / 1000.0);

        return dataBuffer;
    }

    static <T extends RGBPixelType<T>> IntDataBuffer createIntDataFromRGBImg(RandomAccessibleInterval<T> input) {
        long startTime = System.currentTimeMillis();
        long inputSize = Views.iterable(input).size();
        IntDataBuffer dataBuffer = DataBuffers.ofInts(3 * inputSize);
        Cursor<T> inputCursor = Views.flatIterable(input).cursor();
        long rInputIndex = 0;
        long gInputIndex = inputSize;
        long bInputIndex = 2 * inputSize;
        while (inputCursor.hasNext()) {
            inputCursor.fwd();
            T px = inputCursor.get();
            dataBuffer.setInt(px.getRed(), rInputIndex);
            dataBuffer.setInt(px.getGreen(), gInputIndex);
            dataBuffer.setInt(px.getBlue(), bInputIndex);
            rInputIndex++;
            gInputIndex++;
            bInputIndex++;
        }
        LOG.info("Created data buffer from an RGB {} image in {} secs",
                Arrays.asList(input.dimensionsAsLongArray()),
                (System.currentTimeMillis() - startTime) / 1000.0);

        return dataBuffer;
    }
}
