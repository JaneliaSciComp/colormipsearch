package org.janelia.colormipsearch.cds;

import java.io.Serializable;

public interface ColorDepthMatchScore extends Serializable {
    /**
     * Return the score value
     * @return
     */
    int getScore();

    /**
     * Return the normalized score value
     * @return
     */
    float getNormalizedScore();

    /**
     * @return true if the best score comes from the mirrored mask
     */
    boolean isMirrored();
}
