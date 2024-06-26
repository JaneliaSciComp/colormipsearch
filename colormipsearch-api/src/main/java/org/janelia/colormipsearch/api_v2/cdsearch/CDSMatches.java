package org.janelia.colormipsearch.api_v2.cdsearch;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.api_v2.Results;
import org.janelia.colormipsearch.api_v2.cdmips.MIPIdentifier;

@Deprecated
public class CDSMatches extends Results<List<ColorMIPSearchMatchMetadata>> {

    public static CDSMatches EMPTY = new CDSMatches(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Collections.emptyList());

    public static List<CDSMatches> fromResultsOfColorMIPSearchMatches(List<ColorMIPSearchMatchMetadata> listOfCDSMatches) {
        if (CollectionUtils.isNotEmpty(listOfCDSMatches)) {
            return listOfCDSMatches.stream()
                    .collect(Collectors.groupingBy(
                            csr -> new MIPIdentifier(
                                    csr.getSourceId(),
                                    csr.getSourcePublishedName(),
                                    csr.getSourceLibraryName(),
                                    csr.getSourceSampleRef(),
                                    csr.getSourceRelatedImageRefId(),
                                    csr.getSourceImagePath(),
                                    csr.getSourceCdmPath(),
                                    csr.getSourceImageURL(),
                                    csr.getSourceImageStack(),
                                    csr.getSourceScreenImage()
                                    ),
                            Collectors.toList()))
                    .entrySet().stream().map(e -> new CDSMatches(
                            e.getKey().getId(),
                            e.getKey().getPublishedName(),
                            e.getKey().getLibraryName(),
                            e.getKey().getSampleRef(),
                            e.getKey().getRelatedImageRefId(),
                            e.getKey().getImageURL(),
                            e.getKey().getImageStack(),
                            e.getKey().getScreenImage(),
                            e.getValue()))
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    public static CDSMatches singletonfromResultsOfColorMIPSearchMatches(List<ColorMIPSearchMatchMetadata> listOfCDSMatches) {
        List<CDSMatches> cdsMatches = fromResultsOfColorMIPSearchMatches(listOfCDSMatches);
        if (cdsMatches.isEmpty()) {
            throw new IllegalArgumentException("Expected a single set of color depth matches but found none");
        } else if (cdsMatches.size() > 1) {
            throw new IllegalArgumentException("Expected a single set of color depth matches but found " + cdsMatches.size());
        }
        return cdsMatches.get(0);
    }

    private final String maskId;
    private final String maskPublishedName;
    private final String maskLibraryName;
    private final String maskImageURL;
    private final String maskImageStack;
    private final String maskScreenImage;
    private final String maskSampleRef;
    private final String maskRelatedImageRefId;

    @JsonCreator
    public static CDSMatches createCDSMatches(@JsonProperty("maskId") String maskId,
                                              @JsonProperty("maskPublishedName") String maskPublishedName,
                                              @JsonProperty("maskLibraryName") String maskLibraryName,
                                              @JsonProperty("maskSampleRef") String maskSampleRef,
                                              @JsonProperty("maskRelatedImageRefId") String maskRelatedImageRefId,
                                              @JsonProperty("maskImageURL") String maskImageURL,
                                              @JsonProperty("maskImageStack") String maskImageStack,
                                              @JsonProperty("maskScreenImage") String maskScreenImage,
                                              @JsonProperty("results") List<ColorMIPSearchMatchMetadata> results) {
        if (StringUtils.isNotBlank(maskId)) {
            results.forEach(csr -> {
                csr.setSourceId(maskId);
                csr.setSourcePublishedName(maskPublishedName);
                csr.setSourceLibraryName(maskLibraryName);
                csr.setSourceImageURL(maskImageURL);
                csr.setSourceImageStack(maskImageStack);
                csr.setSourceScreenImage(maskScreenImage);
                csr.setSourceRelatedImageRefId(maskRelatedImageRefId);
                csr.setSourceSampleRef(maskSampleRef);
            });
        }
        return new CDSMatches(
                maskId,
                maskPublishedName,
                maskLibraryName,
                maskSampleRef,
                maskRelatedImageRefId,
                maskImageURL,
                maskImageStack,
                maskScreenImage,
                results);
    }

    CDSMatches(String maskId,
               String maskPublishedName,
               String maskLibraryName,
               String maskSampleRef,
               String maskRelatedImageRefId,
               String maskImageURL,
               String maskImageStack,
               String maskScreenImage,
               List<ColorMIPSearchMatchMetadata> results) {
        super(results);
        this.maskId = maskId;
        this.maskPublishedName = maskPublishedName;
        this.maskLibraryName = maskLibraryName;
        this.maskImageURL = maskImageURL;
        this.maskImageStack = maskImageStack;
        this.maskScreenImage = maskScreenImage;
        this.maskSampleRef = maskSampleRef;
        this.maskRelatedImageRefId = maskRelatedImageRefId;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return CollectionUtils.isEmpty(results);
    }

    @JsonProperty
    public String getMaskId() {
        return maskId;
    }

    @JsonProperty
    public String getMaskPublishedName() {
        return maskPublishedName;
    }

    @JsonProperty
    public String getMaskLibraryName() {
        return maskLibraryName;
    }

    @JsonProperty
    public String getMaskImageURL() {
        return maskImageURL;
    }

    @JsonProperty
    public String getMaskSampleRef() {
        return maskSampleRef;
    }

    @JsonProperty
    public String getMaskImageStack() {
        return maskImageStack;
    }

    @JsonProperty
    public String getMaskScreenImage() {
        return maskScreenImage;
    }

    @JsonProperty
    public String getMaskRelatedImageRefId() {
        return maskRelatedImageRefId;
    }
}
