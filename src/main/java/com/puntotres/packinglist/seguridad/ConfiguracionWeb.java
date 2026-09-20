package com.puntotres.packinglist.seguridad;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final InterceptorPermisos interceptorPermisos;

    public ConfiguracionWeb(InterceptorPermisos interceptorPermisos) {
        this.interceptorPermisos = interceptorPermisos;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(interceptorPermisos)
                .addPathPatterns("/**")
                .excludePathPatterns("/estilo*.css", "/icono-web-*.png", "/favicon.ico", "/error");
    }
}
