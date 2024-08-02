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
            final double expected;

            TestData(int pix, long gap, long highExpr, int maxPix, long maxNeg, double expected) {
                this.pix = pix;
                this.gap = gap;
                this.highExpr = highExpr;
                this.maxPix = maxPix;
                this.maxNeg = maxNeg;
                this.expected = expected;
            }
        }
        TestData[] testData = new TestData[] {
                new TestData(636, 0, 1897, 679, 1114361L, 46833.58),
                new TestData(636, 0, 1644, 679, 1107088, 46833.58), // interesting - lower absolute scores higher ranking
                new TestData(636, 0, 1644, 679, 1114361L, 46833.58), // interesting - lower absolute scores higher ranking
                new TestData(795, 0, 93, 875, 1606182L, 45428.57)
        };
        for (TestData td : testData) {
            double s = GradientAreaGapUtils.calculateNormalizedScore(
                    td.pix,
                    GradientAreaGapUtils.calculate2DShapeScore(td.gap, td.highExpr),
                    td.maxPix, td.maxNeg);
            assertEquals(td.expected, s, 0.1);
        }
    }

}
