package org.janelia.colormipsearch.api.cdsearch;

import java.io.Serializable;

public interface ColorDepthMatchScore extends Serializable {
    /**
     * Return the score value
     * @return
     */
    long getScore();

    /**
     * @return true if the best score comes from the mirrored mask
     */
    boolean isMirrored();
}
