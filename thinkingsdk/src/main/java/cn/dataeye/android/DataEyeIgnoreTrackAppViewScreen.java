package cn.dataeye.android;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为 Activity 或者 Fragment 添加此注解，将不再采集该页面的 ta_app_view 事件.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataEyeIgnoreTrackAppViewScreen {
    /**
     * 指定生效的 APP ID, 默认对所有 APP ID 生效
     * @return 项目 APP ID
     */
    String appId() default "";
}
