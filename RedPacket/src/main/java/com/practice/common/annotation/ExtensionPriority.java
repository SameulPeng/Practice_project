package com.practice.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扩展类优先级注解<br/>
 * value属性值越大，优先级越高，执行时机越早
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionPriority {
    /**
     * 优先级，值越大，类中的扩展方法执行时机越早
     */
    int value() default 0;
}
