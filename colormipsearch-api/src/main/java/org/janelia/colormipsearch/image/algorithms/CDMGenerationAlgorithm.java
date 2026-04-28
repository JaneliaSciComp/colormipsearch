package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.ByteImageArray;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ShortImageArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Color Depth MIP (CDM) from a 3D volume.
 * Projects along the Z-axis using maximum intensity projection and maps Z-depth
 * to RGB color using the "psychedelic rainbow 2" look-up table.
 */
public class CDMGenerationAlgorithm {

    private static final Logger LOG = LoggerFactory.getLogger(CDMGenerationAlgorithm.class);

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
     * The input volume is expected to have intensity values (single channel).
     * The output is a 2D RGB image (3-channel ByteImageArray, depth=1) where color encodes Z-depth.
     *
     * @param volume 3D single-channel volume
     * @return 2D RGB color depth MIP (3-channel ByteImageArray)
     */
    public static ByteImageArray generateCDM(ImageArray volume) {
        int width = volume.getWidth();
        int height = volume.getHeight();
        int depth = volume.getDepth();

        // First pass: find global max intensity and do max intensity projection to get per-pixel max and z
        int globalMax = 0;
        int[] maxIntensity = new int[width * height];
        int[] maxZ = new int[width * height];

        for (int z = 0; z < depth; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int val = volume.getIntPixel(x, y, z);
                    if (val > globalMax) globalMax = val;
                    int pi = y * width + x;
                    if (val > maxIntensity[pi]) {
                        maxIntensity[pi] = val;
                        maxZ[pi] = z;
                    }
                }
            }
        }

        if (globalMax == 0) {
            return new ByteImageArray(width, height, 1, 3);
        }

        // Build Z-to-LUT-index mapping
        int[] lutTable = new int[depth];
        for (int s = 0; s < depth; s++) {
            double per = (double) s / depth;
            lutTable[s] = Math.min((int) Math.round(255.0 * per), 255);
        }

        // Generate the color-coded MIP
        ByteImageArray cdm = new ByteImageArray(width, height, 1, 3);
        for (int pi = 0; pi < width * height; pi++) {
            int val = maxIntensity[pi];
            if (val > 0) {
                int z = maxZ[pi];
                int lutIdx = lutTable[z];
                int lutR = PSYCHEDELIC_RAINBOW_2[lutIdx * 3];
                int lutG = PSYCHEDELIC_RAINBOW_2[lutIdx * 3 + 1];
                int lutB = PSYCHEDELIC_RAINBOW_2[lutIdx * 3 + 2];

                // Scale intensity
                double scale = (double) val / globalMax;
                int r = (int) (scale * lutR);
                int g = (int) (scale * lutG);
                int b = (int) (scale * lutB);

                cdm.setChannelVal(pi, 0, r);
                cdm.setChannelVal(pi, 1, g);
                cdm.setChannelVal(pi, 2, b);
            }
        }
        return cdm;
    }
}
