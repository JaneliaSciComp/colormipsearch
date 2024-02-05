package org.janelia.colormipsearch.image;

public interface PixelConverter<S, T> {

    T convert(S source);

    default <R> PixelConverter<R, T> compose(PixelConverter<R, S> before) {
        return (R r) -> convert(before.convert(r));
    }

    default <U> PixelConverter<S, U> andThen(PixelConverter<T, U> after) {
        return (S s) -> after.convert(convert(s));
    }

    static <T> PixelConverter<T, T> identity() { return t -> t; }
}
