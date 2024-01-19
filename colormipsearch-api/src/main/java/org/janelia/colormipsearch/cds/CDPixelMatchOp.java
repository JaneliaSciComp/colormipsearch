package org.janelia.colormipsearch.cds;

import org.janelia.colormipsearch.image.type.RGBPixelType;

class CDPixelMatchOp {
     double calculateGap(RGBPixelType<?> rgbPixelType1, RGBPixelType<?> rgbPixelType2) {
         return calculatePixelGapFromRGBValues(
                 rgbPixelType1.getRed(),
                 rgbPixelType1.getGreen(),
                 rgbPixelType1.getBlue(),
                 rgbPixelType2.getRed(),
                 rgbPixelType2.getGreen(),
                 rgbPixelType2.getBlue());
    }

    /**
     * Calculate the Z-gap between two RGB pixels
     * @param red1 - red component of the first pixel
     * @param green1 - green component of the first pixel
     * @param blue1 - blue component of the first pixel
     * @param red2 - red component of the second pixel
     * @param green2 - green component of the second pixel
     * @param blue2 - blue component of the second pixel
     * @return
     */
    double calculatePixelGapFromRGBValues(int red1, int green1, int blue1, int red2, int green2, int blue2) {
        int RG1 = 0;
        int BG1 = 0;
        int GR1 = 0;
        int GB1 = 0;
        int RB1 = 0;
        int BR1 = 0;
        int RG2 = 0;
        int BG2 = 0;
        int GR2 = 0;
        int GB2 = 0;
        int RB2 = 0;
        int BR2 = 0;
        double rb1 = 0;
        double rg1 = 0;
        double gb1 = 0;
        double gr1 = 0;
        double br1 = 0;
        double bg1 = 0;
        double rb2 = 0;
        double rg2 = 0;
        double gb2 = 0;
        double gr2 = 0;
        double br2 = 0;
        double bg2 = 0;
        double pxGap = 10000;
        double BrBg = 0.354862745;
        double BgGb = 0.996078431;
        double GbGr = 0.505882353;
        double GrRg = 0.996078431;
        double RgRb = 0.505882353;
        double BrGap = 0;
        double BgGap = 0;
        double GbGap = 0;
        double GrGap = 0;
        double RgGap = 0;
        double RbGap = 0;

        if (blue1 > red1 && blue1 > green1) {//1,2
            if (red1 > green1) {
                BR1 = blue1 + red1;//1
                if (blue1 != 0 && red1 != 0)
                    br1 = (double) red1 / (double) blue1;
            } else {
                BG1 = blue1 + green1;//2
                if (blue1 != 0 && green1 != 0)
                    bg1 = (double) green1 / (double) blue1;
            }
        } else if (green1 > blue1 && green1 > red1) {//3,4
            if (blue1 > red1) {
                GB1 = green1 + blue1;//3
                if (green1 != 0 && blue1 != 0)
                    gb1 = (double) blue1 / (double) green1;
            } else {
                GR1 = green1 + red1;//4
                if (green1 != 0 && red1 != 0)
                    gr1 = (double) red1 / (double) green1;
            }
        } else if (red1 > blue1 && red1 > green1) {//5,6
            if (green1 > blue1) {
                RG1 = red1 + green1;//5
                if (red1 != 0 && green1 != 0)
                    rg1 = (double) green1 / (double) red1;
            } else {
                RB1 = red1 + blue1;//6
                if (red1 != 0 && blue1 != 0)
                    rb1 = (double) blue1 / (double) red1;
            }
        }

        if (blue2 > red2 && blue2 > green2) {
            if (red2 > green2) {//1, data
                BR2 = blue2 + red2;
                if (blue2 != 0 && red2 != 0)
                    br2 = (double) red2 / (double) blue2;
            } else {//2, data
                BG2 = blue2 + green2;
                if (blue2 != 0 && green2 != 0)
                    bg2 = (double) green2 / (double) blue2;
            }
        } else if (green2 > blue2 && green2 > red2) {
            if (blue2 > red2) {//3, data
                GB2 = green2 + blue2;
                if (green2 != 0 && blue2 != 0)
                    gb2 = (double) blue2 / (double) green2;
            } else {//4, data
                GR2 = green2 + red2;
                if (green2 != 0 && red2 != 0)
                    gr2 = (double) red2 / (double) green2;
            }
        } else if (red2 > blue2 && red2 > green2) {
            if (green2 > blue2) {//5, data
                RG2 = red2 + green2;
                if (red2 != 0 && green2 != 0)
                    rg2 = (double) green2 / (double) red2;
            } else {//6, data
                RB2 = red2 + blue2;
                if (red2 != 0 && blue2 != 0)
                    rb2 = (double) blue2 / (double) red2;
            }
        }

        ///////////////////////////////////////////////////////
        if (BR1 > 0) {//1, mask// 2 color advance core
            if (BR2 > 0) {//1, data
                if (br1 > 0 && br2 > 0) {
                    if (br1 != br2) {
                        pxGap = Math.abs(br2 - br1);
                    } else
                        pxGap = 0;

                    if (br1 == 255 & br2 == 255)
                        pxGap = 1000;
                }
            } else if (BG2 > 0) {//2, data
                if (br1 < 0.44 && bg2 < 0.54) {
                    BrGap = br1 - BrBg;//BrBg=0.354862745;
                    BgGap = bg2 - BrBg;//BrBg=0.354862745;
                    pxGap = BrGap + BgGap;
                }
            }
        } else if (BG1 > 0) {//2, mask/////////////////////////////
            if (BG2 > 0) {//2, data, 2,mask
                if (bg1 > 0 && bg2 > 0) {
                    if (bg1 != bg2) {
                        pxGap = Math.abs(bg2 - bg1);
                    } else if (bg1 == bg2)
                        pxGap = 0;
                    if (bg1 == 255 & bg2 == 255)
                        pxGap = 1000;
                }
            } else if (GB2 > 0) {//3, data, 2,mask
                if (bg1 > 0.8 && gb2 > 0.8) {
                    BgGap = BgGb - bg1;//BgGb=0.996078431;
                    GbGap = BgGb - gb2;//BgGb=0.996078431;
                    pxGap = BgGap + GbGap;
                }
            } else if (BR2 > 0) {//1, data, 2,mask
                if (bg1 < 0.54 && br2 < 0.44) {
                    BgGap = bg1 - BrBg;//BrBg=0.354862745;
                    BrGap = br2 - BrBg;//BrBg=0.354862745;
                    pxGap = BrGap + BgGap;
                }
            }
        } else if (GB1 > 0) {//3, mask/////////////////////////////
            if (GB2 > 0) {//3, data, 3mask
                if (gb1 > 0 && gb2 > 0) {
                    if (gb1 != gb2) {
                        pxGap = Math.abs(gb2 - gb1);
                    } else
                        pxGap = 0;
                    if (gb1 == 255 & gb2 == 255)
                        pxGap = 1000;
                }
            } else if (BG2 > 0) {//2, data, 3mask
                if (gb1 > 0.8 && bg2 > 0.8) {
                    BgGap = BgGb - gb1;//BgGb=0.996078431;
                    GbGap = BgGb - bg2;//BgGb=0.996078431;
                    pxGap = BgGap + GbGap;
                }
            } else if (GR2 > 0) {//4, data, 3mask
                if (gb1 < 0.7 && gr2 < 0.7) {
                    GbGap = gb1 - GbGr;//GbGr=0.505882353;
                    GrGap = gr2 - GbGr;//GbGr=0.505882353;
                    pxGap = GbGap + GrGap;
                }
            }//2,3,4 data, 3mask
        } else if (GR1 > 0) {//4mask/////////////////////////////
            if (GR2 > 0) {//4, data, 4mask
                if (gr1 > 0 && gr2 > 0) {
                    if (gr1 != gr2) {
                        pxGap = Math.abs(gr2 - gr1);
                    } else
                        pxGap = 0;
                    if (gr1 == 255 & gr2 == 255)
                        pxGap = 1000;
                }
            } else if (GB2 > 0) {//3, data, 4mask
                if (gr1 < 0.7 && gb2 < 0.7) {
                    GrGap = gr1 - GbGr;//GbGr=0.505882353;
                    GbGap = gb2 - GbGr;//GbGr=0.505882353;
                    pxGap = GrGap + GbGap;
                }
            } else if (RG2 > 0) {//5, data, 4mask
                if (gr1 > 0.8 && rg2 > 0.8) {
                    GrGap = GrRg - gr1;//GrRg=0.996078431;
                    RgGap = GrRg - rg2;
                    pxGap = GrGap + RgGap;
                }
            }//3,4,5 data
        } else if (RG1 > 0) {//5, mask/////////////////////////////
            if (RG2 > 0) {//5, data, 5mask
                if (rg1 > 0 && rg2 > 0) {
                    if (rg1 != rg2) {
                        pxGap = Math.abs(rg2 - rg1);
                    } else
                        pxGap = 0;
                    if (rg1 == 255 & rg2 == 255)
                        pxGap = 1000;
                }

            } else if (GR2 > 0) {//4 data, 5mask
                if (rg1 > 0.8 && gr2 > 0.8) {
                    GrGap = GrRg - gr2;//GrRg=0.996078431;
                    RgGap = GrRg - rg1;//GrRg=0.996078431;
                    pxGap = GrGap + RgGap;
                }
            } else if (RB2 > 0) {//6 data, 5mask
                if (rg1 < 0.7 && rb2 < 0.7) {
                    RgGap = rg1 - RgRb;//RgRb=0.505882353;
                    RbGap = rb2 - RgRb;//RgRb=0.505882353;
                    pxGap = RbGap + RgGap;
                }
            }//4,5,6 data
        } else if (RB1 > 0) {//6, mask/////////////////////////////
            if (RB2 > 0) {//6, data, 6mask
                if (rb1 > 0 && rb2 > 0) {
                    if (rb1 != rb2) {
                        pxGap = Math.abs(rb2 - rb1);
                    } else if (rb1 == rb2)
                        pxGap = 0;
                    if (rb1 == 255 & rb2 == 255)
                        pxGap = 1000;
                }
            } else if (RG2 > 0) {//5, data, 6mask
                if (rg2 < 0.7 && rb1 < 0.7) {
                    RgGap = rg2 - RgRb;//RgRb=0.505882353;
                    RbGap = rb1 - RgRb;//RgRb=0.505882353;
                    pxGap = RgGap + RbGap;
                }
            }
        }//2 color advance core
        return pxGap;
    }

}
