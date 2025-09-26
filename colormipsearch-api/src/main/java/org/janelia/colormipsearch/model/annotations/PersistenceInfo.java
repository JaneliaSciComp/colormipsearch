package org.janelia.colormipsearch.model.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Persistence annotation for an entity. It defines where the entity is persisted and it is somehow related to the JPA Entity annotation.
 * Only the top-level class should be mapped using this annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface PersistenceInfo {
    String storeName();
    String archiveName() default "";
}
