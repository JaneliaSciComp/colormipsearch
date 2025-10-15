package org.janelia.colormipsearch.cds;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GradientAreaGapUtilsTest {

    @Test
    public void scoreCalculator() {
        class TestData {
            final int pix;
            final long gap;
            final long highExpr;
            final int maxPix;
            final long maxNeg;
            final long expectedShapeScore;
            final double expectedNormalized;

            TestData(int pix, long gap, long highExpr, int maxPix, long maxNeg, long expectedShapeScore, double expectedNormalized) {
                this.pix = pix;
                this.gap = gap;
                this.highExpr = highExpr;
                this.maxPix = maxPix;
                this.maxNeg = maxNeg;
                this.expectedShapeScore = expectedShapeScore;
                this.expectedNormalized = expectedNormalized;
            }
        }
        TestData[] testData = new TestData[] {
                new TestData(636, 156, 1897, 679, 1114361L,
                        788, 46833.58),
                new TestData(636, 233, 1644, 679, 1107088,
                        781, 46833.58), // interesting - lower absolute scores higher ranking
                new TestData(636, 0, 1644, 679, 1114361L,
                        548, 46833.58), // interesting - lower absolute scores higher ranking
                new TestData(795, 123, 93, 875, 1606182L,
                        154, 45428.57)
        };
        for (TestData td : testData) {
            long shapeScore = GradientAreaGapUtils.calculate2DShapeScore(td.gap, td.highExpr);
            assertEquals(td.expectedShapeScore, shapeScore);
            double s = GradientAreaGapUtils.calculateNormalizedScore(
                    td.pix,
                    shapeScore,
                    td.maxPix,
                    td.maxNeg);
            assertEquals(td.expectedNormalized, s, 0.1);
        }
    }

}
