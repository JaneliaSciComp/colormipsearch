package org.janelia.colormipsearch.model;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMatch<M extends AbstractNeuronMetadata, T extends AbstractNeuronMetadata> {

    private M maskImage;
    private T matchedImage;
    private boolean mirrored; // if true the matchedImage was mirrored
    private Map<FileType, FileData> matchFiles = new HashMap<>(); // match specific files

    public M getMaskImage() {
        return maskImage;
    }

    public void setMaskImage(M maskImage) {
        this.maskImage = maskImage;
    }

    public void resetMaskImage() {
        this.maskImage = null;
    }

    public T getMatchedImage() {
        return matchedImage;
    }

    public void setMatchedImage(T matchedImage) {
        this.matchedImage = matchedImage;
    }

    public void resetMatchedImage() {
        this.matchedImage = null;
    }

    public boolean isMirrored() {
        return mirrored;
    }

    public void setMirrored(boolean mirrored) {
        this.mirrored = mirrored;
    }

    public Map<FileType, FileData> getMatchFiles() {
        return matchFiles;
    }

    public void setMatchFiles(Map<FileType, FileData> matchFiles) {
        this.matchFiles = matchFiles;
    }

    public void setMatchFileData(FileType t, FileData fd) {
        if (fd != null) {
            matchFiles.put(t, fd);
        } else {
            matchFiles.remove(t);
        }
    }

    /**
     * This method only copies data that can be safely assigned to the destination fields;
     * that is why it doess not copy the mask and the target images since the type for
     * those may not coincide with the ones from the source
     *
     * @param that
     * @param <M1> destination mask type
     * @param <T1> destination target type
     * @param <R1> destination result type
     */
    protected <M1 extends AbstractNeuronMetadata,
               T1 extends AbstractNeuronMetadata,
               R1 extends AbstractMatch<M1, T1>> void copyFrom(R1 that) {
        this.mirrored = that.isMirrored();
        this.matchFiles.clear();
        this.matchFiles.putAll(that.getMatchFiles());
    }

    public abstract <M2 extends AbstractNeuronMetadata,
                     T2 extends AbstractNeuronMetadata> AbstractMatch<M2, T2> duplicate(MatchCopier<M, T, AbstractMatch<M, T>, M2, T2, AbstractMatch<M2, T2>> copier);
}
