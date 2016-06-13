package com.davfx.ninio.http.v3.service.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.davfx.ninio.http.v3.service.HttpController;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Intercept {
	Class<? extends HttpController> value();
}
