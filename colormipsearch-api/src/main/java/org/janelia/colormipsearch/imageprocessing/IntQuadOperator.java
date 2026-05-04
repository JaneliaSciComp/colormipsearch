package org.janelia.colormipsearch.imageprocessing;

import java.io.Serializable;

/**
 * This is a 4 parameter int function.
 */
@FunctionalInterface
public interface IntQuadOperator extends Serializable {
    int applyAsInt(int r, int s, int t, int u);
}
