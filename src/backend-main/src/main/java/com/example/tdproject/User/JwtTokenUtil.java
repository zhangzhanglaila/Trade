package com.example.tdproject.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT Token 工具类
 */
@Component
public class JwtTokenUtil {

    /**
     * 签名密钥。来自配置 jwt.secret（真实值见项目根 config/.env 的 JWT_SECRET），
     * 不再硬编码在源码中。
     */
    @Value("${jwt.secret:}")
    private String secret;

    private static final long EXPIRATION = 86400000; // 1天

    private SecretKey getSigningKey() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "未配置 JWT 签名密钥。请在项目根 config/.env 中设置 JWT_SECRET，"
                            + "或通过同名环境变量注入（详见 config/README.md）。");
        }
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
    
    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            String userId = claims.getSubject();
            return Long.parseLong(userId);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 生成 Token
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION);
        
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
}
