package org.janelia.colormipsearch.cds;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class CombinedMatchScore {
    private final int pixelMatches;
    private final long gradScore;

    public CombinedMatchScore(int pixelMatches, long gradScore) {
        this.pixelMatches = pixelMatches;
        this.gradScore = gradScore;
    }

    public int getPixelMatches() {
        return pixelMatches;
    }

    public long getGradScore() {
        return gradScore;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("pixelMatches", pixelMatches)
                .append("gradScore", gradScore)
                .toString();
    }
}
