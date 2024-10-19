package com.clcy.grade_feedback.annotation;

import com.clcy.grade_feedback.enumerate.LimitType;

import java.lang.annotation.*;

@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Limit {
    String name() default "";

    String prefix() default "";

    /**
     * 给定时间范围 在redis中代表过期时间(从第一次调用开始设置)
     * */
    int period();

    /**
     * 允许通过的次数
     * */
    int count();

    /**
     * 限流类型
     * */
    LimitType limitType() default LimitType.IP;
}
