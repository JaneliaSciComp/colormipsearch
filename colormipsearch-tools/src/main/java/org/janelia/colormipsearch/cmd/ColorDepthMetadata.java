package org.janelia.colormipsearch.cmd;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.api.cdmips.AbstractMetadata;
import org.janelia.colormipsearch.api.cdmips.MIPMetadata;

class ColorDepthMetadata extends AbstractMetadata {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty
    String sourceImageRef;
    String filepath;
    String segmentedDataBasePath;
    String segmentFilepath;
    String sampleRef;

    @JsonIgnore
    void setEMSkeletonPublishedName(String publishedName) {
        this.setPublishedName(publishedName);
    }

    @JsonIgnore
    void setLMLinePublishedName(String publishedName) {
        this.setPublishedName(publishedName);
    }

    void copyTo(ColorDepthMetadata that) {
        super.copyTo(that);
        that.filepath = this.filepath;
        that.segmentedDataBasePath = this.segmentedDataBasePath;
        that.segmentFilepath = this.segmentFilepath;
        that.sampleRef = this.sampleRef;
    }

    MIPMetadata asMIPWithVariants() {
        MIPMetadata mipInfo = new MIPMetadata();
        this.copyTo(mipInfo);
        mipInfo.setImageType(this.getImageType());
        mipInfo.setImageArchivePath(segmentedDataBasePath);
        mipInfo.setImageName(StringUtils.defaultIfBlank(segmentFilepath, filepath));
        mipInfo.setCdmPath(filepath);
        mipInfo.setImageURL(getImageURL());
        mipInfo.setThumbnailURL(getThumbnailURL());
        mipInfo.setRelatedImageRefId(extractIdFromRef(sourceImageRef));
        mipInfo.setSampleRef(sampleRef);
        return mipInfo;
    }

    private String extractIdFromRef(String ref) {
        if (StringUtils.isBlank(ref)) {
            return null;
        } else {
            int idseparator = ref.indexOf('#');
            if (idseparator == -1) {
                return null; // not a valid stringified reference
            } else {
                return StringUtils.defaultIfBlank(ref.substring(idseparator + 1), null);
            }
        }
    }
}
