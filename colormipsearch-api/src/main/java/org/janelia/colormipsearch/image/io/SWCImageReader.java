package org.janelia.colormipsearch.image.io;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.image.ImageArray;
import org.janelia.colormipsearch.image.ImageDraw;
import org.janelia.colormipsearch.image.Gray16ImageArray;

/**
 * Reads SWC skeleton files and rasterizes them into a 3D volume.
 */
public class SWCImageReader {

    static class Vec4 {
        double x, y, z, w;
        Vec4(double x, double y, double z, double w) {
            this.x = x; this.y = y; this.z = z; this.w = w;
        }
    }

    static class IVec2 {
        int x, y;
        IVec2(int x, int y) {
            this.x = x; this.y = y;
        }
    }

    public static ImageArray readSWCStream(
            InputStream is,
            int width, int height, int depth,
            double xResolution, double yResolution, double zResolution,
            int radius) {

        // single-channel short image for the rasterized skeleton
        Gray16ImageArray imageArray = new Gray16ImageArray(width, height, depth);
        Map<Integer, Integer> vertexIndexMap = new HashMap<>();
        List<Vec4> verts = new ArrayList<>();
        List<IVec2> edges = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            for (;;) {
                String line = br.readLine();
                if (line == null) break;
                line = StringUtils.trim(line);
                if (line.isEmpty() || line.charAt(0) == '#') continue;

                String[] tokens = StringUtils.split(line, ' ');
                if (tokens.length >= 7) {
                    int id = Integer.parseInt(tokens[0]);
                    double v0 = Double.parseDouble(tokens[2]);
                    double v1 = Double.parseDouble(tokens[3]);
                    double v2 = Double.parseDouble(tokens[4]);
                    double v3 = tokens[5].equals("NA") ? 0.0 : Double.parseDouble(tokens[5]);

                    vertexIndexMap.put(id, verts.size());
                    verts.add(new Vec4(v0, v1, v2, v3));

                    int parentId = Integer.parseInt(tokens[6]);
                    if (parentId != -1) {
                        edges.add(new IVec2(id, parentId));
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        double zratio = (zResolution * zResolution) / (xResolution * yResolution);

        for (IVec2 e : edges) {
            Vec4 v1 = verts.get(vertexIndexMap.get(e.x));
            Vec4 v2 = verts.get(vertexIndexMap.get(e.y));

            double x1 = v1.x / xResolution;
            double y1 = v1.y / yResolution;
            double z1 = v1.z / zResolution;

            double x2 = v2.x / xResolution;
            double y2 = v2.y / yResolution;
            double z2 = v2.z / zResolution;

            ImageDraw.draw3dLine(
                    imageArray,
                    x1, y1, z1, radius,
                    x2, y2, z2, radius,
                    zratio,
                    255);
        }
        return imageArray;
    }
}
