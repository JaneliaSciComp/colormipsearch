package org.janelia.colormipsearch.image;

public class Dimensions {

    public static int X_AXIS = 0x1;
    public static int Y_AXIS = 0x2;
    public static int Z_AXIS = 0x4;

    public static boolean isX(int axes) {
        return (axes & X_AXIS) != 0;
    }

    public static boolean isY(int axes) {
        return (axes & Y_AXIS) != 0;
    }

    public static boolean isZ(int axes) {
        return (axes & Z_AXIS) != 0;
    }

    public static int selectDim(int dimX, int dimY, int dimZ, int dim) {
        switch (dim) {
            case 0:
                return dimX;
            case 1:
                return dimY;
            case 2:
                return dimZ;
            default:
                throw new IllegalArgumentException("Currently it supports up to 3D");
        }
    }

    public static long selectDim(long dimX, long dimY, long dimZ, int dim) {
        switch (dim) {
            case 0:
                return dimX;
            case 1:
                return dimY;
            case 2:
                return dimZ;
            default:
                throw new IllegalArgumentException("Currently it supports up to 3D");
        }
    }

    public static int selectDim(int[] dims, int dim) {
        return dims[dim];
    }
}