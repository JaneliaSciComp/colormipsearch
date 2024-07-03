package org.janelia.colormipsearch.model;

import org.apache.commons.lang3.StringUtils;

public enum ComputeFileType {
    // this is the source color depth MIP used that is search for a user mask when somebody performs a CDS in the workstation
    SourceColorDepthImage,
    // this is the image that will actually be used for CDS
    InputColorDepthImage,
    // gradient image type
    GradientImage,
    // RGB 20px Z-gap images
    ZGapImage,
    Vol3DSegmentation,
    SkeletonSWC,
    SkeletonOBJ;

    public static ComputeFileType fromName(String name) {
        for (ComputeFileType vt : values()) {
            if (StringUtils.equalsIgnoreCase(vt.name(), name)) {
                return vt;
            }
        }
        return null;
    }

}
