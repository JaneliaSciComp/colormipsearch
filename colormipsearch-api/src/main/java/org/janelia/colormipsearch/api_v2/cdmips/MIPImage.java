package org.janelia.colormipsearch.api_v2.cdmips;

import java.io.Serializable;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.imageprocessing.ImageArray;

/**
 * @see org.janelia.colormipsearch.mips.NeuronMIP
 */
@Deprecated
public class MIPImage implements Serializable {
    private final MIPMetadata mipInfo;
    private final ImageArray<?> imageArray;

    public MIPImage(MIPMetadata mipInfo, ImageArray<?> imageArray) {
        this.mipInfo = mipInfo;
        this.imageArray = imageArray;
    }

    public MIPMetadata getMipInfo() {
        return mipInfo;
    }

    public ImageArray<?> getImageArray() {
        return imageArray;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("mipInfo", mipInfo)
                .toString();
    }
}
