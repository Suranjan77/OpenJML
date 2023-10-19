package org.jmlspecs.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Documented;

/** Defines the 'secret' JML annotation */

@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Secret {
    String value() default "";
}