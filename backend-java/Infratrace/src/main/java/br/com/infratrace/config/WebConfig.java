package br.com.infratrace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra o interceptor que identifica o usuário responsável por cada alteração. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UsuarioResponsavelInterceptor usuarioResponsavel;

    public WebConfig(UsuarioResponsavelInterceptor usuarioResponsavel) {
        this.usuarioResponsavel = usuarioResponsavel;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(usuarioResponsavel).addPathPatterns("/**");
    }
}
