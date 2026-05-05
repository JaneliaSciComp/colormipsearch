package org.janelia.colormipsearch.image;

public class ColorOperations {

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    public static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    public static int blue(int rgb) {
        return rgb & 0xFF;
    }

    public static int rgb2Gray8(int rgb) {
        return rgb2Gray8(red(rgb), green(rgb), blue(rgb));
    }

    public static int rgb2Gray8(int r, int g, int b) {
        return rgb2Gray(r, g, b, 255);
    }

    public static int rgb2Gray16(int rgb) {
        return rgb2Gray16(red(rgb), green(rgb), blue(rgb));
    }

    public static int rgb2Gray16(int r, int g, int b) {
        return rgb2Gray(r, g, b, 65535);
    }

    public static int rgbMax(int r, int g, int b) {
        return Math.max(r, Math.max(g, b));
    }


    public static int rgb2Gray(int r, int g, int b, int maxVal) {
        double rw = 1 / 3.;
        double gw = 1 / 3.;
        double bw = 1 / 3.;

        return (int) ((maxVal / (float) maxVal) * (r * rw + g * gw + b * bw + 0.5));
    }

}
