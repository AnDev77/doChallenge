package com.fitmeet.auth.presentation;

import com.fitmeet.auth.domain.AuthAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthRateLimitConfig implements WebMvcConfigurer, HandlerInterceptor {
    @Value("${app.auth.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;
    private final AuthAttemptLimiter limiter;
    public AuthRateLimitConfig(AuthAttemptLimiter limiter) { this.limiter = limiter; }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/auth/**").allowedOrigins(allowedOrigins)
                .allowedMethods("POST", "OPTIONS").allowedHeaders("Content-Type", "Authorization")
                .exposedHeaders("Retry-After").allowCredentials(false);
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/auth/**");
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) return true;
        String path = request.getRequestURI();
        int limit = switch (path) {
            case "/api/v1/auth/email-verifications/send", "/api/v1/auth/signup" -> 10;
            case "/api/v1/auth/email-verifications/confirm" -> 60;
            case "/api/v1/auth/login" -> 30;
            default -> 0;
        };
        // Do not accept arbitrary X-Forwarded-For supplied by the caller.
        if (limit > 0) limiter.check("ip-" + path, request.getRemoteAddr(), limit, 600);
        return true;
    }
}
