package org.janelia.colormipsearch;

import java.util.Random;

/**
 * This is some temporary test data that will be thrown away once we have correct published URLs.
 */
class TestData {
    static Random random = new Random();

    static String[] TEST_URLS = new String[] {
            "https://color-depth-mips.s3.amazonaws.com/JRC2018_Unisex_20x_HR/FlyLight+Split-GAL4+Drivers/LH1989-20160902_22_A3-f-20x-brain-JRC2018_Unisex_20x_HR-color_depth_1.png",
            "https://color-depth-mips.s3.amazonaws.com/JRC2018_Unisex_20x_HR/FlyLight+Split-GAL4+Drivers/LH1000-20151106_52_J3-f-20x-brain-JRC2018_Unisex_20x_HR-color_depth_1.png",
            "https://color-depth-mips.s3.amazonaws.com/JRC2018_Unisex_20x_HR/FlyLight+Split-GAL4+Drivers/LH1046-20151202_33_I1-f-20x-brain-JRC2018_Unisex_20x_HR-color_depth_1.png"
    };

    static String aRandomURL() {
        return TEST_URLS[random.nextInt(TEST_URLS.length)];
    }
}