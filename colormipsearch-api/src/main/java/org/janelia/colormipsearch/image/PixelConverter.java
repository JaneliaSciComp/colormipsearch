package org.janelia.colormipsearch.image;

public interface PixelConverter<S, T> {

    T convertTo(S source);

    default <R> PixelConverter<R, T> compose(PixelConverter<R, S> before) {
        return (R r) -> convertTo(before.convertTo(r));
    }

    default <U> PixelConverter<S, U> andThen(PixelConverter<T, U> after) {
        return (S s) -> after.convertTo(convertTo(s));
    }
}
