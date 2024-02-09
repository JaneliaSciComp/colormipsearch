package org.janelia.colormipsearch.image;

import java.io.Serializable;

/**
 * This is a 4 parameter function.
 *
 * @param <P>
 * @param <Q>
 * @param <R>
 * @param <S>
 * @param <T>
 */
@FunctionalInterface
public interface QuadConverter<P, Q, R, S, T> extends Serializable {
    void convert(P p, Q q, R r, S s, T t);
}
