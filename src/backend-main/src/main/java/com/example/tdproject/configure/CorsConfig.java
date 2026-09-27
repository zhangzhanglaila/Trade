package com.example.tdproject.configure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration // 标记为配置类，Spring 启动时会自动加载
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许前端的域名（开发环境允许 localhost 任意端口）
        // 注意：当 allowCredentials=true 时，不能使用 addAllowedOrigin("*")，需要用 allowedOriginPatterns
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");

        // 允许携带 Cookie（前后端需要同时开启，前端请求时也要设置 withCredentials: true）
        config.setAllowCredentials(true);

        // 允许的请求方法（GET、POST、PUT、DELETE、OPTIONS 等，* 表示所有）
        config.addAllowedMethod("*");

        // 允许的请求头（如 Content-Type、Authorization 等，* 表示所有）
        config.addAllowedHeader("*");

        // 预检请求的有效期（单位：秒，1小时内不再重复发送预检请求）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

