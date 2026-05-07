package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.Gray16ImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Color Depth MIP (CDM) from a 3D volume.
 * Projects along the Z-axis using maximum intensity projection and maps Z-depth
 * to RGB color using the "psychedelic rainbow 2" look-up table with
 * dominant-channel color compositing.
 */
public class CDMGenerationAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(CDMGenerationAlgorithm.class);

    private enum MIPTWO {
        NONE, RB2, RG2, GB2, GR2, BR2, BG2
    }

    private static final int[] PSYCHEDELIC_RAINBOW_2 = {
            127, 0, 255, 125, 3, 255, 124, 6, 255, 122, 9, 255, 121, 12, 255, 120, 15, 255,
            119, 18, 255, 118, 21, 255, 116, 24, 255, 115, 27, 255, 114, 30, 255, 113, 33, 255,
            112, 36, 255, 110, 39, 255, 109, 42, 255, 108, 45, 255, 106, 48, 255, 105, 51, 255,
            104, 54, 255, 103, 57, 255, 101, 60, 255, 100, 63, 255, 99, 66, 255, 98, 69, 255,
            96, 72, 255, 95, 75, 255, 94, 78, 255, 93, 81, 255, 92, 84, 255, 90, 87, 255,
            89, 90, 255, 87, 93, 255, 86, 96, 255, 84, 99, 255, 83, 102, 255, 81, 105, 255,
            80, 108, 255, 78, 111, 255, 77, 114, 255, 75, 117, 255, 74, 120, 255, 72, 123, 255,
            71, 126, 255, 69, 129, 255, 68, 132, 255, 66, 135, 255, 65, 138, 255, 63, 141, 255,
            62, 144, 255, 60, 147, 255, 59, 150, 255, 57, 153, 255, 56, 156, 255, 54, 159, 255,
            53, 162, 255, 51, 165, 255, 50, 168, 255, 48, 171, 255, 47, 174, 255, 45, 177, 255,
            44, 180, 255, 42, 183, 255, 41, 186, 255, 39, 189, 255, 38, 192, 255, 36, 195, 255,
            35, 198, 255, 33, 201, 255, 32, 204, 255, 30, 207, 255, 29, 210, 255, 27, 213, 255,
            26, 216, 255, 24, 219, 255, 23, 222, 255, 21, 225, 255, 20, 228, 255, 18, 231, 255,
            16, 234, 255, 14, 237, 255, 12, 240, 255, 9, 243, 255, 6, 246, 255, 3, 249, 255,
            1, 252, 255, 0, 254, 255, 3, 255, 252, 6, 255, 249, 9, 255, 246, 12, 255, 243,
            15, 255, 240, 18, 255, 237, 21, 255, 234, 24, 255, 231, 27, 255, 228, 30, 255, 225,
            33, 255, 222, 36, 255, 219, 39, 255, 216, 42, 255, 213, 45, 255, 210, 48, 255, 207,
            51, 255, 204, 54, 255, 201, 57, 255, 198, 60, 255, 195, 63, 255, 192, 66, 255, 189,
            69, 255, 186, 72, 255, 183, 75, 255, 180, 78, 255, 177, 81, 255, 174, 84, 255, 171,
            87, 255, 168, 90, 255, 165, 93, 255, 162, 96, 255, 159, 99, 255, 156, 102, 255, 153,
            105, 255, 150, 108, 255, 147, 111, 255, 144, 114, 255, 141, 117, 255, 138, 120, 255, 135,
            123, 255, 132, 126, 255, 129, 129, 255, 126, 132, 255, 123, 135, 255, 120, 138, 255, 117,
            141, 255, 114, 144, 255, 111, 147, 255, 108, 150, 255, 105, 153, 255, 102, 156, 255, 99,
            159, 255, 96, 162, 255, 93, 165, 255, 90, 168, 255, 87, 171, 255, 84, 174, 255, 81,
            177, 255, 78, 180, 255, 75, 183, 255, 72, 186, 255, 69, 189, 255, 66, 192, 255, 63,
            195, 255, 60, 198, 255, 57, 201, 255, 54, 204, 255, 51, 207, 255, 48, 210, 255, 45,
            213, 255, 42, 216, 255, 39, 219, 255, 36, 222, 255, 33, 225, 255, 30, 228, 255, 27,
            231, 255, 24, 234, 255, 21, 237, 255, 18, 240, 255, 15, 243, 255, 12, 246, 255, 9,
            249, 255, 6, 252, 255, 3, 254, 255, 0, 255, 252, 3, 255, 249, 6, 255, 246, 9,
            255, 243, 12, 255, 240, 15, 255, 237, 18, 255, 234, 21, 255, 231, 24, 255, 228, 27,
            255, 225, 30, 255, 222, 33, 255, 219, 36, 255, 216, 39, 255, 213, 42, 255, 210, 45,
            255, 207, 48, 255, 204, 51, 255, 201, 54, 255, 198, 57, 255, 195, 60, 255, 192, 63,
            255, 189, 66, 255, 186, 69, 255, 183, 72, 255, 180, 75, 255, 177, 78, 255, 174, 81,
            255, 171, 84, 255, 168, 87, 255, 165, 90, 255, 162, 93, 255, 159, 96, 255, 156, 99,
            255, 153, 102, 255, 150, 105, 255, 147, 108, 255, 144, 111, 255, 141, 114, 255, 138, 117,
            255, 135, 120, 255, 132, 123, 255, 129, 126, 255, 126, 129, 255, 123, 132, 255, 120, 135,
            255, 117, 138, 255, 114, 141, 255, 111, 144, 255, 108, 147, 255, 105, 150, 255, 102, 153,
            255, 99, 156, 255, 96, 159, 255, 93, 162, 255, 90, 165, 255, 87, 168, 255, 84, 171,
            255, 81, 173, 255, 78, 174, 255, 75, 175, 255, 72, 176, 255, 69, 177, 255, 66, 178,
            255, 63, 179, 255, 60, 180, 255, 57, 181, 255, 54, 182, 255, 51, 183, 255, 48, 184,
            255, 45, 185, 255, 42, 186, 255, 39, 187, 255, 36, 188, 255, 33, 189, 255, 30, 190,
            255, 27, 191, 255, 24, 192, 255, 21, 193, 255, 18, 194, 255, 15, 195, 255, 12, 196,
            255, 9, 197, 255, 6, 198, 255, 3, 199, 255, 0, 200
    };

    /**
     * Generate a Color Depth MIP from a single-channel 3D volume.
     * Applies contrast enhancement, intensity scaling, and color coding with
     * dominant-channel compositing.
     *
     * @param volume 3D single-channel volume
     * @return 2D RGB color depth MIP
     */
    public static ImageArray generateCDM(ImageArray volume) {
        int width = volume.getWidth();
        int height = volume.getHeight();
        int depth = volume.getDepth();

        // Make a mutable copy for intensity manipulation
        Gray16ImageArray inputVolume = new Gray16ImageArray(width, height, depth);
        for (int i = 0; i < volume.getSpatialSize(); i++) {
            inputVolume.setPackedIntValAtIndex(i, volume.getPackedIntValAtIndex(i));
        }

        // Step 1: Max intensity z-projection
        Gray16ImageArray zProjection = (Gray16ImageArray) ImageOperations.maxIntensityProjection(inputVolume, 0, depth, Gray16ImageArray::new);
        int[] minMax = computeMinMax(zProjection);
        int projMin = minMax[0];
        int projMax = minMax[1];

        LOG.debug("MIN/MAX after projection: {}/{}", projMin, projMax);

        // Step 2: Determine defaultMaxValue
        int defaultMaxValue;
        if (projMax > 255 && projMax < 4096)
            defaultMaxValue = 4095;
        else if (projMax > 4095)
            defaultMaxValue = 65535;
        else
            defaultMaxValue = 255;

        // Step 3: Stretch histogram on the z-projection (0.3% saturated)
        ImageArray zProjectionWithContrast = ImageOperations.stretchHistogram(zProjection, 0.3);
        int[] minMaxAfterStretch = computeMinMax(zProjectionWithContrast);
        int initialMax = minMaxAfterStretch[1];

        LOG.debug("MIN/MAX after histogram stretch: {}, {}, default max = {}",
                minMaxAfterStretch[0], initialMax, defaultMaxValue);

        // Step 4: Non-linear intensity adjustment
        if (defaultMaxValue == 4095) {
            if (initialMax < 200 && initialMax > 100)
                initialMax = (int) Math.round(initialMax * 1.5);
            else if (initialMax >= 200 && initialMax < 300)
                initialMax = (int) Math.round(initialMax * 1.2);
            else if (initialMax < 100)
                initialMax = Math.round(initialMax * 2);
            else if (initialMax < 2000 && initialMax > 1000)
                initialMax = (int) Math.round(initialMax * 0.9);
            else if (initialMax >= 2000)
                initialMax = (int) Math.round(initialMax * 0.8);
        } else if (defaultMaxValue == 65535) {
            if (initialMax < 3200 && initialMax > 1600)
                initialMax = (int) Math.round(initialMax * 1.5);
            else if (initialMax >= 3200 && initialMax < 4800)
                initialMax = (int) Math.round(initialMax * 1.2);
            else if (initialMax < 1600)
                initialMax = (int) Math.round(initialMax * 2);
            else if (initialMax >= 4800 && initialMax < 8000)
                initialMax = (int) Math.round(initialMax * 1.1);
        }

        // Step 5: Compute "easy adjust" value
        int applyV = computeValueAdjustment(zProjectionWithContrast, initialMax, defaultMaxValue);

        // Step 6: Scale 3D volume intensities
        if (projMin != 0 || initialMax != 65535) {
            LOG.debug("Scale intensities for INPUT: {} -> {}", applyV, defaultMaxValue);
            scaleIntensity(inputVolume, applyV, defaultMaxValue);
        }

        // Step 7: Second z-projection starting from z=15, scale to 255
        Gray16ImageArray zProjectedAdjusted = (Gray16ImageArray) ImageOperations.maxIntensityProjection(inputVolume, Math.min(15, depth - 1), depth, Gray16ImageArray::new);
        int[] adjMinMax = computeMinMax(zProjectedAdjusted);
        int maxAdjusted = adjMinMax[1];
        LOG.debug("Max adjusted of ZProjectedAdjustedInput: {}", maxAdjusted);
        scaleIntensity(inputVolume, maxAdjusted, 255);

        // Step 8: Color code
        return colorCode(inputVolume, 0, depth);
    }

    private static int[] computeMinMax(ImageArray image) {
        int min = Integer.MAX_VALUE;
        int max = 0;
        int size = image.getSpatialSize();
        for (int i = 0; i < size; i++) {
            int val = image.getPackedIntValAtIndex(i);
            if (val > max) max = val;
            if (val < min) min = val;
        }
        return new int[]{min, max};
    }

    private static void scaleIntensity(WriteableImageArray image, int sourceMax, int targetMax) {
        if (sourceMax == 0) return;
        int size = image.getSpatialSize();
        for (int i = 0; i < size; i++) {
            int value = image.getPackedIntValAtIndex(i);
            if (value > 0) {
                double scaledValue = (double) targetMax * value / sourceMax;
                if (scaledValue > targetMax) scaledValue = targetMax;
                image.setPackedIntValAtIndex(i, (int) Math.round(scaledValue));
            }
        }
    }

    private static int computeValueAdjustment(ImageArray projection, int initialMax, int defaultMaxValue) {
        long sumPxValues = 0;
        long pxCount = 0;
        int size = projection.getSpatialSize();
        for (int i = 0; i < size; i++) {
            int value = projection.getPackedIntValAtIndex(i);
            int iScaledVal;
            if (value > 0) {
                double scaledValue = (double) defaultMaxValue * value / initialMax;
                if (scaledValue > defaultMaxValue) {
                    iScaledVal = defaultMaxValue;
                } else {
                    iScaledVal = (int) Math.round(scaledValue);
                }
            } else {
                iScaledVal = 0;
            }
            if (iScaledVal > 1) {
                sumPxValues += iScaledVal;
                pxCount++;
            }
        }

        long aveval = pxCount > 0 ? Math.round((double) sumPxValues / pxCount / 16) : 0;

        LOG.debug("Easy adjust pxsum={} pxcount={} aveval={} initialMax={} defaultMaxValue={}",
                sumPxValues, pxCount, aveval, initialMax, defaultMaxValue);

        if (defaultMaxValue != 65535) {
            if (initialMax > aveval && aveval > 0) {
                return (int) aveval;
            }
        }
        return initialMax;
    }

    /**
     * Color code the 3D volume using the psychedelic rainbow LUT with
     * dominant-channel compositing (iterates z-slices, compositing each
     * slice's color with the existing MIP pixel).
     */
    private static ImageArray colorCode(ImageArray volume, int startMIP, int endMIP) {
        int[] lut = PSYCHEDELIC_RAINBOW_2;
        int width = volume.getWidth();
        int height = volume.getHeight();
        int depth = volume.getDepth();
        int sliceSize = width * height;

        if (startMIP < 0) startMIP = 0;
        if (endMIP > depth || endMIP < 0) endMIP = depth;

        int[] lutTable = new int[depth];
        for (int s = 0; s < depth; s++) {
            lutTable[s] = Math.min((int) Math.round(255.0 * s / depth), 255);
        }

        // CDM stored as packed ARGB ints
        int[] cdmPixels = new int[sliceSize];
        // initialize to black with alpha
        for (int i = 0; i < sliceSize; i++) {
            cdmPixels[i] = 0xFF000000;
        }

        for (int z = startMIP; z < endMIP; z++) {
            int zOffset = z * sliceSize;
            int lutIdx = lutTable[z];
            int lutR = lut[lutIdx * 3];
            int lutG = lut[lutIdx * 3 + 1];
            int lutB = lut[lutIdx * 3 + 2];

            for (int pi = 0; pi < sliceSize; pi++) {
                int val = volume.getPackedIntValAtIndex(zOffset + pi);
                if (val <= 0) continue;

                int red1 = (int) ((double) val / 255.0 * lutR);
                int green1 = (int) ((double) val / 255.0 * lutG);
                int blue1 = (int) ((double) val / 255.0 * lutB);

                int RB1 = 0, RG1 = 0, GB1 = 0, GR1 = 0, BR1 = 0, BG1 = 0;
                int max1 = 0;

                if (red1 > blue1 && red1 > green1) {
                    max1 = red1;
                    if (blue1 > green1) RB1 = red1 + blue1;
                    else RG1 = red1 + green1;
                } else if (green1 > blue1 && green1 > red1) {
                    max1 = green1;
                    if (blue1 > red1) GB1 = green1 + blue1;
                    else GR1 = green1 + red1;
                } else if (blue1 > red1 && blue1 > green1) {
                    max1 = blue1;
                    if (red1 > green1) BR1 = blue1 + red1;
                    else BG1 = blue1 + green1;
                }

                int existingRgb = cdmPixels[pi];
                int red2 = (existingRgb >>> 16) & 0xff;
                int green2 = (existingRgb >>> 8) & 0xff;
                int blue2 = existingRgb & 0xff;

                if (red2 > 0 || green2 > 0 || blue2 > 0) {
                    int max2 = 0;
                    MIPTWO MIPtwoST = MIPTWO.NONE;

                    if (red2 > blue2 && red2 > green2) {
                        max2 = red2;
                        if (blue2 > green2) MIPtwoST = MIPTWO.RB2;
                        else MIPtwoST = MIPTWO.RG2;
                    } else if (green2 > blue2 && green2 > red2) {
                        max2 = green2;
                        if (blue2 > red2) MIPtwoST = MIPTWO.GB2;
                        else MIPtwoST = MIPTWO.GR2;
                    } else if (blue2 > red2 && blue2 > green2) {
                        max2 = blue2;
                        if (red2 > green2) MIPtwoST = MIPTWO.BR2;
                        else MIPtwoST = MIPTWO.BG2;
                    }

                    if (max1 != 255 || max2 != 255) {
                        if (RB1 > 0) {
                            if (max1 > max2) {
                                int g = (green2 < green1) ? green1 : (green2 < blue1 ? green2 : green1);
                                cdmPixels[pi] = 0xFF000000 | (red1 << 16) | (g << 8) | blue1;
                            } else {
                                cdmPixels[pi] = cdmMax(red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                            }
                        } else if (RG1 > 0) {
                            if (max1 > max2) {
                                int b = (blue2 < blue1) ? blue1 : (blue2 < green1 ? blue2 : blue1);
                                cdmPixels[pi] = 0xFF000000 | (red1 << 16) | (green1 << 8) | b;
                            } else {
                                cdmPixels[pi] = cdmMax(red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                            }
                        } else if (GB1 > 0) {
                            if (max1 > max2) {
                                int r = (red2 < red1) ? red1 : (red2 < blue1 ? red2 : red1);
                                cdmPixels[pi] = 0xFF000000 | (r << 16) | (green1 << 8) | blue1;
                            } else {
                                cdmPixels[pi] = cdmMax(red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                            }
                        } else if (GR1 > 0) {
                            if (max1 > max2) {
                                int b = (blue2 < blue1) ? blue1 : (blue2 < red1 ? blue2 : blue1);
                                cdmPixels[pi] = 0xFF000000 | (red1 << 16) | (green1 << 8) | b;
                            } else {
                                cdmPixels[pi] = cdmMax(red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                            }
                        } else if (BR1 > 0) {
                            if (max1 > max2) {
                                int g = (green2 < green1) ? green1 : (green2 < red1 ? green2 : green1);
                                cdmPixels[pi] = 0xFF000000 | (red1 << 16) | (g << 8) | blue1;
                            } else {
                                cdmPixels[pi] = cdmMax(red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                            }
                        } else if (BG1 > 0) {
                            if (max1 > max2) {
                                int r = (red2 < red1) ? red1 : (red2 < green1 ? red2 : red1);
                                cdmPixels[pi] = 0xFF000000 | (r << 16) | (green1 << 8) | blue1;
                            } else {
                                cdmPixels[pi] = cdmMax(red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                            }
                        }
                    }
                } else {
                    // No existing color — just set the new color
                    cdmPixels[pi] = 0xFF000000 | (red1 << 16) | (green1 << 8) | blue1;
                }
            }
        }

        // Convert packed ARGB to RGBByteImageArray
        RGBByteImageArray cdm = new RGBByteImageArray(width, height, 1);
        for (int pi = 0; pi < sliceSize; pi++) {
            int rgb = cdmPixels[pi];
            cdm.setChannelIntValAtIndex(pi, 0, (rgb >>> 16) & 0xff);
            cdm.setChannelIntValAtIndex(pi, 1, (rgb >>> 8) & 0xff);
            cdm.setChannelIntValAtIndex(pi, 2, rgb & 0xff);
        }
        return cdm;
    }

    private static int cdmMax(int red1, int red2, int green1, int green2, int blue1, int blue2, MIPTWO mipTwo) {
        int rgb1 = 0;
        switch (mipTwo) {
            case RB2:
                rgb1 = red2;
                rgb1 = (rgb1 << 8) + ((green2 > green1) ? green2 : (green1 < blue2 ? green1 : green2));
                rgb1 = (rgb1 << 8) + blue2;
                break;
            case RG2:
                rgb1 = red2;
                rgb1 = (rgb1 << 8) + green2;
                rgb1 = (rgb1 << 8) + ((blue2 > blue1) ? blue2 : (blue1 < green2 ? blue1 : blue2));
                break;
            case GB2:
                rgb1 = (red2 > red1) ? red2 : (red1 < blue2 ? red1 : red2);
                rgb1 = (rgb1 << 8) + green2;
                rgb1 = (rgb1 << 8) + blue2;
                break;
            case GR2:
                rgb1 = red2;
                rgb1 = (rgb1 << 8) + green2;
                rgb1 = (rgb1 << 8) + ((blue2 > blue1) ? blue2 : (blue1 < red2 ? blue1 : blue2));
                break;
            case BR2:
                rgb1 = red2;
                rgb1 = (rgb1 << 8) + ((green2 > green1) ? green2 : (green1 < red2 ? green1 : green2));
                rgb1 = (rgb1 << 8) + blue2;
                break;
            case BG2:
                rgb1 = (red2 > red1) ? red2 : (red1 < green2 ? red1 : red2);
                rgb1 = (rgb1 << 8) + green2;
                rgb1 = (rgb1 << 8) + blue2;
                break;
            default:
                return 0xFF000000;
        }
        return 0xFF000000 | rgb1;
    }
}
