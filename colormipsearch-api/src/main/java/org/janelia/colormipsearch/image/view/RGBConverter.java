package org.janelia.colormipsearch.image.view;

@FunctionalInterface
public interface RGBConverter {
    int convert(int r, int g, int b);
}
