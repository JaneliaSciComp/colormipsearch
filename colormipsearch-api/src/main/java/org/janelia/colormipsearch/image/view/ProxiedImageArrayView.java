package org.janelia.colormipsearch.image.view;

import org.janelia.colormipsearch.image.ImageArray;

public class ProxiedImageArrayView implements ImageArrayView {

    private final ImageArray sourceImage;
    private final ImageViewAdapter imageViewAdapter;

    public ProxiedImageArrayView(ImageArray sourceImage, ImageViewAdapter imageViewAdapter) {
        this.sourceImage = sourceImage;
        this.imageViewAdapter = imageViewAdapter;
    }

    @Override
    public ImageArray getSourceImage() {
        return sourceImage;
    }

    ImageViewAdapter getImageViewAdapter() {
        return getImageViewAdapter();
    }

    @Override
    public int getWidth() {
        return imageViewAdapter.getWidth(getSourceImage());
    }

    @Override
    public int getHeight() {
        return imageViewAdapter.getHeight(getSourceImage());
    }

    @Override
    public int getDepth() {
        return imageViewAdapter.getDepth(getSourceImage());
    }

    @Override
    public int getChannels() {
        return imageViewAdapter.getChannels(getSourceImage());
    }

    @Override
    public int getPackedIntValAtIndex(int pi) {
        return imageViewAdapter.getPackedIntValAtIndex(getSourceImage(), pi);
    }

    @Override
    public int getChannelIntValAtIndex(int pi, int ch) {
        return imageViewAdapter.getChannelIntValAtIndex(getSourceImage(), pi, ch);
    }

}
