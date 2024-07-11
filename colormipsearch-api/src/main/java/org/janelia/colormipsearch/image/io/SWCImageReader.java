package org.janelia.colormipsearch.image.io;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.NumericType;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.image.ImageDraw;

public class SWCImageReader {

    static class Vec4 {
        double x;
        double y;
        double z;
        double w;
        Vec4(double x_, double y_, double z_, double w_) {
            x = x_; y = y_; z = z_; w = w_;
        }
    }

    static class IVec2 {
        int x;
        int y;
        IVec2(int x_, int y_) {
            x = x_; y = y_;
        }
    }

    public static <T extends NumericType<T>> void readSWCSkeleton(String swcSource, Img<T> img,
                                                                  double xySpacing, double zSpacing, double r,
                                                                  T foregroundValue) {
        Map<Integer, Integer> vertexIndexMap = new HashMap<Integer, Integer>();
        List<Vec4> verts = new ArrayList<>();
        List<IVec2> edges = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(swcSource))) {
            for (;;) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                line = StringUtils.trim(line);
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] tokens = StringUtils.split(line, ' ');
                if (tokens.length >= 7)
                {
                    double v0, v1, v2, v3;
                    int ival;
                    int id = Integer.parseInt(tokens[0]);
                    v0 = Double.parseDouble(tokens[2]);
                    v1 = Double.parseDouble(tokens[3]);
                    v2 = Double.parseDouble(tokens[4]);
                    if (tokens[5].equals("NA"))
                        v3 = 0.0;
                    else
                        v3 = Double.parseDouble(tokens[5]);

                    vertexIndexMap.put(id, verts.size());
                    verts.add( new Vec4(v0, v1, v2, v3) );

                    ival = Integer.parseInt(tokens[6]);
                    if (ival != -1)
                        edges.add(new IVec2(id, ival));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        double zratio = zSpacing / xySpacing;

        int w = (int) img.dimension(0);
        int h = (int) img.dimension(1);
        int d = (int) img.dimension(2);

        RandomAccess<T> imgAccess = img.randomAccess();
        for (IVec2 e : edges) {
            Vec4 v1 = verts.get(vertexIndexMap.get(e.x));
            Vec4 v2 = verts.get(vertexIndexMap.get(e.y));

            double x1 = v1.x / xySpacing;
            double y1 = v1.y / xySpacing;
            double z1 = v1.z / zSpacing;

            double x2 = v2.x / xySpacing;
            double y2 = v2.y / xySpacing;
            double z2 = v2.z / zSpacing;

            ImageDraw.draw3dLine(
                    imgAccess,
                    w, h, d,
                    x1, y1, z1, r,
                    x2, y2, z2, r,
                    zratio,
                    foregroundValue);
        }
    }
}
