package org.janelia.colormipsearch.api;

import java.util.ArrayList;
import java.util.List;

import org.janelia.colormipsearch.api.imageprocessing.ImageArray;

public class ColorMIPMaskCompare {

    private final ImageArray m_query;
    private final ImageArray m_negquery;
    private final int[] m_mask;
    private final int[] m_negmask;
    private final int[][] m_tarmasklist;
    private final int[][] m_tarmasklist_mirror;
    private final int[][] m_tarnegmasklist;
    private final int[][] m_tarnegmasklist_mirror;
    private final int m_th;
    private final double m_pixfludub;

    // Advanced Search
    public ColorMIPMaskCompare(ImageArray query, int mask_th, boolean mirror_mask,
                               ImageArray negquery, int negmask_th,
                               boolean mirror_negmask, int search_th, double toleranceZ, int xyshift) {
        m_query = query;
        m_negquery = negquery;

        m_mask = get_mskpos_array(m_query, mask_th);
        if (m_negquery != null) {
            m_negmask = get_mskpos_array(m_negquery, negmask_th);
        } else {
            m_negmask = null;
        }
        m_th = search_th;
        m_pixfludub = toleranceZ;

        // shifting
        m_tarmasklist = generate_shifted_masks(m_mask, xyshift, query.width, query.height);
        if (m_negquery != null) {
            m_tarnegmasklist = generate_shifted_masks(m_negmask, xyshift, query.width, query.height);
        } else {
            m_tarnegmasklist = null;
        }

        // mirroring
        if (mirror_mask) {
            m_tarmasklist_mirror = new int[1 + (xyshift / 2) * 8][];
            for (int i = 0; i < m_tarmasklist.length; i++)
                m_tarmasklist_mirror[i] = mirror_mask(m_tarmasklist[i], query.width);
        } else {
            m_tarmasklist_mirror = null;
        }
        if (mirror_negmask && m_negquery != null) {
            m_tarnegmasklist_mirror = new int[1 + (xyshift / 2) * 8][];
            for (int i = 0; i < m_tarnegmasklist.length; i++)
                m_tarnegmasklist_mirror[i] = mirror_mask(m_tarnegmasklist[i], query.width);
        } else {
            m_tarnegmasklist_mirror = null;
        }
    }

    public ColorMIPCompareOutput runSearch(ImageArray tarimg_in) {
        int posi = 0;
        double posipersent = 0.0;
        int masksize = m_mask.length;
        int negmasksize = m_negquery != null ? m_negmask.length : 0;

        for (int[] ints : m_tarmasklist) {
            int tmpposi = calc_score(m_query, m_mask, tarimg_in, ints, m_th, m_pixfludub);
            if (tmpposi > posi) {
                posi = tmpposi;
                posipersent = (double) posi / (double) masksize;
            }
        }
        if (m_tarnegmasklist != null) {
            int nega = 0;
            double negapersent = 0.0;
            for (int[] ints : m_tarnegmasklist) {
                int tmpnega = calc_score(m_negquery, m_negmask, tarimg_in, ints, m_th, m_pixfludub);
                if (tmpnega > nega) {
                    nega = tmpnega;
                    negapersent = (double) nega / (double) negmasksize;
                }
            }
            posipersent -= negapersent;
            posi = (int) Math.round((double) posi - (double) nega * ((double) masksize / (double) negmasksize));
        }

        if (m_tarmasklist_mirror != null) {
            int mirror_posi = 0;
            double mirror_posipersent = 0.0;
            for (int[] ints : m_tarmasklist_mirror) {
                int tmpposi = calc_score(m_query, m_mask, tarimg_in, ints, m_th, m_pixfludub);
                if (tmpposi > mirror_posi) {
                    mirror_posi = tmpposi;
                    mirror_posipersent = (double) mirror_posi / (double) masksize;
                }
            }
            if (m_tarnegmasklist_mirror != null) {
                int nega = 0;
                double negapersent = 0.0;
                for (int[] ints : m_tarnegmasklist_mirror) {
                    int tmpnega = calc_score(m_negquery, m_negmask, tarimg_in, ints, m_th, m_pixfludub);
                    if (tmpnega > nega) {
                        nega = tmpnega;
                        negapersent = (double) nega / (double) negmasksize;
                    }
                }
                mirror_posipersent -= negapersent;
                mirror_posi = (int) Math.round((double) mirror_posi - (double) nega * ((double) masksize / (double) negmasksize));
            }
            if (posipersent < mirror_posipersent) {
                posi = mirror_posi;
                posipersent = mirror_posipersent;
            }
        }

        return new ColorMIPCompareOutput(posi, posipersent);
    }

    private int[] get_mskpos_array(ImageArray msk, int thresm) {
        int sumpx = msk.getPixelCount();
        List<Integer> pos = new ArrayList<>();
        int pix, red, green, blue;
        for (int n4 = 0; n4 < sumpx; n4++) {

            pix = msk.get(n4);//Mask

            red = (pix >>> 16) & 0xff;//mask
            green = (pix >>> 8) & 0xff;//mask
            blue = pix & 0xff;//mask

            if (red > thresm || green > thresm || blue > thresm)
                pos.add(n4);
        }
        return pos.stream().mapToInt(i -> i).toArray();
    }

    private int[] shift_mskpos_array(int[] src, int xshift, int yshift, int imageWidth, int imageHeight) {
        List<Integer> pos = new ArrayList<>();
        int x, y;
        for (int i = 0; i < src.length; i++) {
            int val = src[i];
            x = (val % imageWidth) + xshift;
            y = val / imageWidth + yshift;
            if (x >= 0 && x < imageWidth && y >= 0 && y < imageHeight)
                pos.add(y * imageWidth + x);
            else
                pos.add(-1);
        }
        return pos.stream().mapToInt(i -> i).toArray();
    }

    private int[][] generate_shifted_masks(int[] in, int xyshift, int w, int h) {
        int[][] out = new int[1 + (xyshift / 2) * 8][];

        out[0] = in.clone();
        int maskid = 1;
        for (int i = 2; i <= xyshift; i += 2) {
            for (int xx = -i; xx <= i; xx += i) {
                for (int yy = -i; yy <= i; yy += i) {
                    if (xx == 0 && yy == 0) continue;
                    out[maskid] = shift_mskpos_array(in, xx, yy, w, h);
                    maskid++;
                }
            }
        }
        return out;
    }

    private int[] mirror_mask(int[] in, int ypitch) {
        int[] out = in.clone();
        int masksize = in.length;
        int x;
        for (int j = 0; j < masksize; j++) {
            int val = in[j];
            x = val % ypitch;
            out[j] = val + (ypitch - 1) - 2 * x;
        }
        return out;
    }

    private int calc_score(ImageArray src, int[] srcmaskposi, ImageArray tar, int[] tarmaskposi, int th, double pixfludub) {
        int masksize = srcmaskposi.length <= tarmaskposi.length ? srcmaskposi.length : tarmaskposi.length;
        int posi = 0;
        for (int masksig = 0; masksig < masksize; masksig++) {

            if (srcmaskposi[masksig] == -1 || tarmaskposi[masksig] == -1) continue;

            int pix1 = src.get(srcmaskposi[masksig]);
            int red1 = (pix1 >>> 16) & 0xff;
            int green1 = (pix1 >>> 8) & 0xff;
            int blue1 = pix1 & 0xff;

            int pix2 = tar.get(tarmaskposi[masksig]);
            int red2 = (pix2 >>> 16) & 0xff;
            int green2 = (pix2 >>> 8) & 0xff;
            int blue2 = pix2 & 0xff;

            if (red2 > th || green2 > th || blue2 > th) {
                double pxGap = calc_score_px(red1, green1, blue1, red2, green2, blue2);
                if (pxGap <= pixfludub) {
                    posi++;
                }
            }
        }
        return posi;
    }

    private double calc_score_px(int red1, int green1, int blue1, int red2, int green2, int blue2) {
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
                        pxGap = br2 - br1;
                        pxGap = Math.abs(pxGap);
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
                        pxGap = bg2 - bg1;
                        pxGap = Math.abs(pxGap);

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
                        pxGap = gb2 - gb1;
                        pxGap = Math.abs(pxGap);
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
                        pxGap = gr2 - gr1;
                        pxGap = Math.abs(pxGap);
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
                        pxGap = rg2 - rg1;
                        pxGap = Math.abs(pxGap);
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
                        pxGap = rb2 - rb1;
                        pxGap = Math.abs(pxGap);
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
