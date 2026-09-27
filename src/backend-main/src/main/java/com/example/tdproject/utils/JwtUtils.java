package com.example.tdproject.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT工具类
 * 用于生成、解析和验证JWT令牌
 * 适配 jjwt 0.12.x 版本
 */
@Component
public class JwtUtils {

    /**
     * 签名密钥。真实值见项目根 config/.env 的 JWT_SECRET（该文件不入库）。
     * 刻意不设默认值：如果没配置就启动失败，避免用公开的默认密钥签发令牌。
     */
    @Value("${jwt.secret:}")
    private String secretKey;

    // 从配置文件读取过期时间（毫秒），默认1天
    @Value("${jwt.expiration:86400000}")
    private long expiration;

    /**
     * 启动自检：密钥必须已配置且足够长。
     */
    @PostConstruct
    void validateSecret() {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "未配置 JWT 签名密钥。请在项目根 config/.env 中设置 JWT_SECRET，"
                            + "或通过同名环境变量注入（详见 config/README.md）。");
        }
        if (secretKey.getBytes().length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET 长度不足 32 字节，存在被暴力破解的风险，请更换为更长的随机串"
                            + "（例如：openssl rand -base64 48）。");
        }
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    /**
     * 生成JWT令牌
     *
     * @param userId 用户ID（作为令牌的主题）
     * @return JWT令牌字符串
     */
    public String generateToken(String userId) {
        // 当前时间
        Date now = new Date();
        // 过期时间
        Date expiryDate = new Date(now.getTime() + expiration);

        // 构建JWT令牌（jjwt 0.12.x 新API）
        return Jwts.builder()
                .subject(userId)           // 设置主题（用户ID）
                .issuedAt(now)             // 设置签发时间
                .expiration(expiryDate)    // 设置过期时间
                .signWith(getSigningKey()) // 设置签名密钥（算法自动推断）
                .compact();
    }

    /**
     * 从JWT令牌中获取用户ID
     *
     * @param token JWT令牌
     * @return 用户ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    /**
     * 验证JWT令牌是否有效
     *
     * @param token JWT令牌
     * @return true：有效，false：无效（过期、签名错误等）
     */
    public boolean validateToken(String token) {
        try {
            // 解析令牌（会自动验证签名和过期时间）
            parseClaims(token);
            return true;
        } catch (Exception e) {
            // 解析失败：令牌无效
            return false;
        }
    }

    /**
     * 解析JWT令牌中的声明（私有方法）
     *
     * @param token JWT令牌
     * @return 声明对象（包含主题、过期时间等信息）
     */
    private Claims parseClaims(String token) {
        // jjwt 0.12.x 新API
        return Jwts.parser()
                .verifyWith(getSigningKey())  // 设置验证密钥（替代setSigningKey）
                .build()
                .parseSignedClaims(token)     // 解析令牌（替代parseClaimsJws）
                .getPayload();                // 获取声明体（替代getBody）
    }

    /**
     * 获取令牌过期时间
     */
    public Date getExpirationDate(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration();
    }

    /**
     * 检查令牌是否即将过期（默认5分钟内）
     */
    public boolean isTokenExpiredSoon(String token, long minutes) {
        Date expiration = getExpirationDate(token);
        long diff = expiration.getTime() - System.currentTimeMillis();
        return diff < minutes * 60 * 1000;
    }
}
