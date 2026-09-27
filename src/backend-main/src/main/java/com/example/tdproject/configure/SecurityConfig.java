package com.example.tdproject.configure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置类
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 密码编码器
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Security 过滤器链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（前后端分离项目不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 启用 CORS（使用 CorsConfig 中的 CorsConfigurationSource 配置）
            .cors(Customizer.withDefaults())
            // 配置无状态会话（JWT 不需要 session）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // 配置请求授权
            .authorizeHttpRequests(auth -> auth
                // 预检请求放行（否则浏览器会在 /auth/login 的 OPTIONS 预检阶段直接失败）
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 公开接口：登录、注册、用户相关
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/user/**").permitAll()
                // 图谱分析相关接口公开
                .requestMatchers("/ontology/**").permitAll()
                // 物流、新闻等其他接口也公开
                .requestMatchers("/logistics/**").permitAll()
                .requestMatchers("/news/**").permitAll()
                // 其他接口允许访问
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
