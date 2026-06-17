package org.janelia.colormipsearch.datarequests;

import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class PagedRequest {
    private long firstPageOffset;
    private long pageNumber;
    private int pageSize;
    private int batchSize;
    private List<SortCriteria> sortCriteria;

    public long getFirstPageOffset() {
        return firstPageOffset;
    }

    public PagedRequest setFirstPageOffset(long firstPageOffset) {
        this.firstPageOffset = firstPageOffset;
        return this;
    }

    public long getPageNumber() {
        return pageNumber;
    }

    public PagedRequest setPageNumber(long pageNumber) {
        this.pageNumber = pageNumber;
        return this;
    }

    public int getPageSize() {
        return pageSize;
    }

    public PagedRequest setPageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public PagedRequest setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public List<SortCriteria> getSortCriteria() {
        return sortCriteria;
    }

    public PagedRequest setSortCriteria(List<SortCriteria> sortCriteria) {
        this.sortCriteria = sortCriteria;
        return this;
    }

    public long getOffset() {
        long offset = 0L;
        if (firstPageOffset > 0) {
            offset = firstPageOffset;
        }
        if (pageNumber > 0 && pageSize > 0) {
            offset += pageNumber * pageSize;
        }
        return offset;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("firstPageOffset", firstPageOffset)
                .append("pageNumber", pageNumber)
                .append("pageSize", pageSize)
                .append("sortCriteria", sortCriteria)
                .build();
    }
}
