package cait.collector.configure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfigure {

    @Bean(name = {"byteRedisTemplate"})
    public RedisTemplate<String, byte[]> byteRedisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(RedisSerializer.byteArray());
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setDefaultSerializer(RedisSerializer.byteArray());

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

    @Bean(name = {"redis", "redisTemplate"})
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(RedisSerializer.json());
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setDefaultSerializer(RedisSerializer.json());

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

}
