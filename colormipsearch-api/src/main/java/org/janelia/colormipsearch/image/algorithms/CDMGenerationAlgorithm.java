package org.janelia.colormipsearch.image.algorithms;

import java.util.Comparator;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.janelia.colormipsearch.image.ImageAccessUtils;
import org.janelia.colormipsearch.image.ImageTransforms;
import org.janelia.colormipsearch.image.type.IntRGBPixelType;
import org.janelia.colormipsearch.image.type.RGBPixelType;

public class CDMGenerationAlgorithm {

    enum MIPTWO {
        NONE,
        RB2,
        RG2,
        GB2,
        GR2,
        BR2,
        BG2
    }

    private static final int[] PSYCHEDELIC_RAINBOW_2 = {
            127, 0, 255,
            125, 3, 255,
            124, 6, 255,
            122, 9, 255,
            121, 12, 255,
            120, 15, 255,
            119, 18, 255,
            118, 21, 255,
            116, 24, 255,
            115, 27, 255,
            114, 30, 255,
            113, 33, 255,
            112, 36, 255,
            110, 39, 255,
            109, 42, 255,
            108, 45, 255,
            106, 48, 255,
            105, 51, 255,
            104, 54, 255,
            103, 57, 255,
            101, 60, 255,
            100, 63, 255,
            99, 66, 255,
            98, 69, 255,
            96, 72, 255,
            95, 75, 255,
            94, 78, 255,
            93, 81, 255,
            92, 84, 255,
            90, 87, 255,
            89, 90, 255,
            87, 93, 255,
            86, 96, 255,
            84, 99, 255,
            83, 102, 255,
            81, 105, 255,
            80, 108, 255,
            78, 111, 255,
            77, 114, 255,
            75, 117, 255,
            74, 120, 255,
            72, 123, 255,
            71, 126, 255,
            69, 129, 255,
            68, 132, 255,
            66, 135, 255,
            65, 138, 255,
            63, 141, 255,
            62, 144, 255,
            60, 147, 255,
            59, 150, 255,
            57, 153, 255,
            56, 156, 255,
            54, 159, 255,
            53, 162, 255,
            51, 165, 255,
            50, 168, 255,
            48, 171, 255,
            47, 174, 255,
            45, 177, 255,
            44, 180, 255,
            42, 183, 255,
            41, 186, 255,
            39, 189, 255,
            38, 192, 255,
            36, 195, 255,
            35, 198, 255,
            33, 201, 255,
            32, 204, 255,
            30, 207, 255,
            29, 210, 255,
            27, 213, 255,
            26, 216, 255,
            24, 219, 255,
            23, 222, 255,
            21, 225, 255,
            20, 228, 255,
            18, 231, 255,
            16, 234, 255,
            14, 237, 255,
            12, 240, 255,
            9, 243, 255,
            6, 246, 255,
            3, 249, 255,
            1, 252, 255,
            0, 254, 255,
            3, 255, 252,
            6, 255, 249,
            9, 255, 246,
            12, 255, 243,
            15, 255, 240,
            18, 255, 237,
            21, 255, 234,
            24, 255, 231,
            27, 255, 228,
            30, 255, 225,
            33, 255, 222,
            36, 255, 219,
            39, 255, 216,
            42, 255, 213,
            45, 255, 210,
            48, 255, 207,
            51, 255, 204,
            54, 255, 201,
            57, 255, 198,
            60, 255, 195,
            63, 255, 192,
            66, 255, 189,
            69, 255, 186,
            72, 255, 183,
            75, 255, 180,
            78, 255, 177,
            81, 255, 174,
            84, 255, 171,
            87, 255, 168,
            90, 255, 165,
            93, 255, 162,
            96, 255, 159,
            99, 255, 156,
            102, 255, 153,
            105, 255, 150,
            108, 255, 147,
            111, 255, 144,
            114, 255, 141,
            117, 255, 138,
            120, 255, 135,
            123, 255, 132,
            126, 255, 129,
            129, 255, 126,
            132, 255, 123,
            135, 255, 120,
            138, 255, 117,
            141, 255, 114,
            144, 255, 111,
            147, 255, 108,
            150, 255, 105,
            153, 255, 102,
            156, 255, 99,
            159, 255, 96,
            162, 255, 93,
            165, 255, 90,
            168, 255, 87,
            171, 255, 84,
            174, 255, 81,
            177, 255, 78,
            180, 255, 75,
            183, 255, 72,
            186, 255, 69,
            189, 255, 66,
            192, 255, 63,
            195, 255, 60,
            198, 255, 57,
            201, 255, 54,
            204, 255, 51,
            207, 255, 48,
            210, 255, 45,
            213, 255, 42,
            216, 255, 39,
            219, 255, 36,
            222, 255, 33,
            225, 255, 30,
            228, 255, 27,
            231, 255, 24,
            234, 255, 21,
            237, 255, 18,
            240, 255, 15,
            243, 255, 12,
            246, 255, 9,
            249, 255, 6,
            252, 255, 3,
            254, 255, 0,
            255, 252, 3,
            255, 249, 6,
            255, 246, 9,
            255, 243, 12,
            255, 240, 15,
            255, 237, 18,
            255, 234, 21,
            255, 231, 24,
            255, 228, 27,
            255, 225, 30,
            255, 222, 33,
            255, 219, 36,
            255, 216, 39,
            255, 213, 42,
            255, 210, 45,
            255, 207, 48,
            255, 204, 51,
            255, 201, 54,
            255, 198, 57,
            255, 195, 60,
            255, 192, 63,
            255, 189, 66,
            255, 186, 69,
            255, 183, 72,
            255, 180, 75,
            255, 177, 78,
            255, 174, 81,
            255, 171, 84,
            255, 168, 87,
            255, 165, 90,
            255, 162, 93,
            255, 159, 96,
            255, 156, 99,
            255, 153, 102,
            255, 150, 105,
            255, 147, 108,
            255, 144, 111,
            255, 141, 114,
            255, 138, 117,
            255, 135, 120,
            255, 132, 123,
            255, 129, 126,
            255, 126, 129,
            255, 123, 132,
            255, 120, 135,
            255, 117, 138,
            255, 114, 141,
            255, 111, 144,
            255, 108, 147,
            255, 105, 150,
            255, 102, 153,
            255, 99, 156,
            255, 96, 159,
            255, 93, 162,
            255, 90, 165,
            255, 87, 168,
            255, 84, 171,
            255, 81, 173,
            255, 78, 174,
            255, 75, 175,
            255, 72, 176,
            255, 69, 177,
            255, 66, 178,
            255, 63, 179,
            255, 60, 180,
            255, 57, 181,
            255, 54, 182,
            255, 51, 183,
            255, 48, 184,
            255, 45, 185,
            255, 42, 186,
            255, 39, 187,
            255, 36, 188,
            255, 33, 189,
            255, 30, 190,
            255, 27, 191,
            255, 24, 192,
            255, 21, 193,
            255, 18, 194,
            255, 15, 195,
            255, 12, 196,
            255, 9, 197,
            255, 6, 198,
            255, 3, 199,
            255, 0, 200
    };

    public static <S extends IntegerType<S> & NativeType<S>, T extends RGBPixelType<T>> Img<T> generateCDM(RandomAccessibleInterval<S> input,
                                                                                                           S inputPxType,
                                                                                                           T cdmPxType) {
        int startMIP = 0;
        int endMIP = 1000;

        Comparator<S> pxComparator = Comparator.comparingInt(IntegerType::getInteger);
        RandomAccessibleInterval<S> zProjection = ImageAccessUtils.materializeAsNativeImg(
                ImageTransforms.maxIntensityProjection(
                        input,
                        pxComparator,
                        2,
                        input.min(2),
                        input.max(2)
                ),
                null,
                inputPxType
        );
        S minS = inputPxType.createVariable();
        S maxS = inputPxType.createVariable();
        ComputeMinMax.computeMinMax(zProjection, minS, maxS);

        System.out.printf("MIN max after projection %d, %d\n", minS.getInteger(), maxS.getInteger());
        int Inimin = minS.getInteger();
        int max = maxS.getInteger();

        int defaultMaxValue;
        if (max > 255 && max < 4096)
            defaultMaxValue = 4095;
        else if (max > 4095)
            defaultMaxValue = 65535;
        else // if (max < 256)
            defaultMaxValue = 255;

        RandomAccessibleInterval<S> contrastEnhancedZProjection = ImageTransforms.enhanceContrast(
                zProjection,
                inputPxType::createVariable,
                0.3, -1, -1, 65536
        );
        S minSAfterContrastEnhancement = inputPxType.createVariable();
        S maxSAfterContrastEnhancement = inputPxType.createVariable();
        ComputeMinMax.computeMinMax(contrastEnhancedZProjection, minSAfterContrastEnhancement, maxSAfterContrastEnhancement);
        System.out.printf("MIN max after histogram stretch %d, %d, default max = %d\n",
                minSAfterContrastEnhancement.getInteger(), maxSAfterContrastEnhancement.getInteger(), defaultMaxValue);

        int initialMax = maxSAfterContrastEnhancement.getInteger();
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

        System.out.printf("Values to scale intensity: %d -> %d\n", initialMax, defaultMaxValue);
        int applyV = initialMax;
        RandomAccessibleInterval<S> intensityAdjustedZProjection = ImageTransforms.scaleIntensity(contrastEnhancedZProjection, initialMax, defaultMaxValue, inputPxType);
        System.out.printf("Limits for intensity adjustment: %d, %d\n", initialMax, defaultMaxValue);
        long sumPxValues = 0;
        long pxCount = 0;
        Cursor<S> cursor = Views.flatIterable(intensityAdjustedZProjection).cursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            int val = cursor.get().getInteger();
            if (val > 1) {
                sumPxValues = sumPxValues + val;
                pxCount++;
            }
        }

        long aveval = Math.round((double) sumPxValues / pxCount / 16);

        if (defaultMaxValue != 65535) {
            if (initialMax > aveval && aveval > 0)
                applyV = (int) aveval;
        }
        System.out.printf("Easy adjust pxsum=%d pxcount=%d %d %d \n", sumPxValues, pxCount, aveval, applyV);

        RandomAccessibleInterval<S> intensityAdjustedInput;
        if (Inimin != 0 || initialMax != 65535) {
            System.out.printf("Scale intensities for INPUT: %d -> %d\n", applyV, defaultMaxValue);
            intensityAdjustedInput = ImageAccessUtils.materializeAsNativeImg(
                    ImageTransforms.scaleIntensity(input, applyV, defaultMaxValue, inputPxType),
                    null,
                    inputPxType
            );
        } else {
            intensityAdjustedInput = input;
        }

        RandomAccessibleInterval<S> colorCoderInput;
        if (inputPxType instanceof UnsignedShortType) {
            RandomAccessibleInterval<S> zProjectedAdjustedInput = ImageTransforms.maxIntensityProjection(
                    intensityAdjustedInput,
                    pxComparator,
                    2,
                    15,
                    intensityAdjustedInput.max(2)
            );
            S minAdjustedT = inputPxType.createVariable();
            S maxAdjustedT = inputPxType.createVariable();
            ComputeMinMax.computeMinMax(zProjectedAdjustedInput, minAdjustedT, maxAdjustedT);
            int maxAdjusted = maxAdjustedT.getInteger();
            System.out.printf("Max adjusted of ZProjectedAdjustedInput: %d\n", maxAdjusted);
            colorCoderInput = ImageAccessUtils.materializeAsNativeImg(
                    ImageTransforms.scaleIntensity(intensityAdjustedInput, maxAdjusted, 255, inputPxType),
                    null,
                    inputPxType
            );
        } else {
            colorCoderInput = ImageAccessUtils.materializeAsNativeImg(
                    intensityAdjustedInput,
                    null,
                    inputPxType
            );
        }
        return colorCode(colorCoderInput, startMIP, endMIP, cdmPxType);
    }

    // "Time-Lapse Color Coder"
    private static <S extends IntegerType<S>, T extends IntegerType<T> & NativeType<T>> Img<T> colorCode(RandomAccessibleInterval<S> stack,
                                                                                                         int startMIP,
                                                                                                         int endMIP,
                                                                                                         T cdmPxType) {
        int[] lut = PSYCHEDELIC_RAINBOW_2;    // default LUT

        int width = (int) stack.dimension(0);
        int height = (int) stack.dimension(1);
        int slices = (int) stack.dimension(2);

        if (startMIP < 0) startMIP = 0;
        if (endMIP > slices || endMIP < 0) endMIP = slices;

        int[] lut_table = new int[slices];

        for (int s = 0; s < slices; s++) {
            double per = (double) s / slices;
            double colv = 255.0 * per;
            int val = (int) Math.round(colv);
            lut_table[s] = val;
        }

        ArrayImgFactory<T> factory = new ArrayImgFactory<>(cdmPxType);
        Img<T> cdm = factory.create(width, height);

        // Iterate over the 2D projection image
        Cursor<T> cdmCursor = cdm.cursor();
        RandomAccess<S> randomAccess = stack.randomAccess();

        while (cdmCursor.hasNext()) {
            cdmCursor.fwd();
            long x = cdmCursor.getIntPosition(0);
            long y = cdmCursor.getIntPosition(1);

            cdmCursor.get().setInteger(0xFF000000);

            randomAccess.setPosition(x, 0);
            randomAccess.setPosition(y, 1);

            // Find the maximum intensity along the Z-axis for this x,y position
            for (int z = startMIP; z < endMIP; z++) {
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
                int max1 = 0;
                int max2 = 0;
                int MIPtwo = 0;
                MIPTWO MIPtwoST = MIPTWO.NONE;

                randomAccess.setPosition(z, 2);
                int val = randomAccess.get().getInteger();
                if (val > 0) {
                    int lut_r = lut[lut_table[z] * 3];
                    int lut_g = lut[lut_table[z] * 3 + 1];
                    int lut_b = lut[lut_table[z] * 3 + 2];

                    int red1 = (int) ((double) val / 255.0 * (double) lut_r);
                    int green1 = (int) ((double) val / 255.0 * (double) lut_g);
                    int blue1 = (int) ((double) val / 255.0 * (double) lut_b);

                    if (red1 > blue1 && red1 > green1) {//RB1 & RG1
                        max1 = red1;
                        if (blue1 > green1) {
                            RB1 = red1 + blue1;//1
                        } else {
                            RG1 = red1 + green1;//2
                        }
                    } else if (green1 > blue1 && green1 > red1) {
                        max1 = green1;
                        if (blue1 > red1)
                            GB1 = green1 + blue1;//3
                        else
                            GR1 = green1 + red1;//4
                    } else if (blue1 > red1 && blue1 > green1) {
                        max1 = blue1;
                        if (red1 > green1)
                            BR1 = blue1 + red1;//5
                        else
                            BG1 = blue1 + green1;//6
                    }

                    int rgb2 = cdmCursor.get().getInteger();
                    int red2 = (rgb2 >>> 16) & 0xff;//MIP
                    int green2 = (rgb2 >>> 8) & 0xff;//MIP
                    int blue2 = rgb2 & 0xff;//MIP

                    if (red2 > 0 || green2 > 0 || blue2 > 0) {
                        if (red2 > blue2 && red2 > green2) {
                            max2 = red2;
                            if (blue2 > green2) {//1
                                RB2 = red2 + blue2;
                                MIPtwo = RB2;
                                MIPtwoST = MIPTWO.RB2;
                            } else {//2
                                RG2 = red2 + green2;
                                MIPtwo = RG2;
                                MIPtwoST = MIPTWO.RG2;
                            }
                        } else if (green2 > blue2 && green2 > red2) {
                            max2 = green2;
                            if (blue2 > red2) {//3
                                GB2 = green2 + blue2;
                                MIPtwo = GB2;
                                MIPtwoST = MIPTWO.GB2;
                            } else {//4
                                GR2 = green2 + red2;
                                MIPtwo = GR2;
                                MIPtwoST = MIPTWO.GR2;
                            }
                        } else if (blue2 > red2 && blue2 > green2) {
                            max2 = blue2;
                            if (red2 > green2) {//5
                                BR2 = blue2 + red2;
                                MIPtwo = BR2;
                                MIPtwoST = MIPTWO.BR2;
                            } else {//6
                                BG2 = blue2 + green2;
                                MIPtwo = BG2;
                                MIPtwoST = MIPTWO.BG2;
                            }
                        }//if(red2>=blue2 && red2>=green2){

                        int rgb1 = 0;
                        if (max1 != 255 || max2 != 255) {
                            if (RB1 > 0) {//data1 > 0
                                if (max1 > max2) {//1
                                    rgb1 = red1;

                                    if (green2 < green1)
                                        rgb1 = (rgb1 << 8) + green1;
                                    else {//green2>green1
                                        if (green2 < blue1)
                                            rgb1 = (rgb1 << 8) + green2;
                                        else//if(green2>=blue1)
                                            rgb1 = (rgb1 << 8) + green1;
                                    }

                                    rgb1 = (rgb1 << 8) + blue1;
                                    cdmCursor.get().setInteger(0xFF000000 | rgb1);
                                } else {
                                    cdmMax(cdmCursor.get(), red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                                }//if(RB1<=MIPtwo){

                            } else if (RG1 > 0) {//2

                                if (max1 > max2) {
                                    rgb1 = red1;
                                    rgb1 = (rgb1 << 8) + green1;

                                    if (blue2 < blue1)
                                        rgb1 = (rgb1 << 8) + blue1;
                                    else {//blue2>blue1
                                        if (blue2 < green1)
                                            rgb1 = (rgb1 << 8) + blue2;
                                        else//(blue2>=green1)
                                            rgb1 = (rgb1 << 8) + blue1;
                                    }
                                    cdmCursor.get().setInteger(0xFF000000 | rgb1);
                                } else {
                                    cdmMax(cdmCursor.get(), red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                                }//if(RG1>MIPtwo){

                            } else if (GB1 > 0) {//3

                                if (max1 > max2) {

                                    if (red2 < red1)
                                        rgb1 = red1;
                                    else {//red2>red1
                                        if (red2 < blue1)
                                            rgb1 = red2;
                                        else//(red2>=blue1)
                                            rgb1 = red1;
                                    }

                                    rgb1 = (rgb1 << 8) + green1;
                                    rgb1 = (rgb1 << 8) + blue1;
                                    cdmCursor.get().setInteger(0xFF000000 | rgb1);
                                } else {
                                    cdmMax(cdmCursor.get(), red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                                }//if(RG1>MIPtwo){

                            } else if (GR1 > 0) {//4

                                if (max1 > max2) {

                                    rgb1 = red1;
                                    rgb1 = (rgb1 << 8) + green1;

                                    if (blue2 < blue1)
                                        rgb1 = (rgb1 << 8) + blue1;
                                    else {//blue2>blue1
                                        if (blue2 < red1)
                                            rgb1 = (rgb1 << 8) + blue2;
                                        else//(blue2>=red1)
                                            rgb1 = (rgb1 << 8) + blue1;
                                    }
                                    cdmCursor.get().setInteger(0xFF000000 | rgb1);
                                } else {
                                    cdmMax(cdmCursor.get(), red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                                }//if(RG1>MIPtwo){

                            } else if (BR1 > 0) {//5

                                if (max1 > max2) {

                                    rgb1 = red1;

                                    if (green2 < green1)
                                        rgb1 = (rgb1 << 8) + green1;
                                    else {//green2>green1
                                        if (green2 < red1)
                                            rgb1 = (rgb1 << 8) + green2;
                                        else//(green2>=red1)
                                            rgb1 = (rgb1 << 8) + green1;
                                    }

                                    rgb1 = (rgb1 << 8) + blue1;
                                    cdmCursor.get().setInteger(0xFF000000 | rgb1);
                                } else {
                                    cdmMax(cdmCursor.get(), red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                                }//if(RG1>MIPtwo){

                            } else if (BG1 > 0) {//6

                                if (max1 > max2) {

                                    if (red2 < red1)
                                        rgb1 = red1;
                                    else {//red2>red1
                                        if (red2 < green1)
                                            rgb1 = red2;
                                        else//(red2>=green1)
                                            rgb1 = red1;
                                    }

                                    rgb1 = (rgb1 << 8) + green1;
                                    rgb1 = (rgb1 << 8) + blue1;
                                    cdmCursor.get().setInteger(0xFF000000 | rgb1);
                                } else {
                                    cdmMax(cdmCursor.get(), red1, red2, green1, green2, blue1, blue2, MIPtwoST);
                                }//if(RG1>MIPtwo){
                            }//if data1 > 0
                        }
                    } else {
                        cdmCursor.get().setInteger(0xFF000000 | (red1 << 16) | (green1 << 8) | blue1);
                    }
                }
            }
        }
        return cdm;
    }

    private static <T extends IntegerType<T>> void cdmMax(T pix,
                                                          int red1,
                                                          int red2,
                                                          int green1,
                                                          int green2,
                                                          int blue1,
                                                          int blue2,
                                                          MIPTWO MIPtwoST2) {

        int rgb1 = 0;

        switch (MIPtwoST2) {
            case RB2:
                rgb1 = red2;
                if (green2 > green1)
                    rgb1 = (rgb1 << 8) + green2;
                else {//green2<green1
                    if (green1 < blue2)
                        rgb1 = (rgb1 << 8) + green1;
                    else//(green1>=blue2)
                        rgb1 = (rgb1 << 8) + green2;
                }

                rgb1 = (rgb1 << 8) + blue2;
                pix.setInteger(0xFF000000 | rgb1);
                break;
            case RG2:
                rgb1 = red2;
                rgb1 = (rgb1 << 8) + green2;

                if (blue2 > blue1)
                    rgb1 = (rgb1 << 8) + blue2;
                else {//blue2<blue1
                    if (blue1 < green2)
                        rgb1 = (rgb1 << 8) + blue1;
                    else//(blue1>=green2)
                        rgb1 = (rgb1 << 8) + blue2;
                }
                pix.setInteger(0xFF000000 | rgb1);
                break;
            case GB2:
                if (red2 > red1)
                    rgb1 = red2;
                else {//red2<red1
                    if (red1 < blue2)
                        rgb1 = red1;
                    else//(red1>=blue2){
                        rgb1 = red2;
                }

                rgb1 = (rgb1 << 8) + green2;
                rgb1 = (rgb1 << 8) + blue2;

                pix.setInteger(0xFF000000 | rgb1);
                break;
            case GR2:
                rgb1 = red2;
                rgb1 = (rgb1 << 8) + green2;

                if (blue2 > blue1)
                    rgb1 = (rgb1 << 8) + blue2;
                else {//blue2<blue1
                    if (blue1 < red2)
                        rgb1 = (rgb1 << 8) + blue1;
                    else//(blue1>=red2)
                        rgb1 = (rgb1 << 8) + blue2;
                }

                pix.setInteger(0xFF000000 | rgb1);
                break;
            case BR2:
                rgb1 = red2;

                if (green2 > green1)
                    rgb1 = (rgb1 << 8) + green2;
                else {//green2<green1
                    if (green1 < red2)
                        rgb1 = (rgb1 << 8) + green1;
                    else//(green1>=red2)
                        rgb1 = (rgb1 << 8) + green2;
                }

                rgb1 = (rgb1 << 8) + blue2;

                pix.setInteger(0xFF000000 | rgb1);
                break;
            case BG2:
                if (red2 > red1)
                    rgb1 = red2;
                else {//red2<red1
                    if (red1 < green2)
                        rgb1 = red1;
                    else//(red1>=green2)
                        rgb1 = red2;
                }

                rgb1 = (rgb1 << 8) + green2;
                rgb1 = (rgb1 << 8) + blue2;

                pix.setInteger(0xFF000000 | rgb1);
                break;
        }
    }

}
