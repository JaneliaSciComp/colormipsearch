package org.janelia.colormipsearch.cds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageMaskPredicate;
import org.janelia.colormipsearch.image.RGBByteImageArray;
import org.janelia.colormipsearch.imageprocessing.ImageOperations;
import org.janelia.colormipsearch.mips.ComputeVariantImageSupplier;
import org.janelia.colormipsearch.model.ComputeFileType;

/**
 * PixelMatchColorDepthQuerySearchAlgorithm - implements the color depth mip comparison
 * using internal arrays containg the positions from the mask that are above the mask threshold
 * and the positions after applying the specified x-y shift and mirroring transformations.
 * The mask pixels are compared against the target pixels tht
 */
public class PixelMatchColorDepthSearchAlgorithm implements ColorDepthSearchAlgorithm<PixelMatchScore> {

    private final ImageArray queryImage;
    private final int[] queryPositions;
    private final int targetThreshold;
    private final double zTolerance;
    private final int[][] shiftedQueryPositionsList;
    private final int[][] mirroredShiftedQueryPositionsList;

    public PixelMatchColorDepthSearchAlgorithm(ImageArray queryImage, int queryThreshold,
                                               boolean mirrorQuery,
                                               int targetThreshold,
                                               double zTolerance, int xyshift,
                                               ImageMaskPredicate excludedRegionsPredicate) {
        this.queryImage = ImageOperations.duplicateImage(
                ImageOperations.maskRGB(
                        ImageOperations.maskRegion(queryImage, excludedRegionsPredicate),
                        queryThreshold
                ),
                RGBByteImageArray::new
        );
        this.queryPositions = getMaskPosArray(this.queryImage);
        this.targetThreshold = targetThreshold;
        this.zTolerance = zTolerance;
        // shifting
        shiftedQueryPositionsList = generateAllShiftedQueryPositions(
                queryPositions,
                xyshift,
                this.queryImage.getWidth(), this.queryImage.getHeight()
        );
        // mirroring
        if (mirrorQuery) {
            int[] mirroredPositions = mirrorPositions(queryPositions, this.queryImage.getWidth());
            mirroredShiftedQueryPositionsList = generateAllShiftedQueryPositions(
                    mirroredPositions,
                    xyshift,
                    this.queryImage.getWidth(),
                    this.queryImage.getHeight()
            );
        } else {
            mirroredShiftedQueryPositionsList = null;
        }
    }

    @Override
    public ImageArray getQueryImage() {
        return queryImage;
    }

    @Override
    public int getQuerySize() {
        return queryPositions.length;
    }

    @Override
    public Set<ComputeFileType> getRequiredTargetVariantTypes() {
        return Collections.emptySet();
    }

    @Override
    public PixelMatchScore calculateMatchingScore(@Nonnull ImageArray targetImageArray,
                                                  Map<ComputeFileType, ComputeVariantImageSupplier> variantImageSuppliers) {
        int querySize = getQuerySize();
        if (querySize == 0) {
            return new PixelMatchScore(0, 0, false);
        }
        if (hasDifferentShape(targetImageArray)) {
            throw new IllegalArgumentException(String.format("Invalid image size - target's image size (%d, %d) must match query's image size: (%d, %d)",
                    getQueryImage().getWidth(), getQueryImage().getHeight(), targetImageArray.getWidth(), targetImageArray.getHeight()
            ));
        }
        int maxMatchingPixels = calculateMaxScoreForAllTargetTransformations(
                queryImage,
                queryPositions,
                targetImageArray,
                shiftedQueryPositionsList);
        boolean bestScoreMirrored = false;
        if (mirroredShiftedQueryPositionsList != null) {
            int mirroredXYShiftsMaxScore = calculateMaxScoreForAllTargetTransformations(
                    queryImage,
                    queryPositions,
                    targetImageArray,
                    mirroredShiftedQueryPositionsList
            );
            if (mirroredXYShiftsMaxScore > maxMatchingPixels) {
                maxMatchingPixels = mirroredXYShiftsMaxScore;
                bestScoreMirrored = true;
            }
        }
        double maxMatchingPixelsRatio = (double)maxMatchingPixels / (double)querySize;
        return new PixelMatchScore(maxMatchingPixels, maxMatchingPixelsRatio, bestScoreMirrored);
    }

    private boolean hasDifferentShape(ImageArray target) {
        return queryImage.getWidth() != target.getWidth()
                || queryImage.getHeight() != target.getHeight()
                || queryImage.getDepth() != target.getDepth();
    }

    private int[] getMaskPosArray(ImageArray msk) {
        List<Integer> pos = new ArrayList<>();
        for (int pi = 0; pi < msk.getSpatialSize(); pi++) {
            int pix = msk.getPackedIntValAtIndex(pi);
            if ((pix & 0xFFFFFF) != 0) {
                pos.add(pi);
            }
        }
        return pos.stream().mapToInt(i -> i).toArray();
    }

    private int[][] generateAllShiftedQueryPositions(int[] pixelCoords, int xyshift, int imageWidth, int imageHeight) {
        int nshifts = 1 + (xyshift / 2) * 8;
        int[][] out = new int[nshifts][];
        if (nshifts > 1) {
            int maskid = 0;
            for (int i = 2; i <= xyshift; i += 2) {
                for (int xx = -i; xx <= i; xx += i) {
                    for (int yy = -i; yy <= i; yy += i) {
                        out[maskid] = shiftMaskPosArray(pixelCoords, xx, yy, imageWidth, imageHeight);
                        maskid++;
                    }
                }
            }
        } else {
            out[0] = pixelCoords;
        }
        return out;
    }

    private int[] shiftMaskPosArray(int[] pixelCoords, int xshift, int yshift, int imageWidth, int imageHeight) {
        return Arrays.stream(pixelCoords)
                .map(pi -> {
                    int x = (pi % imageWidth) + xshift;
                    int y = pi / imageWidth + yshift;
                    if (x >= 0 && x < imageWidth && y >= 0 && y < imageHeight)
                        return y * imageWidth + x;
                    else
                        return -1;

                })
                .filter(pi -> pi != -1)
                .toArray();
    }

    private int[] mirrorPositions(int[] positions, int imageWidth) {
        int[] mirrored = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            int pos = positions[i];
            int x = pos % imageWidth;
            mirrored[i] = pos + (imageWidth - 1) - 2 * x;
        }
        return mirrored;
    }

    private int calculateMaxScoreForAllTargetTransformations(ImageArray srcImageArray,
                                                             int[] srcPixelCoord,
                                                             ImageArray targetImageArray,
                                                             int[][] targetPixelCoordSupplier) {
        int maxScore = 0;
        for (int[] targetPixelCoord : targetPixelCoordSupplier) {
            int score = calculateScore(srcImageArray, srcPixelCoord, targetImageArray, targetPixelCoord);
            if (score > maxScore) {
                maxScore = score;
            }
        }
        return maxScore;
    }

    private int calculateScore(ImageArray srcImage,
                               int[] srcPositions,
                               ImageArray targetImage,
                               int[] targetPositions) {
        int size = Math.min(srcPositions.length, targetPositions.length);
        int score = 0;
        for (int i = 0; i < size; i++) {
            int srcPos = srcPositions[i];
            int targetPos = targetPositions[i];
            int red2 = targetImage.getChannelIntValAtIndex(targetPos, 0);
            int green2 = targetImage.getChannelIntValAtIndex(targetPos, 1);
            int blue2 = targetImage.getChannelIntValAtIndex(targetPos, 2);
            if (red2 > targetThreshold || green2 > targetThreshold || blue2 > targetThreshold) {
                int red1 = srcImage.getChannelIntValAtIndex(srcPos, 0);
                int green1 = srcImage.getChannelIntValAtIndex(srcPos, 1);
                int blue1 = srcImage.getChannelIntValAtIndex(srcPos, 2);
                double pxGap = calculatePixelGap(red1, green1, blue1, red2, green2, blue2);
                if (pxGap <= zTolerance) {
                    score++;
                }
            }
        }
        return score;
    }

    /**
     * Calculate the Z-gap between two RGB pixels
     * @param red1 - red component of the first pixel
     * @param green1 - green component of the first pixel
     * @param blue1 - blue component of the first pixel
     * @param red2 - red component of the second pixel
     * @param green2 - green component of the second pixel
     * @param blue2 - blue component of the second pixel
     * @return pixel gap value
     */
    private double calculatePixelGap(int red1, int green1, int blue1, int red2, int green2, int blue2) {
        int RG1 = 0;
        int BG1 = 0;
        int GR1 = 0;
        int GB1 = 0;
        int RB1 = 0;
        int BR1 = 0;
        int RG2 = 0;
        int BG2 = 0;
        int GR2 = 0;
        int GB2 = 0;
        int RB2 = 0;
        int BR2 = 0;
        double rb1 = 0;
        double rg1 = 0;
        double gb1 = 0;
        double gr1 = 0;
        double br1 = 0;
        double bg1 = 0;
        double rb2 = 0;
        double rg2 = 0;
        double gb2 = 0;
        double gr2 = 0;
        double br2 = 0;
        double bg2 = 0;
        double pxGap = 10000;
        double BrBg = 0.354862745;
        double BgGb = 0.996078431;
        double GbGr = 0.505882353;
        double GrRg = 0.996078431;
        double RgRb = 0.505882353;
        double BrGap;
        double BgGap;
        double GbGap;
        double GrGap;
        double RgGap;
        double RbGap;

        if (blue1 > red1 && blue1 > green1) { // 1,2
            if (red1 > green1) {
                BR1 = blue1 + red1;//1
                if (blue1 != 0 && red1 != 0)
                    br1 = (double) red1 / (double) blue1;
            } else {
                BG1 = blue1 + green1; // 2
                if (blue1 != 0 && green1 != 0)
                    bg1 = (double) green1 / (double) blue1;
            }
        } else if (green1 > blue1 && green1 > red1) {//3,4
            if (blue1 > red1) {
                GB1 = green1 + blue1;//3
                if (green1 != 0 && blue1 != 0)
                    gb1 = (double) blue1 / (double) green1;
            } else {
                GR1 = green1 + red1;//4
                if (green1 != 0 && red1 != 0)
                    gr1 = (double) red1 / (double) green1;
            }
        } else if (red1 > blue1 && red1 > green1) {//5,6
            if (green1 > blue1) {
                RG1 = red1 + green1;//5
                if (red1 != 0 && green1 != 0)
                    rg1 = (double) green1 / (double) red1;
            } else {
                RB1 = red1 + blue1;//6
                if (red1 != 0 && blue1 != 0)
                    rb1 = (double) blue1 / (double) red1;
            }
        }

        if (blue2 > red2 && blue2 > green2) {
            if (red2 > green2) {//1, data
                BR2 = blue2 + red2;
                if (blue2 != 0 && red2 != 0)
                    br2 = (double) red2 / (double) blue2;
            } else {//2, data
                BG2 = blue2 + green2;
                if (blue2 != 0 && green2 != 0)
                    bg2 = (double) green2 / (double) blue2;
            }
        } else if (green2 > blue2 && green2 > red2) {
            if (blue2 > red2) {//3, data
                GB2 = green2 + blue2;
                if (green2 != 0 && blue2 != 0)
                    gb2 = (double) blue2 / (double) green2;
            } else {//4, data
                GR2 = green2 + red2;
                if (green2 != 0 && red2 != 0)
                    gr2 = (double) red2 / (double) green2;
            }
        } else if (red2 > blue2 && red2 > green2) {
            if (green2 > blue2) {//5, data
                RG2 = red2 + green2;
                if (red2 != 0 && green2 != 0)
                    rg2 = (double) green2 / (double) red2;
            } else {//6, data
                RB2 = red2 + blue2;
                if (red2 != 0 && blue2 != 0)
                    rb2 = (double) blue2 / (double) red2;
            }
        }

        ///////////////////////////////////////////////////////
        if (BR1 > 0) {//1, mask// 2 color advance core
            if (BR2 > 0) {//1, data
                if (br1 > 0 && br2 > 0) {
                    if (br1 != br2) {
                        pxGap = Math.abs(br2 - br1);
                    } else
                        pxGap = 0;

                    if (br1 == 255 & br2 == 255)
                        pxGap = 1000;
                }
            } else if (BG2 > 0) {//2, data
                if (br1 < 0.44 && bg2 < 0.54) {
                    BrGap = br1 - BrBg;//BrBg=0.354862745;
                    BgGap = bg2 - BrBg;//BrBg=0.354862745;
                    pxGap = BrGap + BgGap;
                }
            }
        } else if (BG1 > 0) {//2, mask/////////////////////////////
            if (BG2 > 0) {//2, data, 2,mask
                if (bg1 > 0 && bg2 > 0) {
                    if (bg1 != bg2) {
                        pxGap = Math.abs(bg2 - bg1);
                    } else // (bg1 == bg2)
                        pxGap = 0;
                    if (bg1 == 255 & bg2 == 255)
                        pxGap = 1000;
                }
            } else if (GB2 > 0) {//3, data, 2,mask
                if (bg1 > 0.8 && gb2 > 0.8) {
                    BgGap = BgGb - bg1; // BgGb=0.996078431;
                    GbGap = BgGb - gb2; // BgGb=0.996078431;
                    pxGap = BgGap + GbGap;
                }
            } else if (BR2 > 0) {//1, data, 2,mask
                if (bg1 < 0.54 && br2 < 0.44) {
                    BgGap = bg1 - BrBg; // BrBg=0.354862745;
                    BrGap = br2 - BrBg; // BrBg=0.354862745;
                    pxGap = BrGap + BgGap;
                }
            }
        } else if (GB1 > 0) {//3, mask/////////////////////////////
            if (GB2 > 0) {//3, data, 3mask
                if (gb1 > 0 && gb2 > 0) {
                    if (gb1 != gb2) {
                        pxGap = Math.abs(gb2 - gb1);
                    } else
                        pxGap = 0;
                    if (gb1 == 255 & gb2 == 255)
                        pxGap = 1000;
                }
            } else if (BG2 > 0) { // 2, data, 3mask
                if (gb1 > 0.8 && bg2 > 0.8) {
                    BgGap = BgGb - gb1; // BgGb=0.996078431;
                    GbGap = BgGb - bg2; // BgGb=0.996078431;
                    pxGap = BgGap + GbGap;
                }
            } else if (GR2 > 0) { // 4, data, 3mask
                if (gb1 < 0.7 && gr2 < 0.7) {
                    GbGap = gb1 - GbGr; // GbGr=0.505882353;
                    GrGap = gr2 - GbGr; // GbGr=0.505882353;
                    pxGap = GbGap + GrGap;
                }
            }//2,3,4 data, 3mask
        } else if (GR1 > 0) { // 4mask/////////////////////////////
            if (GR2 > 0) { // 4, data, 4mask
                if (gr1 > 0 && gr2 > 0) {
                    if (gr1 != gr2) {
                        pxGap = Math.abs(gr2 - gr1);
                    } else
                        pxGap = 0;
                    if (gr1 == 255 & gr2 == 255)
                        pxGap = 1000;
                }
            } else if (GB2 > 0) {//3, data, 4mask
                if (gr1 < 0.7 && gb2 < 0.7) {
                    GrGap = gr1 - GbGr;//GbGr=0.505882353;
                    GbGap = gb2 - GbGr;//GbGr=0.505882353;
                    pxGap = GrGap + GbGap;
                }
            } else if (RG2 > 0) {//5, data, 4mask
                if (gr1 > 0.8 && rg2 > 0.8) {
                    GrGap = GrRg - gr1;//GrRg=0.996078431;
                    RgGap = GrRg - rg2;
                    pxGap = GrGap + RgGap;
                }
            }//3,4,5 data
        } else if (RG1 > 0) { // 5, mask/////////////////////////////
            if (RG2 > 0) { // 5, data, 5mask
                if (rg1 > 0 && rg2 > 0) {
                    if (rg1 != rg2) {
                        pxGap = Math.abs(rg2 - rg1);
                    } else
                        pxGap = 0;
                    if (rg1 == 255 & rg2 == 255)
                        pxGap = 1000;
                }

            } else if (GR2 > 0) {//4 data, 5mask
                if (rg1 > 0.8 && gr2 > 0.8) {
                    GrGap = GrRg - gr2;//GrRg=0.996078431;
                    RgGap = GrRg - rg1;//GrRg=0.996078431;
                    pxGap = GrGap + RgGap;
                }
            } else if (RB2 > 0) {//6 data, 5mask
                if (rg1 < 0.7 && rb2 < 0.7) {
                    RgGap = rg1 - RgRb;//RgRb=0.505882353;
                    RbGap = rb2 - RgRb;//RgRb=0.505882353;
                    pxGap = RbGap + RgGap;
                }
            }//4,5,6 data
        } else if (RB1 > 0) {//6, mask/////////////////////////////
            if (RB2 > 0) {//6, data, 6mask
                if (rb1 > 0 && rb2 > 0) {
                    if (rb1 != rb2) {
                        pxGap = Math.abs(rb2 - rb1);
                    } else if (rb1 == rb2)
                        pxGap = 0;
                    if (rb1 == 255 & rb2 == 255)
                        pxGap = 1000;
                }
            } else if (RG2 > 0) {//5, data, 6mask
                if (rg2 < 0.7 && rb1 < 0.7) {
                    RgGap = rg2 - RgRb;//RgRb=0.505882353;
                    RbGap = rb1 - RgRb;//RgRb=0.505882353;
                    pxGap = RgGap + RbGap;
                }
            }
        }//2 color advance core
        return pxGap;
    }

}
