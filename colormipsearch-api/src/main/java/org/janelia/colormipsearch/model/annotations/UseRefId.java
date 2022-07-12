package org.janelia.colormipsearch.model.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({
        ElementType.FIELD,
        ElementType.METHOD
})
public @interface UseRefId {
}
