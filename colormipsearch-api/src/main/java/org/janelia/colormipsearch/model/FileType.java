package org.janelia.colormipsearch.model;

import org.apache.commons.lang3.StringUtils;

public enum FileType {
    ColorDepthMip("_5_ch.png"), // The CDM of the image. For PPPM, this is the best matching channel of the matching LM stack and called 'Best Channel CDM' in the NeuronBridge GUI.
    ColorDepthMipThumbnail, // The thumbnail sized version of the ColorDepthMip, if available.
    ColorDepthMipInput, // CDM-only. The actual color depth image that was input. 'Matched CDM' in the NeuronBridge GUI.
    ColorDepthMipMatch, // CDM-only. The actual color depth image that was matched. 'Matched CDM' in the NeuronBridge GUI.
    ColorDepthMipSkel("_6_ch_skel.png"), // PPPM-only. The CDM of the best matching channel with the matching LM segmentation fragments overlaid. 'LM - Best Channel CDM with EM overlay' in the NeuronBridge GUI.
    SignalMip("_1_raw.png"), // PPPM-only. The full MIP of all channels of the matching sample. 'LM - Sample All-Channel MIP' in the NeuronBridge GUI.
    SignalMipMasked("_2_masked_raw.png"), // PPPM-only. LM signal content masked with the matching LM segmentation fragments. 'PPPM Mask' in the NeuronBridge GUI.
    SignalMipMaskedSkel("_3_skel.png"), // PPPM-only. LM signal content masked with the matching LM segmentation fragments, overlaid with the EM skeleton. 'PPPM Mask with EM Overlay' in the NeuronBridge GUI.
    SignalMipExpression, // MCFO-only. A representative CDM image of the full expression of the line.
    VisuallyLosslessStack, // LMImage-only. An H5J 3D image stack of all channels of the LM image.
    AlignedBodySWC, // EMImage-only, A 3D SWC skeleton of the EM body in the alignment space.
    AlignedBodyOBJ; // EMImage-only. A 3D OBJ representation of the EM body in the alignment space.

    private String optionalFileSuffix; // optional suffix used only by the PPP image files

    FileType() {
        this(null);
    }

    FileType(String optionalFileSuffix) {
        this.optionalFileSuffix = optionalFileSuffix;
    }

    public static FileType fromName(String name) {
        for (FileType vt : values()) {
            if (StringUtils.equalsIgnoreCase(vt.name(), name)) {
                return vt;
            }
        }
        return null;
    }

    public static FileType findFileTypeByPPPSuffix(String fname) {
        for (FileType vt : values()) {
            if (vt.optionalFileSuffix == null) {
                // skip
                continue;
            } else if (fname.endsWith(vt.optionalFileSuffix)) {
                return vt;
            }
        }
        return null;
    }

    public boolean hasFileSuffix() {
        return optionalFileSuffix != null;
    }

    public String getFileSuffix() {
        return optionalFileSuffix;
    }

    public String getDisplayPPPSuffix() {
        return optionalFileSuffix == null ? "" : optionalFileSuffix.substring(3); // hacky to remove the prefix _n_ for PPP
    }
}