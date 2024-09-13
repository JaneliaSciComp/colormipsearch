package org.janelia.colormipsearch.cds;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class ShapeMatchScore implements ColorDepthMatchScore {
    private final long bidirectionalAreaGap;
    private final long gradientAreaGap;
    private final long highExpressionArea;
    private final long maxGradientAreaGap;
    private final boolean mirrored;

    ShapeMatchScore(long gradientAreaGap, long highExpressionArea, long maxGradientAreaGap, boolean mirrored) {
        this.gradientAreaGap = gradientAreaGap;
        this.highExpressionArea = highExpressionArea;
        this.maxGradientAreaGap = maxGradientAreaGap;
        this.mirrored = mirrored;
        this.bidirectionalAreaGap = -1;
    }

    ShapeMatchScore(long bidirectionalAreaGap) {
        this.bidirectionalAreaGap = bidirectionalAreaGap;
        this.gradientAreaGap = -1;
        this.highExpressionArea = -1;
        this.maxGradientAreaGap = -1;
        this.mirrored = false;
    }

    @Override
    public int getScore() {
        return bidirectionalAreaGap != -1
                ? (int) bidirectionalAreaGap
                : (int) GradientAreaGapUtils.calculate2DShapeScore(gradientAreaGap, highExpressionArea);
    }

    @Override
    public float getNormalizedScore() {
        long currentScore = getScore();
        return maxGradientAreaGap > 0 ? currentScore / (float) maxGradientAreaGap : currentScore;
    }

    @Override
    public boolean isMirrored() {
        return mirrored;
    }

    public long getBidirectionalAreaGap() {
        return bidirectionalAreaGap;
    }

    public long getGradientAreaGap() {
        return gradientAreaGap;
    }

    public long getHighExpressionArea() {
        return highExpressionArea;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("gradientAreaGap", gradientAreaGap)
                .append("highExpressionArea", highExpressionArea)
                .toString();
    }
}
