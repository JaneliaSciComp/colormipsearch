package org.janelia.colormipsearch.image.algorithms.tensor;

import java.util.Arrays;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.PixelOps;
import org.janelia.colormipsearch.image.type.RGBPixelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.EagerSession;
import org.tensorflow.TensorFlow;
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
                .setLogDevicePlacement(LOG.isDebugEnabled())
                .setAllowSoftPlacement(true);

        // Set GPU options if needed
        GPUOptions.Builder gpuOptionsBuilder = GPUOptions.newBuilder()
                .setAllowGrowth(false)
                .setForceGpuCompatible(false)
                .clearVisibleDeviceList();

        configBuilder.setGpuOptions(gpuOptionsBuilder);

        return EagerSession.options().async(true).config(configBuilder.build()).build();
    }

    /**
     * Copy the data from the data buffer containing gray pixel values to a single channel (gray) image.
     * @param dataBuffer
     * @param target
     * @param <T>
     */
    static <T extends IntegerType<T>> void copyPixelIntDataToGrayImg(IntDataBuffer dataBuffer, RandomAccessibleInterval<T> target) {
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
     * Copy the data from 3 channel data buffer to an RGB image.
     * @param dataBuffer - data buffer of shape 3 x [img-dimensions]
     * @param target
     * @param <T>
     */
    static <T extends RGBPixelType<T>> void copyRGBIntDataToRGBImg(IntDataBuffer dataBuffer, RandomAccessibleInterval<T> target) {
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

    /**
     * Create a data buffer with pixel values from a single channel image.
     * @param input
     * @return
     * @param <T>
     */
    static <T extends IntegerType<T>> IntDataBuffer createGrayIntDataFromGrayImg(RandomAccessibleInterval<T> input) {
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

    /**
     * Create a data buffer with gray pixel intensity values from an RGB image.
     * @param input
     * @return
     * @param <T>
     */
    static <T extends RGBPixelType<T>> IntDataBuffer createGrayIntDataFromRGBImg(RandomAccessibleInterval<T> input) {
        long startTime = System.currentTimeMillis();
        long inputSize = Views.iterable(input).size();
        IntDataBuffer dataBuffer = DataBuffers.ofInts(inputSize);
        Cursor<T> inputCursor = Views.flatIterable(input).cursor();
        long inputIndex = 0;
        while (inputCursor.hasNext()) {
            inputCursor.fwd();
            T px = inputCursor.get();
            dataBuffer.setInt(PixelOps.rgbToGrayNoGammaCorrection(px.getRed(), px.getGreen(), px.getBlue(), 255), inputIndex++);
        }
        LOG.info("Created intensity data buffer from an RGB {} image in {} secs",
                Arrays.asList(input.dimensionsAsLongArray()),
                (System.currentTimeMillis() - startTime) / 1000.0);
        return dataBuffer;
    }

    /**
     * Create a data buffer with pixel values from an RGB image. The shape of the output is C x [img-shape].
     * @param input
     * @return
     * @param <T>
     */
    static <T extends RGBPixelType<T>> IntDataBuffer createRGBIntDataFromRGBImg(RandomAccessibleInterval<T> input) {
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
