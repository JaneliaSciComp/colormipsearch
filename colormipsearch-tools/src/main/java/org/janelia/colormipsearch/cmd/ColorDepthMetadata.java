package org.janelia.colormipsearch.cmd;

import java.nio.file.Paths;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.tools.MIPInfo;
import org.janelia.colormipsearch.tools.MetadataAttrs;

class ColorDepthMetadata extends MetadataAttrs {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty
    String sourceImageRef;
    String filepath;
    String type;
    String segmentedDataBasePath;
    String segmentFilepath;

    @JsonIgnore
    void setEMSkeletonPublishedName(String publishedName) {
        this.setPublishedName(publishedName);
        addAttr("Body Id", publishedName);
    }

    @JsonIgnore
    void setLMLinePublishedName(String publishedName) {
        this.setPublishedName(publishedName);
        addAttr("Published Name", publishedName);
    }

    @JsonIgnore
    String getCdmName() {
        if (StringUtils.isNotBlank(filepath)) {
            return Paths.get(filepath).getFileName().toString();
        } else {
            return null;
        }
    }

    void copyTo(ColorDepthMetadata that) {
        super.copyTo(that);
        that.filepath = this.filepath;
        that.segmentedDataBasePath = this.segmentedDataBasePath;
        that.segmentFilepath = this.segmentFilepath;
    }

    MIPInfo asMIPInfo() {
        MIPInfo mipInfo = new MIPInfo();
        mipInfo.setId(getId());
        mipInfo.setLibraryName(getLibraryName());
        mipInfo.setPublishedName(getPublishedName());
        mipInfo.setType(type);
        mipInfo.setArchivePath(segmentedDataBasePath);
        mipInfo.setImagePath(StringUtils.defaultIfBlank(segmentFilepath, filepath));
        mipInfo.setCdmPath(filepath);
        mipInfo.setImageURL(getImageUrl());
        mipInfo.setThumbnailURL(getThumbnailUrl());
        mipInfo.setRelatedImageRefId(extractIdFromRef(sourceImageRef));
        iterateAttrs((k, v) -> mipInfo.addAttr(k, v));
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
