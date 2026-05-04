package org.janelia.colormipsearch.image;

public interface ImageArrayVisitor {
    void init();
    void visitPos(ImageArray imageArray, int x, int y, int z);
    void visitPosCh(ImageArray imageArray, int x, int y, int z, int ch);
    int getVal();
    int getChVal(int ch);
}
