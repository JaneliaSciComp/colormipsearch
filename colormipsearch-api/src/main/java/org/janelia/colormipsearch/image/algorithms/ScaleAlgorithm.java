package org.janelia.colormipsearch.image.algorithms;

import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageArrayFactory;
import org.janelia.colormipsearch.image.MultiChannelByteImageArray;
import org.janelia.colormipsearch.image.WriteableImageArray;

public class ScaleAlgorithm {

    /**
     * Rescale a volume to target dimensions using nearest-neighbor interpolation.
     */
    public static ImageArray scaleVolume(ImageArray input, int targetW, int targetH, int targetD, ImageArrayFactory factory) {
        int srcW = input.getWidth();
        int srcH = input.getHeight();
        int srcD = input.getDepth();
        if (srcW == targetW && srcH == targetH && srcD == targetD) {
            return input;
        }
        WriteableImageArray output = factory.create(targetW, targetH, targetD);
        for (int z = 0; z < targetD; z++) {
            int sz = (int) ((long) z * srcD / targetD);
            for (int y = 0; y < targetH; y++) {
                int sy = (int) ((long) y * srcH / targetH);
                for (int x = 0; x < targetW; x++) {
                    int sx = (int) ((long) x * srcW / targetW);
                    output.setPackedIntValAtCoords(x, y, z, input.getPackedIntValAtCoords(sx, sy, sz));
                }
            }
        }
        return output;
    }


}
