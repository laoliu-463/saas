package com.colonel.saas.domain.user.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks Mapper methods whose query wrapper should consume user-domain data scope.
 *
 * @see com.colonel.saas.domain.user.infrastructure.aspect.DataScopeAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * Column used when appending visible user ids to the query wrapper.
     */
    String userField() default "user_id";
}
