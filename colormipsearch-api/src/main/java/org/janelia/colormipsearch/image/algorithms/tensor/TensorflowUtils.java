package org.janelia.colormipsearch.image.algorithms.tensor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.EagerSession;
import org.tensorflow.TensorFlow;
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
}
